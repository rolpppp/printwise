package com.printwise

import android.app.Application
import android.content.Context
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
import com.printwise.image.SampleSheet
import com.printwise.layout.FreeSpace
import com.printwise.layout.PackItem
import com.printwise.layout.Packer
import com.printwise.layout.PageLayout
import com.printwise.layout.Placement
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
import java.util.UUID
import kotlin.math.max

/** Segmentation runs on a copy no larger than this; boxes are mapped back to full resolution. */
private const val SEGMENT_EDGE_PX = 1600
private const val UNDO_LIMIT = 50
private const val THUMB_EDGE_PX = 360
/** Each picture is held at working resolution, so cap how many a project can hold. */
const val MAX_SHEETS = 6
private const val PREFS = "printwise"
private const val KEY_SHOW_PRINT_GUIDE = "show_print_guide"
private const val KEY_SEEN_WELCOME = "seen_welcome"

enum class Step(val title: String, val number: Int) {
    HOME("Printwise", 0),
    SELECT("Pick pieces", 1),
    SIZE("Set sizes", 2),
    PRINT("Preview & print", 3)
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

/** One picture the user picked pieces from. Piece boxes are in this bitmap's pixels. */
data class Sheet(
    val id: String,
    val bitmap: Bitmap,
    val image: ImageBitmap,
    val segmentation: SheetSegmentation? = null,
    val segScale: Float = 1f,
    val detected: List<DetectedPiece> = emptyList(),
    val detection: Detection = Detection.Running
)

class PrintwiseViewModel(app: Application) : AndroidViewModel(app) {
    var step by mutableStateOf(Step.HOME)
        private set
    var sheets by mutableStateOf<List<Sheet>>(emptyList())
        private set
    var activeSheetId by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var importError by mutableStateOf<String?>(null)
        private set
    var pieces by mutableStateOf<List<PrintPiece>>(emptyList())
        private set
    var activeId by mutableStateOf<String?>(null)
        private set
    var canUndo by mutableStateOf(false)
        private set

    var unit by mutableStateOf(LengthUnit.IN)
    var paper by mutableStateOf(PaperSize.A4)
        private set
    var orientation by mutableStateOf(Orientation.BEST)
        private set
    var exportStatus by mutableStateOf<ExportStatus>(ExportStatus.Idle)
        private set

    /** Pages the user has edited by hand on the print step; null while pages follow the automatic packing. */
    var customLayout by mutableStateOf<PageLayout?>(null)
        private set
    /** A change that would re-pack hand-edited pages, waiting for the user to confirm. */
    var pendingChange by mutableStateOf<(() -> Unit)?>(null)
        private set

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    /** Whether the photo-paper printing guide opens before each print (on until the user turns it off). */
    var showPrintGuide by mutableStateOf(prefs.getBoolean(KEY_SHOW_PRINT_GUIDE, true))
        private set
    /** Whether the welcome screens have been seen (or skipped) once; they open by themselves only on first launch. */
    var seenWelcome by mutableStateOf(prefs.getBoolean(KEY_SEEN_WELCOME, false))
        private set

    private val history = ArrayDeque<List<PrintPiece>>()
    private val thumbnails = HashMap<Pair<String, Box>, ImageBitmap>()

    val activeSheet: Sheet? get() = sheets.firstOrNull { it.id == activeSheetId }
    val source: Bitmap? get() = activeSheet?.bitmap
    val sourceImage: ImageBitmap? get() = activeSheet?.image
    val detected: List<DetectedPiece> get() = activeSheet?.detected.orEmpty()
    val detection: Detection get() = activeSheet?.detection ?: Detection.Idle
    /** Pieces cut from the picture currently open in the editor. */
    val sheetPieces: List<PrintPiece> get() = pieces.filter { it.sourceId == activeSheetId }
    val canAddSheet: Boolean get() = sheets.size < MAX_SHEETS && !loading

    private val autoLayout: PageLayout by derivedStateOf {
        val items = pieces.flatMap { piece ->
            val height = heightOf(piece)
            val width = PrintPlan.widthOf(piece, height)
            List(piece.copies) { PackItem(piece.id, width, height) }
        }
        Packer.pack(items, paper, orientation)
    }

