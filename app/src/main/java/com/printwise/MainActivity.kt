package com.printwise

import android.app.Activity
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import com.printwise.ai.GeminiClassSuggestionService
import com.printwise.ai.MlKitCropSuggestionService
import kotlinx.coroutines.launch

private const val APP_NAME = "Printwise"
private const val A4_WIDTH = 8.27f
private const val A4_HEIGHT = 11.69f
private const val DEFAULT_MARGIN = 0.25f
private const val DEFAULT_GAP = 0.10f

data class Crop(
    val id: String = UUID.randomUUID().toString(),
    val sourceRect: RectF,
    val aspectRatio: Float,
    val classId: String
)

data class PrintClass(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val heightIn: String = "",
    val requestedCopies: String = "1",
    val crops: List<Crop> = emptyList()
)

data class PlacedItem(
    val cropId: String,
    val classId: String,
    val pageIndex: Int,
    val xIn: Float,
    val yIn: Float,
    val widthIn: Float,
    val heightIn: Float
)

data class Project(
    val sourceUri: Uri? = null,
    val sourceWidthPx: Int = 0,
    val sourceHeightPx: Int = 0,
    val classes: List<PrintClass> = emptyList(),
    val placedItems: List<PlacedItem> = emptyList(),
    val landscape: Boolean = false
)

private enum class Screen { IMPORT, CLASSES, CROPS, SIZES, QUANTITIES, LAYOUT, EXPORT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PrintwiseApp(this) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrintwiseApp(activity: Activity) {
    var screen by remember { mutableStateOf(Screen.IMPORT) }
    var project by remember { mutableStateOf(Project()) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bitmap = decodePreview(activity.contentResolver, uri)
            if (bitmap != null) {
                project = Project(
                    sourceUri = uri,
                    sourceWidthPx = bitmap.width,
                    sourceHeightPx = bitmap.height,
                    classes = defaultClasses(bitmap.width, bitmap.height)
                )
                preview = bitmap
                screen = Screen.CLASSES
            }
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(APP_NAME) }) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.IMPORT -> ImportScreen { importLauncher.launch("image/*") }
                Screen.CLASSES -> ClassesScreen(preview, project.classes, { project = project.copy(classes = it) }) {
                    screen = Screen.CROPS
                }
                Screen.CROPS -> CropScreen(preview, project.classes, { project = project.copy(classes = it) }) {
                    screen = Screen.SIZES
                }
                Screen.SIZES -> SizeScreen(project.classes, { project = project.copy(classes = it) }) {
                    screen = Screen.QUANTITIES
                }
                Screen.QUANTITIES -> QuantityScreen(project.classes, { project = project.copy(classes = it) }) {
                    project = project.copy(placedItems = pack(project.classes, project.landscape))
                    screen = Screen.LAYOUT
                }
                Screen.LAYOUT -> LayoutScreen(project, { project = it }) {
                    project = project.copy(placedItems = pack(project.classes, project.landscape))
                    screen = Screen.EXPORT
                }
                Screen.EXPORT -> ExportScreen(activity, project)
            }
        }
    }
}

@Composable
private fun ImportScreen(onImport: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Print cake decorations at the right size.", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text("Start with one JPG or PNG image.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImport) { Text("Import image") }
    }
}

@Composable
private fun ClassesScreen(
    preview: Bitmap?,
    classes: List<PrintClass>,
    onChange: (List<PrintClass>) -> Unit,
    onNext: () -> Unit
) {
    var newLabel by remember { mutableStateOf("") }
    var aiMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Confirm classes", style = MaterialTheme.typography.headlineSmall)
        Text("Rename, remove, or add groups. Suggestions are only a starting point.")
        Button(
            onClick = {
                if (preview == null) return@Button
                scope.launch {
                    aiMessage = "Asking Gemini backend for class suggestions..."
                    runCatching {
                        GeminiClassSuggestionService(preview, BuildConfig.GEMINI_BACKEND_URL).suggestClasses()
                    }.onSuccess { suggestions ->
                        onChange(suggestions.map { PrintClass(label = it.label) })
                        aiMessage = "Gemini suggestions added. Confirm the labels and assign crops manually."
                    }.onFailure { error ->
                        aiMessage = error.message ?: "Gemini suggestion failed."
                    }
                }
            },
            enabled = preview != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Suggest classes with Gemini") }
        if (aiMessage.isNotBlank()) Text(aiMessage)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(classes, key = { it.id }) { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = item.label,
                        onValueChange = { value -> onChange(classes.map { if (it.id == item.id) it.copy(label = value) else it }) },
                        label = { Text("Class") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = {
                        val split = item.copy(id = UUID.randomUUID().toString(), label = "${item.label} 2")
                        onChange(classes + split)
                    }) { Text("Split") }
                    OutlinedButton(onClick = {
                        val index = classes.indexOfFirst { it.id == item.id }
                        if (index > 0) {
                            val previous = classes[index - 1]
                            onChange(classes.filterNot { it.id == item.id }.map {
                                if (it.id == previous.id) it.copy(crops = it.crops + item.crops) else it
                            })
                        }
                    }, enabled = classes.indexOfFirst { it.id == item.id } > 0) { Text("Merge") }
                    OutlinedButton(onClick = { onChange(classes.filterNot { it.id == item.id }) }) { Text("Remove") }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newLabel, { newLabel = it }, label = { Text("New class") }, modifier = Modifier.weight(1f))
            Button(onClick = {
                if (newLabel.isNotBlank()) {
                    onChange(classes + PrintClass(label = newLabel.trim()))
                    newLabel = ""
                }
            }) { Text("Add") }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onNext, enabled = classes.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Review crops") }
    }
}

