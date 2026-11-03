package com.printwise.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CropLandscape
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.printwise.PrintwiseViewModel
import com.printwise.layout.PageLayout
import com.printwise.layout.Placement
import com.printwise.model.MIN_PIECE_IN
import com.printwise.model.Orientation
import com.printwise.model.PaperSize
import com.printwise.model.PrintPiece
import com.printwise.model.PrintPlan
import com.printwise.ui.theme.Motion
import com.printwise.ui.theme.printwise
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun PrintScreen(
    vm: PrintwiseViewModel,
    snackbar: SnackbarHostState,
    onPrint: () -> Unit,
    onShare: () -> Unit,
    onSavePdf: () -> Unit,
    onSaveDocx: () -> Unit
) {
    val layout = vm.layout
    val busy = vm.exportStatus is com.printwise.ExportStatus.Working
    val scope = rememberCoroutineScope()
    var selectedPageState by remember { mutableIntStateOf(0) }
    val selectedPage = selectedPageState.coerceIn(0, max(0, layout.pageCount - 1))
    var selectedPlacementId by remember { mutableStateOf<String?>(null) }
    // null: closed · true: opened by Print (continues to printing) · false: opened from the tips link.
    var guideBeforePrint by remember { mutableStateOf<Boolean?>(null) }
    val startPrint = { if (vm.showPrintGuide) guideBeforePrint = true else onPrint() }
    val selectedPlacement = layout.placements.firstOrNull { it.id == selectedPlacementId }
    val numbers = vm.pieces.withIndex().associate { (index, piece) -> piece.id to index + 1 }

    fun addPiece(piece: PrintPiece) {
        if (vm.addToPage(piece.id, selectedPage)) return
        scope.launch {
            val result = snackbar.showSnackbar("No room on page ${selectedPage + 1}", actionLabel = "Add page", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                val page = vm.addPage()
                selectedPageState = page
                vm.addToPage(piece.id, page)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "options") { PaperOptions(vm, layout) }
            if (layout.tooBig.isNotEmpty()) {
                item(key = "warning") {
                    Card(
                        Modifier.animateItem().widthIn(max = 640.dp).fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (layout.tooBig.size == 1) {
                                    "1 piece is bigger than the paper and is left out. Go back and make it smaller."
                                } else {
                                    "${layout.tooBig.size} pieces are bigger than the paper and are left out. Go back and make them smaller."
                                },
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            items(List(layout.pageCount) { it }, key = { "page-$it" }) { page ->
                PagePreview(
                    vm = vm,
                    layout = layout,
                    page = page,
                    selected = page == selectedPage,
                    selectedPlacementId = selectedPlacementId,
                    onSelectPage = {
                        selectedPageState = page
                        selectedPlacementId = null
                    },
                    onTapPlacement = { id ->
                        selectedPageState = page
                        selectedPlacementId = if (selectedPlacementId == id) null else id
                    },
                    onRemovePage = if (layout.pageCount > 1 && layout.placements.none { it.page == page }) {
                        { vm.removePage(page) }
                    } else null,
                    modifier = Modifier.animateItem().then(if (page == 0) Modifier.tourTarget(TourTarget.PAGE) else Modifier)
                )
            }
            item(key = "add-page") {
                AddPageTile(
                    onClick = {
                        selectedPageState = vm.addPage()
                        selectedPlacementId = null
                    },
                    modifier = Modifier.animateItem()
                )
            }
            item(key = "tip") {
                Row(
                    Modifier.animateItem().widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.printwise.info, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "When printing, choose 100% or \"Actual size\" (not \"Fit to page\") so every piece comes out exactly the size you set.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { guideBeforePrint = false }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text("How to print sharp on photo paper")
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar)
        AnimatedVisibility(
            visible = selectedPlacement != null,
            enter = expandVertically(tween(Motion.BASE_MS, easing = Motion.EaseOut)) + fadeIn(tween(Motion.BASE_MS)),
            exit = shrinkVertically(tween(Motion.FAST_MS, easing = Motion.EaseOut)) + fadeOut(tween(Motion.FAST_MS))
        ) {
            // Keeps showing the last placement while the panel animates away (a plain holder, not state).
            val last = remember { LastValue<Placement>() }
            if (selectedPlacement != null) last.value = selectedPlacement
            last.value?.let { placed ->
                PlacementPanel(
                    vm = vm,
                    placement = placed,
                    number = numbers[placed.pieceId] ?: 0,
                    onResize = { heightIn ->
                        if (!vm.resizePlacement(placed.id, heightIn)) {
                            scope.launch { snackbar.showSnackbar("Not enough room on this page at that size") }
                        }
                    },
                    onRemove = {
                        vm.removePlacement(placed.id)
                        selectedPlacementId = null
                    },
                    onDone = { selectedPlacementId = null }
                )
            }
        }
        AddTray(vm, selectedPage, numbers, onAdd = ::addPiece)
        BottomActionBar {
            Row(
                Modifier.tourTarget(TourTarget.ACTIONS),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { menuOpen = true },
                        enabled = !busy,
                        modifier = Modifier.height(56.dp),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("PDF (best for printing)") },
                            leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) },
                            onClick = { menuOpen = false; onSavePdf() }
                        )
                        DropdownMenuItem(
                            text = { Text("Word document") },
                            leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                            onClick = { menuOpen = false; onSaveDocx() }
                        )
                    }
                }
                FilledTonalButton(onClick = onShare, enabled = !busy, modifier = Modifier.height(56.dp), shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share")
                }
                Button(
                    onClick = startPrint,
                    enabled = !busy && layout.placements.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Rounded.Print, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Print", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    guideBeforePrint?.let { beforePrint ->
        PrintGuideSheet(
            paperLabel = vm.paper.label,
            blurryCount = vm.blurryPieceCount,
            beforePrint = beforePrint,
            showBeforePrint = vm.showPrintGuide,
            onShowBeforePrintChange = vm::changeShowPrintGuide,
            onContinue = {
                guideBeforePrint = null
                if (beforePrint) onPrint()
            },
            onDismiss = { guideBeforePrint = null }
        )
    }
}

private class LastValue<T> {
    var value: T? = null
}

/** Page count plus two compact pickers, so the pages themselves are what fills the screen. */
@Composable
private fun PaperOptions(vm: PrintwiseViewModel, layout: PageLayout) {
    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RollingText(
            "${if (layout.pageCount == 1) "1 page" else "${layout.pageCount} pages"} · ${layout.placements.size} pieces",
            style = MaterialTheme.typography.titleLarge
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.tourTarget(TourTarget.PAPER)) {
            OptionChip(
                icon = Icons.Rounded.Description,
                label = vm.paper.label,
                options = PaperSize.entries,
                selected = vm.paper,
                optionLabel = { it.label },
                description = "Paper size",
                onSelect = vm::changePaper
            )
            OptionChip(
                icon = if (layout.landscape) Icons.Rounded.CropLandscape else Icons.Rounded.CropPortrait,
                label = vm.orientation.label,
                options = Orientation.entries,
                selected = vm.orientation,
                optionLabel = { it.label },
                description = "Page direction",
                onSelect = vm::changeOrientation
            )
        }
    }
}

@Composable
private fun <T> OptionChip(
    icon: ImageVector,
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    description: String,
    onSelect: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(label) },
            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.semantics { contentDescription = "$description: $label" }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    trailingIcon = {
                        if (option == selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                    },
                    onClick = { open = false; onSelect(option) }
                )
            }
        }
    }
}

