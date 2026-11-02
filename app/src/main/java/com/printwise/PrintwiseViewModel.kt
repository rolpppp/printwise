package com.printwise

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.printwise.export.DocxExporter
import com.printwise.export.PdfExporter
import com.printwise.export.ShareExporter
import com.printwise.export.exportFileName
import com.printwise.image.ImageLoader
import com.printwise.layout.PackItem
import com.printwise.layout.Packer
import com.printwise.layout.PageLayout
import com.printwise.model.Box
import com.printwise.model.DEFAULT_TALLEST_IN
import com.printwise.model.LengthUnit
import com.printwise.model.MARGIN_IN
import com.printwise.model.MIN_PIECE_IN
import com.printwise.model.Orientation
import com.printwise.model.PaperSize
import com.printwise.model.PrintPiece
import com.printwise.model.PrintPlan
import com.printwise.segmentation.SheetSegmentation
import com.printwise.segmentation.SheetSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Segmentation runs on a copy no larger than this; boxes are mapped back to full resolution. */
private const val SEGMENT_EDGE_PX = 1600
private const val UNDO_LIMIT = 50
private const val THUMB_EDGE_PX = 360

enum class Step(val title: String, val number: Int) {
    HOME("Printwise", 0),
    SELECT("Pick pieces", 1),
    SIZE("Choose sizes", 2),
    PRINT("Print", 3)
}

data class DetectedPiece(val id: Int, val box: Box)

sealed interface Detection {
    data object Idle : Detection
    data object Running : Detection
    data class Done(val found: Int) : Detection
    data class Failed(val message: String) : Detection
}

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data class Working(val label: String) : ExportStatus
    data class Done(val title: String, val message: String) : ExportStatus
    data class Failed(val message: String) : ExportStatus
}

enum class TapResult { ADDED, ACTIVATED, NOTHING }

class PrintwiseViewModel(app: Application) : AndroidViewModel(app) {
    var step by mutableStateOf(Step.HOME)
        private set
    var source by mutableStateOf<Bitmap?>(null)
        private set
    var sourceImage by mutableStateOf<ImageBitmap?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var importError by mutableStateOf<String?>(null)
        private set
    var detection by mutableStateOf<Detection>(Detection.Idle)
        private set
    var detected by mutableStateOf<List<DetectedPiece>>(emptyList())
        private set
    var pieces by mutableStateOf<List<PrintPiece>>(emptyList())
        private set
    var activeId by mutableStateOf<String?>(null)
        private set
    var canUndo by mutableStateOf(false)
        private set

    var unit by mutableStateOf(LengthUnit.IN)
    var paper by mutableStateOf(PaperSize.A4)
    var orientation by mutableStateOf(Orientation.BEST)
    var tallestIn by mutableStateOf(DEFAULT_TALLEST_IN)
        private set
    var exportStatus by mutableStateOf<ExportStatus>(ExportStatus.Idle)
        private set

    private var segmentation: SheetSegmentation? = null
    private var segScale = 1f
    private val history = ArrayDeque<List<PrintPiece>>()
    private val thumbnails = HashMap<Box, ImageBitmap>()

    val layout: PageLayout by derivedStateOf {
        val items = pieces.flatMap { piece ->
            val height = heightOf(piece)
            val width = PrintPlan.widthOf(piece, height)
            List(piece.copies) { PackItem(piece.id, width, height) }
        }
        Packer.pack(items, paper, orientation)
    }

    val totalCopies: Int get() = pieces.sumOf { it.copies }

    /** Largest height that can still fit on a page (long side of the printable area). */
    val maxHeightIn: Float get() = max(paper.widthIn, paper.heightIn) - 2 * MARGIN_IN

    // ---- Navigation -------------------------------------------------------------------------------------

    fun goTo(target: Step) {
        if (target != Step.HOME && source == null) return
        activeId = null
        step = target
    }

    /** Returns false when there is nowhere left to go back to (the system should close the app). */
    fun back(): Boolean {
        val previous = when (step) {
            Step.HOME -> return false
            Step.SELECT -> Step.HOME
            Step.SIZE -> Step.SELECT
            Step.PRINT -> Step.SIZE
        }
        goTo(previous)
        return true
    }

    // ---- Import & detection -----------------------------------------------------------------------------