    val layout: PageLayout get() = customLayout ?: autoLayout

    val totalCopies: Int get() = pieces.sumOf { it.copies }

    /** Largest height that can still fit on a page (long side of the printable area). */
    val maxHeightIn: Float get() = max(paper.widthIn, paper.heightIn) - 2 * MARGIN_IN

    // ---- Navigation -------------------------------------------------------------------------------------

    fun goTo(target: Step) {
        if (target != Step.HOME && sheets.isEmpty()) return
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

    // ---- Re-pack guard ----------------------------------------------------------------------------------

    /** Runs [change] now, or, when pages were edited by hand, holds it until the user agrees to re-pack. */
    private fun guardLayout(change: () -> Unit) {
        if (customLayout == null) change() else pendingChange = change
    }

    fun confirmPendingChange() {
        val change = pendingChange ?: return
        pendingChange = null
        customLayout = null
        change()
    }

    fun cancelPendingChange() {
        pendingChange = null
    }

    // ---- Import & detection -----------------------------------------------------------------------------

    /** A new project from one picture (Home, or "Share → Printwise"). */
    fun import(uri: Uri) = load(uri, ::startProject)

    /** A new project from the built-in sample sheet, for the guided tour. */
    fun importSample() = load({ SampleSheet.render() }, ::startProject)

    private fun startProject(sheet: Sheet) {
        thumbnails.clear()
        history.clear()
        canUndo = false
        pieces = emptyList()
        customLayout = null
        pendingChange = null
        activeId = null
        sheets = listOf(sheet)
        activeSheetId = sheet.id
        step = Step.SELECT
    }

    /** Another picture in the same project, for pieces that live on a different sheet. */
    fun addSheet(uri: Uri) {
        if (!canAddSheet) return
        guardLayout {
            load(uri) { sheet ->
                sheets = sheets + sheet
                activeSheetId = sheet.id
                activeId = null
            }
        }
    }

    fun showSheet(id: String) {
        if (sheets.none { it.id == id }) return
        activeSheetId = id
        activeId = null
    }

    /** Drops a picture and every piece cut from it. Undo history is cleared so it can't bring those pieces back. */
    fun removeSheet(id: String) {
        if (sheets.size <= 1) return
        guardLayout {
            val index = sheets.indexOfFirst { it.id == id }
            if (index < 0) return@guardLayout
            sheets = sheets.filterNot { it.id == id }
            pieces = pieces.filterNot { it.sourceId == id }
            history.clear()
            canUndo = false
            activeId = null
            if (activeSheetId == id) activeSheetId = sheets[(index - 1).coerceAtLeast(0)].id
        }
    }

    private fun load(uri: Uri, onLoaded: (Sheet) -> Unit) =
        load({ ImageLoader.load(getApplication<Application>().contentResolver, uri) }, onLoaded)

    private fun load(decode: () -> Bitmap?, onLoaded: (Sheet) -> Unit) {
        if (loading) return
        loading = true
        importError = null
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { runCatching(decode).getOrNull() }
            loading = false
            if (bitmap == null) {
                importError = "We couldn't open that picture. Try a JPG or PNG."
                return@launch
            }
            val sheet = Sheet(id = UUID.randomUUID().toString(), bitmap = bitmap, image = bitmap.asImageBitmap())
            onLoaded(sheet)
            detect(sheet.id)
        }
    }

    private fun updateSheet(id: String, change: (Sheet) -> Sheet) {
        sheets = sheets.map { if (it.id == id) change(it) else it }
    }