@Composable
private fun CropScreen(
    preview: Bitmap?,
    classes: List<PrintClass>,
    onChange: (List<PrintClass>) -> Unit,
    onNext: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var aiMessage by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf(classes.firstOrNull()?.id) }
    var cropLeft by remember { mutableStateOf("0") }
    var cropTop by remember { mutableStateOf("0") }
    var cropRight by remember { mutableStateOf((preview?.width ?: 1).toString()) }
    var cropBottom by remember { mutableStateOf((preview?.height ?: 1).toString()) }
    val selected = classes.firstOrNull { it.id == selectedClass }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Review crops", style = MaterialTheme.typography.headlineSmall)
        Text("Each crop is separate from its class and can be corrected manually.")
        preview?.let { Image(it.asImageBitmap(), "Imported image", Modifier.fillMaxWidth().height(180.dp)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            classes.forEach { item ->
                OutlinedButton(onClick = { selectedClass = item.id }) { Text(item.label) }
            }
        }
        Text("Crop rectangle in source pixels")
        Button(
            onClick = {
                if (preview == null || selected == null) return@Button
                scope.launch {
                    aiMessage = "ML Kit is suggesting crops..."
                    runCatching { MlKitCropSuggestionService(preview).suggestCrops() }
                        .onSuccess { suggestions ->
                            val crops = suggestions.map {
                                Crop(
                                    sourceRect = it.sourceRect,
                                    aspectRatio = it.sourceRect.width() / it.sourceRect.height(),
                                    classId = selected.id
                                )
                            }
                            onChange(classes.map { if (it.id == selected.id) it.copy(crops = it.crops + crops) else it })
                            aiMessage = "ML Kit suggestions added. Review and correct them below."
                        }
                        .onFailure { error -> aiMessage = error.message ?: "ML Kit suggestion failed." }
                }
            },
            enabled = preview != null && selected != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Suggest crops with ML Kit") }
        if (aiMessage.isNotBlank()) Text(aiMessage)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallField("Left", cropLeft) { cropLeft = it }
            SmallField("Top", cropTop) { cropTop = it }
            SmallField("Right", cropRight) { cropRight = it }
            SmallField("Bottom", cropBottom) { cropBottom = it }
        }
        Button(
            onClick = {
                val left = cropLeft.toFloatOrNull() ?: return@Button
                val top = cropTop.toFloatOrNull() ?: return@Button
                val right = cropRight.toFloatOrNull() ?: return@Button
                val bottom = cropBottom.toFloatOrNull() ?: return@Button
                if (selected != null && right > left && bottom > top) {
                    val crop = Crop(sourceRect = RectF(left, top, right, bottom), aspectRatio = (right - left) / (bottom - top), classId = selected.id)
                    onChange(classes.map { if (it.id == selected.id) it.copy(crops = it.crops + crop) else it })
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add crop to selected class") }
        LazyColumn(Modifier.weight(1f)) {
            items(classes, key = { it.id }) { item ->
                Text("${item.label}: ${item.crops.size} crop(s)")
            }
        }
        Button(onClick = onNext, enabled = classes.any { it.crops.isNotEmpty() }, modifier = Modifier.fillMaxWidth()) {
            Text("Set sizes")
        }
    }
}

@Composable
private fun SmallField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.width(84.dp), singleLine = true)
}