    fun import(uri: Uri) {
        if (loading) return
        loading = true
        importError = null
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { ImageLoader.load(getApplication<Application>().contentResolver, uri) }.getOrNull()
            }
            loading = false
            if (bitmap == null) {
                importError = "We couldn't open that picture. Try a JPG or PNG."
                return@launch
            }
            source = bitmap
            sourceImage = bitmap.asImageBitmap()
            thumbnails.clear()
            history.clear()
            canUndo = false
            pieces = emptyList()
            detected = emptyList()
            activeId = null
            segmentation = null
            tallestIn = DEFAULT_TALLEST_IN
            step = Step.SELECT
            detect(bitmap)
        }
    }

    private suspend fun detect(bitmap: Bitmap) {
        detection = Detection.Running
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val small = ImageLoader.scaleDown(bitmap, SEGMENT_EDGE_PX)
                val pixels = IntArray(small.width * small.height)
                small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
                SheetSegmenter.segment(pixels, small.width, small.height) to small.width.toFloat() / bitmap.width
            }
        }
        if (source !== bitmap) return // another picture was chosen meanwhile
        result.onSuccess { (seg, scale) ->
            segmentation = seg
            segScale = scale
            detected = seg.pieces.map { DetectedPiece(it.id, segToSource(it.left, it.top, it.right, it.bottom)) }
            // Start with everything selected: most people print the whole sheet and remove a few pieces.
            pieces = detected.map { PrintPiece(box = it.box, detectedId = it.id) }
            detection = Detection.Done(detected.size)
        }.onFailure {
            detection = Detection.Failed("Automatic detection didn't work on this picture. Drag on it to draw boxes.")
        }
    }

    private fun segToSource(left: Int, top: Int, right: Int, bottom: Int): Box {
        val image = source ?: return Box(0f, 0f, 0f, 0f)
        val pad = 2f
        return Box((left - pad) / segScale, (top - pad) / segScale, (right + pad) / segScale, (bottom + pad) / segScale)
            .clampTo(image.width.toFloat(), image.height.toFloat())
    }

    // ---- Editing (all coordinates in source-image pixels) -------------------------------------------------

    /** Tap on the sheet outside any selected box: pick the detected piece there, if any. */
    fun selectAt(x: Float, y: Float, radiusPx: Float): TapResult {
        val seg = segmentation
        val hit = seg?.pieceAt((x * segScale).toInt(), (y * segScale).toInt(), max(1, (radiusPx * segScale).toInt()))
        if (hit == null) {
            activeId = null
            return TapResult.NOTHING
        }
        pieces.firstOrNull { it.detectedId == hit.id }?.let {
            activeId = it.id
            return TapResult.ACTIVATED
        }
        val box = detected.firstOrNull { it.id == hit.id }?.box ?: return TapResult.NOTHING
        // Adding doesn't open the edit handles, so several pieces can be tapped in a row.
        commit(pieces + PrintPiece(box = box, detectedId = hit.id))
        activeId = null
        return TapResult.ADDED
    }

    /** A box the user drew; snapped tight around the artwork inside it. */
    fun addDrawn(box: Box) {
        val snapped = segmentation?.contentBounds(
            (box.left * segScale).toInt(), (box.top * segScale).toInt(),
            (box.right * segScale + 1).toInt(), (box.bottom * segScale + 1).toInt()
        )?.let { (l, t, r, b) -> segToSource(l, t, r, b) }
        val final = if (snapped != null && snapped.width >= 8f && snapped.height >= 8f) snapped else box
        val piece = PrintPiece(box = final)
        commit(pieces + piece)
        activeId = piece.id
    }

    fun updateBox(id: String, box: Box) {
        if (box.width < 4f || box.height < 4f) return
        commit(pieces.map { if (it.id == id) it.copy(box = box) else it })
    }

    fun remove(id: String) {
        commit(pieces.filterNot { it.id == id })
        if (activeId == id) activeId = null
    }

    fun activate(id: String?) {
        activeId = id
    }

    fun selectAll() {
        val have = pieces.mapNotNull { it.detectedId }.toSet()
        val missing = detected.filter { it.id !in have }.map { PrintPiece(box = it.box, detectedId = it.id) }
        if (missing.isNotEmpty()) commit(pieces + missing)
    }

    fun clearAll() {
        if (pieces.isNotEmpty()) commit(emptyList())
        activeId = null
    }

    fun undo() {
        val previous = history.removeLastOrNull() ?: return
        pieces = previous
        canUndo = history.isNotEmpty()
        if (pieces.none { it.id == activeId }) activeId = null
    }

    private fun commit(next: List<PrintPiece>) {
        history.addLast(pieces)
        if (history.size > UNDO_LIMIT) history.removeFirst()
        pieces = next
        canUndo = true
    }

    // ---- Sizes ------------------------------------------------------------------------------------------

    fun heightOf(piece: PrintPiece): Float = PrintPlan.heightOf(piece, pieces, tallestIn)

    /** Resizes everything proportionally (the sheet's own relative sizes are kept). */
    fun setTallest(inches: Float) {
        tallestIn = inches.coerceIn(MIN_PIECE_IN, maxHeightIn)
        pieces = pieces.map { it.copy(heightIn = null) }
    }

    fun setHeight(id: String, inches: Float) {
        val clamped = inches.coerceIn(MIN_PIECE_IN, maxHeightIn)
        pieces = pieces.map { if (it.id == id) it.copy(heightIn = clamped) else it }
    }

    fun setCopies(id: String, copies: Int) {
        pieces = pieces.map { if (it.id == id) it.copy(copies = copies.coerceIn(1, 99)) else it }
    }

    fun setAllCopies(copies: Int) {
        pieces = pieces.map { it.copy(copies = copies.coerceIn(1, 99)) }
    }

    fun thumbnail(piece: PrintPiece): ImageBitmap? {
        thumbnails[piece.box]?.let { return it }
        val image = source ?: return null
        val crop = ImageLoader.crop(image, piece.box) ?: return null
        val thumb = ImageLoader.scaleDown(crop, THUMB_EDGE_PX).asImageBitmap()
        if (thumbnails.size > 200) thumbnails.clear()
        thumbnails[piece.box] = thumb
        return thumb
    }

    // ---- Export -----------------------------------------------------------------------------------------

    private fun pageWord(count: Int) = if (count == 1) "1 page" else "$count pages"

    fun savePdf(uri: Uri) = export("Saving your PDF…") { image, plan ->
        write(uri, PdfExporter.render(image, pieces, plan))
        ExportStatus.Done("Saved!", "${pageWord(plan.pageCount)} ready. Print at 100% (Actual size) for exact sizes.")
    }

    fun saveDocx(uri: Uri) = export("Saving your Word file…") { image, plan ->
        write(uri, DocxExporter.render(image, pieces, plan))
        ExportStatus.Done("Saved!", "Word file with ${pageWord(plan.pageCount)} saved. For exact sizes, the PDF is the most reliable.")
    }

    /** Builds the PDF and hands it to [onReady] on the main thread (for printing, which needs an Activity). */
    fun preparePrint(onReady: (pdf: ByteArray, layout: PageLayout) -> Unit) = export("Preparing pages…") { image, plan ->
        val bytes = PdfExporter.render(image, pieces, plan)
        withContext(Dispatchers.Main) { onReady(bytes, plan) }
        ExportStatus.Idle
    }

    fun prepareShare(onReady: (Uri) -> Unit) = export("Preparing to share…") { image, plan ->
        val uri = ShareExporter.shareableUri(getApplication(), PdfExporter.render(image, pieces, plan), exportFileName("pdf"))
        withContext(Dispatchers.Main) { onReady(uri) }
        ExportStatus.Idle
    }

    fun dismissExportStatus() {
        exportStatus = ExportStatus.Idle
    }

    private fun write(uri: Uri, bytes: ByteArray) {
        val stream = getApplication<Application>().contentResolver.openOutputStream(uri)
            ?: throw java.io.IOException("Couldn't open the file you picked")
        stream.use { it.write(bytes) }
    }

    private fun export(label: String, block: suspend (Bitmap, PageLayout) -> ExportStatus) {
        val image = source ?: return
        if (exportStatus is ExportStatus.Working) return
        val plan = layout
        exportStatus = ExportStatus.Working(label)
        viewModelScope.launch {
            exportStatus = withContext(Dispatchers.IO) {
                try {
                    block(image, plan)
                } catch (error: Throwable) {
                    ExportStatus.Failed(error.message ?: "Something went wrong while exporting.")
                }
            }
        }
    }
}