    private suspend fun detect(sheetId: String) {
        val bitmap = sheets.firstOrNull { it.id == sheetId }?.bitmap ?: return
        val result = withContext(Dispatchers.Default) {
            runCatching {
                val small = ImageLoader.scaleDown(bitmap, SEGMENT_EDGE_PX)
                val pixels = IntArray(small.width * small.height)
                small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
                SheetSegmenter.segment(pixels, small.width, small.height) to small.width.toFloat() / bitmap.width
            }
        }
        if (sheets.none { it.id == sheetId }) return // removed, or another project started, meanwhile
        result.onSuccess { (seg, scale) ->
            val found = seg.pieces.map { DetectedPiece(it.id, segToSource(bitmap, scale, it.left, it.top, it.right, it.bottom)) }
            updateSheet(sheetId) { it.copy(segmentation = seg, segScale = scale, detected = found, detection = Detection.Done(found.size)) }
            // Start with everything selected: most people print the whole sheet and remove a few pieces.
            pieces = pieces + found.map { PrintPiece(box = it.box, sourceId = sheetId, detectedId = it.id) }
        }.onFailure {
            updateSheet(sheetId) {
                it.copy(detection = Detection.Failed("Automatic detection didn't work on this picture. Drag on it to draw boxes."))
            }
        }
    }

    private fun segToSource(image: Bitmap, scale: Float, left: Int, top: Int, right: Int, bottom: Int): Box {
        val pad = 2f
        return Box((left - pad) / scale, (top - pad) / scale, (right + pad) / scale, (bottom + pad) / scale)
            .clampTo(image.width.toFloat(), image.height.toFloat())
    }

    // ---- Editing (all coordinates in the active sheet's pixels) ------------------------------------------

    /** Tap on the sheet outside any selected box: pick the detected piece there, if any. */
    fun selectAt(x: Float, y: Float, radiusPx: Float): TapResult {
        val sheet = activeSheet ?: return TapResult.NOTHING
        val scale = sheet.segScale
        val hit = sheet.segmentation?.pieceAt((x * scale).toInt(), (y * scale).toInt(), max(1, (radiusPx * scale).toInt()))
        if (hit == null) {
            activeId = null
            return TapResult.NOTHING
        }
        sheetPieces.firstOrNull { it.detectedId == hit.id }?.let {
            activeId = it.id
            return TapResult.ACTIVATED
        }
        val box = sheet.detected.firstOrNull { it.id == hit.id }?.box ?: return TapResult.NOTHING
        // Adding doesn't open the edit handles, so several pieces can be tapped in a row.
        commit(pieces + PrintPiece(box = box, sourceId = sheet.id, detectedId = hit.id))
        activeId = null
        return TapResult.ADDED
    }

    /** A box the user drew; snapped tight around the artwork inside it. */
    fun addDrawn(box: Box) {
        val sheet = activeSheet ?: return
        val scale = sheet.segScale
        val snapped = sheet.segmentation?.contentBounds(
            (box.left * scale).toInt(), (box.top * scale).toInt(),
            (box.right * scale + 1).toInt(), (box.bottom * scale + 1).toInt()
        )?.let { (l, t, r, b) -> segToSource(sheet.bitmap, scale, l, t, r, b) }
        val final = if (snapped != null && snapped.width >= 8f && snapped.height >= 8f) snapped else box
        val piece = PrintPiece(box = final, sourceId = sheet.id)
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

    /** Opens [id] for editing, switching to its picture if it was cut from another one. */
    fun activate(id: String?) {
        pieces.firstOrNull { it.id == id }?.let { if (it.sourceId != activeSheetId) activeSheetId = it.sourceId }
        activeId = id
    }

    fun selectAll() {
        val sheet = activeSheet ?: return
        val have = sheetPieces.mapNotNull { it.detectedId }.toSet()
        val missing = sheet.detected.filter { it.id !in have }.map { PrintPiece(box = it.box, sourceId = sheet.id, detectedId = it.id) }
        if (missing.isNotEmpty()) commit(pieces + missing)
    }

    /** Clears the pieces of the picture on screen; other pictures keep theirs. */
    fun clearAll() {
        if (sheetPieces.isNotEmpty()) commit(pieces.filterNot { it.sourceId == activeSheetId })
        activeId = null
    }

    fun undo() = guardLayout {
        val previous = history.removeLastOrNull() ?: return@guardLayout
        pieces = previous
        canUndo = history.isNotEmpty()
        if (pieces.none { it.id == activeId }) activeId = null
    }

    private fun commit(next: List<PrintPiece>) = guardLayout {
        history.addLast(pieces)
        if (history.size > UNDO_LIMIT) history.removeFirst()
        pieces = next
        canUndo = true
    }

    // ---- Sizes ------------------------------------------------------------------------------------------

    fun heightOf(piece: PrintPiece): Float = PrintPlan.heightOf(piece, pieces, DEFAULT_TALLEST_IN)

    fun setHeight(id: String, inches: Float) = guardLayout {
        val clamped = inches.coerceIn(MIN_PIECE_IN, maxHeightIn)
        pieces = pieces.map { if (it.id == id) it.copy(heightIn = clamped) else it }
    }

    fun setCopies(id: String, copies: Int) = guardLayout {
        pieces = pieces.map { if (it.id == id) it.copy(copies = copies.coerceIn(1, 99)) else it }
    }

    /** The user's name for [piece], or "Piece N" by its position across all pictures. */
    fun nameOf(piece: PrintPiece): String =
        piece.name?.takeIf { it.isNotBlank() } ?: "Piece ${pieces.indexOfFirst { it.id == piece.id } + 1}"

    /** Renaming changes nothing on the pages, so it skips undo and the re-pack guard. */
    fun rename(id: String, name: String) {
        pieces = pieces.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { null }) else it }
    }