@Composable
private fun SizeScreen(classes: List<PrintClass>, onChange: (List<PrintClass>) -> Unit, onNext: () -> Unit) {
    val valid = classes.isNotEmpty() && classes.all { (it.heightIn.toFloatOrNull() ?: 0f) > 0f && it.crops.isNotEmpty() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Set sizes", style = MaterialTheme.typography.headlineSmall)
        Text("Enter the finished height in inches. Width follows each crop's aspect ratio.")
        LazyColumn(Modifier.weight(1f)) {
            items(classes, key = { it.id }) { item ->
                OutlinedTextField(
                    item.heightIn,
                    { value -> onChange(classes.map { if (it.id == item.id) it.copy(heightIn = value) else it }) },
                    label = { Text("${item.label} height (in)") },
                    supportingText = { Text("${item.crops.size} crop(s), locked during layout") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        Button(onClick = onNext, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("See page capacity") }
    }
}

@Composable
private fun QuantityScreen(classes: List<PrintClass>, onChange: (List<PrintClass>) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Choose quantities", style = MaterialTheme.typography.headlineSmall)
        Text("Capacity is an estimate for each class printed by itself. Copies are always your choice.")
        LazyColumn(Modifier.weight(1f)) {
            items(classes, key = { it.id }) { item ->
                val height = item.heightIn.toFloatOrNull() ?: 0f
                val capacity = classCapacity(item, height)
                OutlinedTextField(
                    item.requestedCopies,
                    { value -> onChange(classes.map { if (it.id == item.id) it.copy(requestedCopies = value) else it }) },
                    label = { Text("${item.label} copies (capacity ~${capacity})") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Arrange pages") }
    }
}

@Composable
private fun LayoutScreen(project: Project, onChange: (Project) -> Unit, onNext: () -> Unit) {
    val width = if (project.landscape) A4_HEIGHT else A4_WIDTH
    val height = if (project.landscape) A4_WIDTH else A4_HEIGHT
    var selectedIndex by remember { mutableStateOf(0) }
    fun moveSelected(dx: Float, dy: Float) {
        val item = project.placedItems.getOrNull(selectedIndex) ?: return
        val moved = item.copy(
            xIn = (item.xIn + dx).coerceIn(DEFAULT_MARGIN, width - DEFAULT_MARGIN - item.widthIn),
            yIn = (item.yIn + dy).coerceIn(DEFAULT_MARGIN, height - DEFAULT_MARGIN - item.heightIn)
        )
        onChange(project.copy(placedItems = project.placedItems.toMutableList().also { it[selectedIndex] = moved }))
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Arrange pages", style = MaterialTheme.typography.headlineSmall)
        Text("${project.placedItems.maxOfOrNull { it.pageIndex + 1 } ?: 0} page(s), ${if (project.landscape) "landscape" else "portrait"}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onChange(project.copy(landscape = false, placedItems = pack(project.classes, false))) }) { Text("Portrait") }
            OutlinedButton(onClick = { onChange(project.copy(landscape = true, placedItems = pack(project.classes, true))) }) { Text("Landscape") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            project.placedItems.take(8).forEachIndexed { index, _ ->
                OutlinedButton(onClick = { selectedIndex = index }) { Text("${index + 1}") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { moveSelected(-DEFAULT_GAP, 0f) }) { Text("Left") }
            OutlinedButton(onClick = { moveSelected(DEFAULT_GAP, 0f) }) { Text("Right") }
            OutlinedButton(onClick = { moveSelected(0f, -DEFAULT_GAP) }) { Text("Up") }
            OutlinedButton(onClick = { moveSelected(0f, DEFAULT_GAP) }) { Text("Down") }
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(16.dp)) {
            val scale = minOf(size.width / width, size.height / height)
            drawRect(ComposeColor.White, size = androidx.compose.ui.geometry.Size(width * scale, height * scale))
            project.placedItems.filter { it.pageIndex == 0 }.forEach { item ->
                drawRect(
                    ComposeColor(0xFFB39DDB),
                    topLeft = Offset(item.xIn * scale, item.yIn * scale),
                    size = androidx.compose.ui.geometry.Size(item.widthIn * scale, item.heightIn * scale)
                )
            }
        }
        Text("Items stay at their specified physical size. More pages are added when needed.")
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Export") }
    }
}

@Composable
private fun ExportScreen(activity: Activity, project: Project) {
    var message by remember { mutableStateOf("PDF is the reference output for exact size.") }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            writeToUri(activity, uri) { exportPdf(activity, project) }
            message = "PDF saved."
        }
    }
    val docxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { uri ->
        if (uri != null) {
            writeToUri(activity, uri) { exportDocx(activity, project) }
            message = "Word document saved."
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Export", style = MaterialTheme.typography.headlineSmall)
        Text("Use actual size / 100% when printing the PDF.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = { pdfLauncher.launch("printwise.pdf") }, modifier = Modifier.fillMaxWidth()) { Text("Save PDF") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { docxLauncher.launch("printwise.docx") }, modifier = Modifier.fillMaxWidth()) { Text("Save Word document") }
        Spacer(Modifier.height(16.dp))
        Text(message)
    }
}

private fun defaultClasses(width: Int, height: Int): List<PrintClass> {
    val item = PrintClass(label = "Figures")
    return listOf(
        item.copy(crops = listOf(
        Crop(
            sourceRect = RectF(0f, 0f, width.toFloat(), height.toFloat()),
            aspectRatio = width.toFloat() / height,
            classId = item.id
        )
        )),
        PrintClass(label = "Hats"),
        PrintClass(label = "Letters")
    )
}

private fun classCapacity(item: PrintClass, heightIn: Float): Int {
    if (heightIn <= 0f || item.crops.isEmpty()) return 0
    val usableWidth = A4_WIDTH - 2 * DEFAULT_MARGIN
    val usableHeight = A4_HEIGHT - 2 * DEFAULT_MARGIN
    return item.crops.sumOf { crop ->
        val w = heightIn * crop.aspectRatio
        if (w <= usableWidth && heightIn <= usableHeight) {
            max(1, floorInt((usableWidth + DEFAULT_GAP) / (w + DEFAULT_GAP))) *
                max(1, floorInt((usableHeight + DEFAULT_GAP) / (heightIn + DEFAULT_GAP)))
        } else 0
    }.coerceAtLeast(0)
}

private fun pack(classes: List<PrintClass>, landscape: Boolean): List<PlacedItem> {
    val pageWidth = if (landscape) A4_HEIGHT else A4_WIDTH
    val pageHeight = if (landscape) A4_WIDTH else A4_HEIGHT
    val usableWidth = pageWidth - 2 * DEFAULT_MARGIN
    val usableHeight = pageHeight - 2 * DEFAULT_MARGIN
    val placements = mutableListOf<PlacedItem>()
    var page = 0
    var x = DEFAULT_MARGIN
    var y = DEFAULT_MARGIN
    var rowHeight = 0f
    classes.forEach { item ->
        val height = item.heightIn.toFloatOrNull() ?: return@forEach
        val copies = item.requestedCopies.toIntOrNull()?.coerceAtLeast(0) ?: 0
        repeat(copies) {
            item.crops.forEach { crop ->
                val width = height * crop.aspectRatio
                if (width > usableWidth || height > usableHeight) return@forEach
                if (x + width > pageWidth - DEFAULT_MARGIN) {
                    x = DEFAULT_MARGIN
                    y += rowHeight + DEFAULT_GAP
                    rowHeight = 0f
                }
                if (y + height > pageHeight - DEFAULT_MARGIN) {
                    page++
                    x = DEFAULT_MARGIN
                    y = DEFAULT_MARGIN
                    rowHeight = 0f
                }
                placements += PlacedItem(crop.id, item.id, page, x, y, width, height)
                x += width + DEFAULT_GAP
                rowHeight = max(rowHeight, height)
            }
        }
    }
    return placements
}

private fun floorInt(value: Float): Int = value.toInt()

private fun decodePreview(resolver: ContentResolver, uri: Uri): Bitmap? =
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }

private fun writeToUri(activity: Activity, uri: Uri, producer: () -> ByteArray) {
    activity.contentResolver.openOutputStream(uri)?.use { it.write(producer()) }
}

private fun exportPdf(activity: Activity, project: Project): ByteArray {
    val widthPt = (if (project.landscape) A4_HEIGHT else A4_WIDTH) * 72
    val heightPt = (if (project.landscape) A4_WIDTH else A4_HEIGHT) * 72
    val pages = (project.placedItems.maxOfOrNull { it.pageIndex + 1 } ?: 1)
    val source = project.sourceUri?.let { decodeFullImage(activity.contentResolver, it) }
    val document = PdfDocument()
    repeat(pages) { pageIndex ->
        val page = document.startPage(PdfDocument.PageInfo.Builder(widthPt.toInt(), heightPt.toInt(), pageIndex + 1).create())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; style = Paint.Style.STROKE }
        page.canvas.drawRect(DEFAULT_MARGIN * 72, DEFAULT_MARGIN * 72, widthPt - DEFAULT_MARGIN * 72, heightPt - DEFAULT_MARGIN * 72, paint)
        project.placedItems.filter { it.pageIndex == pageIndex }.forEach { item ->
            val crop = project.classes.flatMap { it.crops }.firstOrNull { it.id == item.cropId }
            paint.style = Paint.Style.FILL
            if (source != null && crop != null) {
                val sourceRect = Rect(
                    crop.sourceRect.left.toInt().coerceIn(0, source.width),
                    crop.sourceRect.top.toInt().coerceIn(0, source.height),
                    crop.sourceRect.right.toInt().coerceIn(0, source.width),
                    crop.sourceRect.bottom.toInt().coerceIn(0, source.height)
                )
                if (sourceRect.width() > 0 && sourceRect.height() > 0) {
                    page.canvas.drawBitmap(
                        source,
                        sourceRect,
                        RectF(
                            item.xIn * 72,
                            item.yIn * 72,
                            (item.xIn + item.widthIn) * 72,
                            (item.yIn + item.heightIn) * 72
                        ),
                        paint
                    )
                }
            }
        }
        document.finishPage(page)
    }
    val output = ByteArrayOutputStream()
    document.writeTo(output)
    document.close()
    return output.toByteArray()
}

private fun exportDocx(activity: Activity, project: Project): ByteArray {
    val pageCount = project.placedItems.maxOfOrNull { it.pageIndex + 1 } ?: 1
    val source = project.sourceUri?.let { decodeFullImage(activity.contentResolver, it) }
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        fun entry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray())
            zip.closeEntry()
        }
        entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
        entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
        val relationships = StringBuilder("""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        val mediaEntries = mutableListOf<Pair<String, ByteArray>>()
        val body = buildString {
            repeat(pageCount) { page ->
                project.placedItems.filter { it.pageIndex == page }.forEach { placed ->
                    val crop = project.classes.flatMap { it.crops }.firstOrNull { it.id == placed.cropId }
                    if (source != null && crop != null) {
                        val image = cropBitmap(source, crop.sourceRect)
                        if (image != null) {
                            val name = "image${mediaEntries.size + 1}.png"
                            val relationshipId = "rId${mediaEntries.size + 1}"
                            val bytes = ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                            mediaEntries += name to bytes
                            relationships.append("<Relationship Id=\"$relationshipId\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/$name\"/>")
                            append(docxImageParagraph(relationshipId, placed.xIn, placed.yIn, placed.widthIn, placed.heightIn))
                        }
                    }
                }
                if (page < pageCount - 1) append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
            }
        }
        relationships.append("</Relationships>")
        entry("word/_rels/document.xml.rels", relationships.toString())
        mediaEntries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry("word/media/$name"))
            zip.write(bytes)
            zip.closeEntry()
        }
        entry("word/document.xml", """<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="360" w:right="360" w:bottom="360" w:left="360"/></w:sectPr></w:body></w:document>""")
    }

    return output.toByteArray()
}

private fun cropBitmap(source: Bitmap, rect: RectF): Bitmap? {
    val left = rect.left.toInt().coerceIn(0, source.width)
    val top = rect.top.toInt().coerceIn(0, source.height)
    val right = rect.right.toInt().coerceIn(0, source.width)
    val bottom = rect.bottom.toInt().coerceIn(0, source.height)
    return if (right > left && bottom > top) Bitmap.createBitmap(source, left, top, right - left, bottom - top) else null
}

private fun decodeFullImage(resolver: ContentResolver, uri: Uri): Bitmap? =
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }

private fun docxImageParagraph(relationshipId: String, xIn: Float, yIn: Float, widthIn: Float, heightIn: Float): String {
    val xEmu = (xIn * 914400).toInt()
    val yEmu = (yIn * 914400).toInt()
    val widthEmu = (widthIn * 914400).toInt()
    val heightEmu = (heightIn * 914400).toInt()
    return """<w:p><w:r><w:drawing><wp:anchor xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" behindDoc="0" layoutInCell="1" allowOverlap="1"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="page"><wp:posOffset>$xEmu</wp:posOffset></wp:positionH><wp:positionV relativeFrom="page"><wp:posOffset>$yEmu</wp:posOffset></wp:positionV><wp:extent cx="$widthEmu" cy="$heightEmu"/><wp:wrapNone/><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:blipFill><a:blip xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" r:embed="$relationshipId"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$widthEmu" cy="$heightEmu"/></a:xfrm><a:prstGeom prst="rect"/></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r></w:p>"""
}