/** What a page looks like, used as the crossfade key so changes blend instead of jumping. */
private data class PageContent(val widthIn: Float, val heightIn: Float, val placements: List<Placement>)

/**
 * One page. Tapping empty paper makes it the page new pieces go to; tapping a piece selects that copy for
 * resizing. The selection outline is drawn here only and never reaches the exported file.
 */
@Composable
private fun PagePreview(
    vm: PrintwiseViewModel,
    layout: PageLayout,
    page: Int,
    selected: Boolean,
    selectedPlacementId: String?,
    onSelectPage: () -> Unit,
    onTapPlacement: (String) -> Unit,
    onRemovePage: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val pieces = vm.pieces.associateBy { it.id }
    val content = PageContent(layout.pageWidthIn, layout.pageHeightIn, layout.placements.filter { it.page == page })
    val primary = MaterialTheme.colorScheme.primary
    Column(modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Crossfade(targetState = content, animationSpec = tween(Motion.BASE_MS, easing = Motion.EaseOut), label = "page") { shown ->
            // The page is always white paper: brand colours never reach the preview or the export.
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.printwise.paper,
                border = if (selected) BorderStroke(2.dp, primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().aspectRatio(shown.widthIn / shown.heightIn)
            ) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = "Page ${page + 1} with ${shown.placements.size} pieces" }
                        .pointerInput(shown) {
                            detectTapGestures { tap ->
                                val scale = size.width / shown.widthIn
                                val hit = shown.placements.lastOrNull { p ->
                                    tap.x in p.xIn * scale..(p.xIn + p.widthIn) * scale && tap.y in p.yIn * scale..(p.yIn + p.heightIn) * scale
                                }
                                if (hit != null) onTapPlacement(hit.id) else onSelectPage()
                            }
                        }
                ) {
                    val scale = size.width / shown.widthIn
                    shown.placements.forEach { placed ->
                        val thumb = pieces[placed.pieceId]?.let { vm.thumbnail(it) } ?: return@forEach
                        drawImage(
                            thumb,
                            dstOffset = IntOffset((placed.xIn * scale).roundToInt(), (placed.yIn * scale).roundToInt()),
                            dstSize = IntSize((placed.widthIn * scale).roundToInt().coerceAtLeast(1), (placed.heightIn * scale).roundToInt().coerceAtLeast(1)),
                            filterQuality = FilterQuality.Medium
                        )
                        if (placed.id == selectedPlacementId) {
                            val pad = 2.dp.toPx()
                            drawRoundRect(
                                primary,
                                Offset(placed.xIn * scale - pad, placed.yIn * scale - pad),
                                Size(placed.widthIn * scale + 2 * pad, placed.heightIn * scale + 2 * pad),
                                CornerRadius(3.dp.toPx()),
                                style = Stroke(2.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (selected) "Page ${page + 1} of ${layout.pageCount} · adding here" else "Page ${page + 1} of ${layout.pageCount}",
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onRemovePage != null) {
                TextButton(onClick = onRemovePage) { Text("Remove page") }
            }
        }
    }
}

@Composable
private fun AddPageTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    Box(
        modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .height(72.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    outline,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Add page", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Every piece at its set size; a tap drops one more copy into free space on the selected page. */
@Composable
private fun AddTray(vm: PrintwiseViewModel, page: Int, numbers: Map<String, Int>, onAdd: (PrintPiece) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.tourTarget(TourTarget.TRAY)) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "Tap a piece to add it to page ${page + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(vm.pieces, key = { _, piece -> piece.id }) { _, piece ->
                    val fits = vm.fitsOnPage(piece)
                    val height = vm.heightOf(piece)
                    val number = numbers[piece.id] ?: 0
                    Column(
                        Modifier
                            .alpha(if (fits) 1f else 0.4f)
                            .clickable(enabled = fits, role = Role.Button, onClickLabel = "Add to page ${page + 1}") { onAdd(piece) }
                            .semantics { if (!fits) contentDescription = "Piece $number is too big for this paper" },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PieceTile(vm.thumbnail(piece), 56.dp, number = number)
                        Text(
                            "${vm.unit.formatNumber(PrintPlan.widthOf(piece, height))} × ${vm.unit.format(height)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Resize or remove the one placed copy that's selected; other copies of the piece keep their size. */
@Composable
private fun PlacementPanel(
    vm: PrintwiseViewModel,
    placement: Placement,
    number: Int,
    onResize: (Float) -> Unit,
    onRemove: () -> Unit,
    onDone: () -> Unit
) {
    val unit = vm.unit
    val piece = vm.piece(placement.pieceId)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PieceTile(piece?.let { vm.thumbnail(it) }, 48.dp, number = number)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(piece?.let(vm::nameOf) ?: "Piece $number", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text("This copy only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val shown = unit.fromInches(placement.heightIn)
                Stepper(
                    value = unit.formatNumber(placement.heightIn),
                    onMinus = { onResize(unit.toInches(snap(shown - unit.step, unit.step))) },
                    onPlus = { onResize(unit.toInches(snap(shown + unit.step, unit.step))) },
                    onValueCommit = { text ->
                        text.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }?.let { onResize(unit.toInches(it)) }
                    },
                    minusEnabled = placement.heightIn > MIN_PIECE_IN + 1e-3f,
                    valueWidth = 84.dp,
                    contentDescription = "height of this copy of piece $number",
                    suffix = unit.label,
                    decimal = true
                )
            }
            if (piece != null) {
                BlurryNote(
                    visible = PrintPlan.mayLookBlurry(piece, placement.heightIn),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onRemove) { Text("Remove from page", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDone) { Text("Done") }
            }
        }
    }
}