    /**
     * Adds an independent copy of [id] right after it: same artwork, size and copies, sized separately from now on.
     * It drops the link to the detected piece so tapping that piece on the sheet still finds the original.
     */
    fun duplicate(id: String) {
        val index = pieces.indexOfFirst { it.id == id }
        if (index < 0) return
        val original = pieces[index]
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            detectedId = null,
            heightIn = heightOf(original),
            name = original.name?.let { "$it (copy)" }
        )
        commit(pieces.toMutableList().apply { add(index + 1, copy) })
    }

    fun changePaper(value: PaperSize) {
        if (value != paper) guardLayout { paper = value }
    }

    fun changeOrientation(value: Orientation) {
        if (value != orientation) guardLayout { orientation = value }
    }

    fun thumbnail(piece: PrintPiece): ImageBitmap? {
        val key = piece.sourceId to piece.box
        thumbnails[key]?.let { return it }
        val image = sheets.firstOrNull { it.id == piece.sourceId }?.bitmap ?: return null
        val crop = ImageLoader.crop(image, piece.box) ?: return null
        val thumb = ImageLoader.scaleDown(crop, THUMB_EDGE_PX).asImageBitmap()
        if (thumbnails.size > 200) thumbnails.clear()
        thumbnails[key] = thumb
        return thumb
    }

    // ---- Hand edits on the print step (one placed copy at a time) ---------------------------------------

    fun piece(id: String): PrintPiece? = pieces.firstOrNull { it.id == id }

    /** Whether [piece] at its set size fits inside the margins of the current page shape at all. */
    fun fitsOnPage(piece: PrintPiece): Boolean {
        val height = heightOf(piece)
        return FreeSpace.fits(MARGIN_IN, MARGIN_IN, PrintPlan.widthOf(piece, height), height, layout.pageWidthIn, layout.pageHeightIn)
    }

    /** Adds one more copy of [pieceId], at its set size, into free space on [page]. False when there's no room. */
    fun addToPage(pieceId: String, page: Int): Boolean {
        val piece = piece(pieceId) ?: return false
        val current = layout
        if (page !in 0 until current.pageCount) return false
        val height = heightOf(piece)
        val width = PrintPlan.widthOf(piece, height)
        val (x, y) = FreeSpace.find(current.placements.filter { it.page == page }, width, height, current.pageWidthIn, current.pageHeightIn)
            ?: return false
        customLayout = current.copy(placements = current.placements + Placement(piece.id, page, x, y, width, height))
        return true
    }

    /**
     * Resizes one placed copy only; other copies of the same piece and its set size are untouched. It stays put
     * when the new size fits there, otherwise it moves to free space on the same page. False when neither works.
     */
    fun resizePlacement(id: String, heightIn: Float): Boolean {
        val current = layout
        val placed = current.placements.firstOrNull { it.id == id } ?: return false
        val piece = piece(placed.pieceId) ?: return false
        val height = heightIn.coerceAtLeast(MIN_PIECE_IN)
        val width = PrintPlan.widthOf(piece, height)
        val onPage = current.placements.filter { it.page == placed.page }
        val staysPut = FreeSpace.fits(placed.xIn, placed.yIn, width, height, current.pageWidthIn, current.pageHeightIn) &&
            onPage.none { it.id != id && FreeSpace.overlaps(placed.xIn, placed.yIn, width, height, it) }
        val (x, y) = if (staysPut) placed.xIn to placed.yIn
        else FreeSpace.find(onPage, width, height, current.pageWidthIn, current.pageHeightIn, ignore = id) ?: return false
        customLayout = current.copy(
            placements = current.placements.map { if (it.id == id) it.copy(xIn = x, yIn = y, widthIn = width, heightIn = height) else it }
        )
        return true
    }

    fun removePlacement(id: String) {
        val current = layout
        customLayout = current.copy(placements = current.placements.filterNot { it.id == id })
    }

    /** Adds a blank page at the end and returns its index. */
    fun addPage(): Int {
        val current = layout
        customLayout = current.copy(pageCount = current.pageCount + 1)
        return current.pageCount
    }

    /** Removes [page] if it is empty and not the only one; later pages move up. */
    fun removePage(page: Int) {
        val current = layout
        if (current.pageCount <= 1 || current.placements.any { it.page == page }) return
        customLayout = current.copy(
            pageCount = current.pageCount - 1,
            placements = current.placements.map { if (it.page > page) it.copy(page = it.page - 1) else it }
        )
    }

    /** How many different pieces have at least one copy on the pages that may print blurry. */
    val blurryPieceCount: Int
        get() = layout.placements
            .filter { placed -> piece(placed.pieceId)?.let { PrintPlan.mayLookBlurry(it, placed.heightIn) } == true }
            .distinctBy { it.pieceId }
            .size

    fun changeShowPrintGuide(show: Boolean) {
        showPrintGuide = show
        prefs.edit().putBoolean(KEY_SHOW_PRINT_GUIDE, show).apply()
    }

    fun markWelcomeSeen() {
        if (seenWelcome) return
        seenWelcome = true
        prefs.edit().putBoolean(KEY_SEEN_WELCOME, true).apply()
    }

    // ---- Export -----------------------------------------------------------------------------------------

    private fun pageWord(count: Int) = if (count == 1) "1 page" else "$count pages"

    fun savePdf(uri: Uri) = export("Saving your PDF…") { sources, plan ->
        write(uri, PdfExporter.render(sources, pieces, plan))
        ExportStatus.Done("Saved!", "${pageWord(plan.pageCount)} ready. Print at 100% (Actual size) for exact sizes.")
    }

    fun saveDocx(uri: Uri) = export("Saving your Word file…") { sources, plan ->
        write(uri, DocxExporter.render(sources, pieces, plan))
        ExportStatus.Done("Saved!", "Word file with ${pageWord(plan.pageCount)} saved. For exact sizes, the PDF is the most reliable.")
    }

    /** Builds the PDF and hands it to [onReady] on the main thread (for printing, which needs an Activity). */
    fun preparePrint(onReady: (pdf: ByteArray, layout: PageLayout) -> Unit) = export("Preparing pages…") { sources, plan ->
        val bytes = PdfExporter.render(sources, pieces, plan)
        withContext(Dispatchers.Main) { onReady(bytes, plan) }
        ExportStatus.Idle
    }

    fun prepareShare(onReady: (Uri) -> Unit) = export("Preparing to share…") { sources, plan ->
        val uri = ShareExporter.shareableUri(getApplication(), PdfExporter.render(sources, pieces, plan), exportFileName("pdf"))
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

    private fun export(label: String, block: suspend (Map<String, Bitmap>, PageLayout) -> ExportStatus) {
        val sources = sheets.associate { it.id to it.bitmap }
        if (sources.isEmpty() || exportStatus is ExportStatus.Working) return
        val plan = layout
        exportStatus = ExportStatus.Working(label)
        viewModelScope.launch {
            exportStatus = withContext(Dispatchers.IO) {
                try {
                    block(sources, plan)
                } catch (error: Throwable) {
                    ExportStatus.Failed(error.message ?: "Something went wrong while exporting.")
                }
            }
        }
    }
}
