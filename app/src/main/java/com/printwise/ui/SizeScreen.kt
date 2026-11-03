package com.printwise.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.printwise.ui.theme.printwise
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.printwise.PrintwiseViewModel
import com.printwise.model.LengthUnit
import com.printwise.model.MIN_PIECE_IN
import com.printwise.model.PrintPiece
import com.printwise.model.PrintPlan
import com.printwise.ui.theme.Motion
import kotlin.math.roundToInt

private val MEDIA_WIDTH = 64.dp
private val CONTENT_GAP = 10.dp
private val LABEL_WIDTH = 52.dp
private val VALUE_WIDTH = 84.dp
/** 44dp: the low end of the 44–48dp touch range, so two control rows stay compact. */
private val CONTROL_SIZE = 44.dp
/** Label + minus + value + plus: the narrowest the control rows can be without squeezing. */
private val CONTROLS_WIDTH = LABEL_WIDTH + CONTROL_SIZE + 2.dp + VALUE_WIDTH + 2.dp + CONTROL_SIZE
private val MENU_SIZE = 44.dp

@Composable
fun SizeScreen(vm: PrintwiseViewModel, onAddPiece: () -> Unit, onNext: () -> Unit) {
    val layout = vm.layout
    var renaming by remember { mutableStateOf<PrintPiece?>(null) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "units") { UnitRow(vm) }
            itemsIndexed(vm.pieces, key = { _, piece -> piece.id }) { index, piece ->
                PieceSizeCard(
                    vm = vm,
                    number = index + 1,
                    piece = piece,
                    tooBig = piece.id in layout.tooBig,
                    onRename = { renaming = piece },
                    modifier = Modifier.animateItem()
                )
            }
            item(key = "add") {
                OutlinedButton(
                    onClick = onAddPiece,
                    modifier = Modifier.animateItem().widthIn(max = 640.dp).fillMaxWidth().padding(top = 4.dp).height(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add another piece")
                }
            }
        }
        BottomActionBar {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    RollingText(
                        if (vm.totalCopies == 1) "1 piece" else "${vm.totalCopies} pieces",
                        style = MaterialTheme.typography.titleMedium
                    )
                    RollingText(
                        "fits on ${if (layout.pageCount == 1) "1 page" else "${layout.pageCount} pages"} of ${vm.paper.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onNext,
                    enabled = layout.placements.isNotEmpty(),
                    modifier = Modifier.height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Preview pages", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                }
            }
        }
    }
    renaming?.let { piece ->
        RenameDialog(
            current = vm.nameOf(piece),
            onSave = { vm.rename(piece.id, it); renaming = null },
            onDismiss = { renaming = null }
        )
    }
}

/** Which unit every size on this screen is shown and typed in. Inches unless the user switches. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitRow(vm: PrintwiseViewModel) {
    Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (vm.unit == LengthUnit.IN) "Sizes in inches" else "Sizes in centimetres",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        SingleChoiceSegmentedButtonRow {
            LengthUnit.entries.forEachIndexed { index, option ->
                val selected = vm.unit == option
                SegmentedButton(
                    selected = selected,
                    onClick = { vm.unit = option },
                    shape = SegmentedButtonDefaults.itemShape(index, LengthUnit.entries.size),
                    icon = {}
                ) {
                    // Selection shows as fill plus weight, not colour alone.
                    Text(
                        if (option == LengthUnit.IN) "Inches" else "CM",
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * One piece, kept to about 128dp tall so four or more fit on a phone screen:
 * - the artwork fills a strip down the left edge (no tile inside the card, no empty space under it);
 * - the name and printed size share one line;
 * - Height and Copies rows sit under it in fixed columns, so every card lines up.
 * The overflow menu floats in the top-right corner when the card is wide enough to keep it clear of the
 * controls, and otherwise moves into the name row.
 */
@Composable
private fun PieceSizeCard(
    vm: PrintwiseViewModel,
    number: Int,
    piece: PrintPiece,
    tooBig: Boolean,
    onRename: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier.widthIn(max = 640.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp
    ) {
        BoxWithConstraints {
            val floatingMenu = maxWidth >= MEDIA_WIDTH + CONTENT_GAP + CONTROLS_WIDTH + MENU_SIZE
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = MEDIA_WIDTH + CONTENT_GAP, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                    if (floatingMenu) {
                        PieceTitle(vm, piece, Modifier.padding(end = MENU_SIZE - 8.dp).height(20.dp))
                        Spacer(Modifier.height(4.dp))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PieceTitle(vm, piece, Modifier.weight(1f))
                            PieceMenu(vm, piece, onRename)
                        }
                    }
                    SizeControls(vm, piece, number)
                    AnimatedVisibility(
                        visible = tooBig,
                        enter = expandVertically(tween(Motion.BASE_MS, easing = Motion.EaseOut)) + fadeIn(tween(Motion.BASE_MS)),
                        exit = shrinkVertically(tween(Motion.FAST_MS, easing = Motion.EaseOut)) + fadeOut(tween(Motion.FAST_MS))
                    ) {
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(end = 6.dp).size(18.dp)
                            )
                            Text(
                                "Too big for ${vm.paper.label} paper. Make it smaller.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    BlurryNote(visible = !tooBig && PrintPlan.mayLookBlurry(piece, vm.heightOf(piece)))
                }
                // Measured after the content, so the strip is exactly as tall as the card.
                Box(Modifier.matchParentSize()) {
                    PieceMedia(vm.thumbnail(piece), number, Modifier.width(MEDIA_WIDTH).fillMaxHeight())
                }
                if (floatingMenu) {
                    Box(Modifier.align(Alignment.TopEnd)) { PieceMenu(vm, piece, onRename) }
                }
            }
        }
    }
}

/**
 * Plain-language heads-up when the picture has too few pixels for the chosen size. Shown before printing so
 * nobody wastes photo paper finding out.
 */
@Composable
internal fun BlurryNote(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(tween(Motion.BASE_MS, easing = Motion.EaseOut)) + fadeIn(tween(Motion.BASE_MS)),
        exit = shrinkVertically(tween(Motion.FAST_MS, easing = Motion.EaseOut)) + fadeOut(tween(Motion.FAST_MS))
    ) {
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.BlurOn,
                contentDescription = null,
                tint = MaterialTheme.printwise.warning,
                modifier = Modifier.padding(end = 6.dp).size(18.dp)
            )
            Text(
                "May print blurry at this size. Smaller looks sharper.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The piece's artwork on white paper, filling the card's left edge, with its number badge. */
@Composable
private fun PieceMedia(image: ImageBitmap?, number: Int, modifier: Modifier = Modifier) {
    val divider = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier
            .background(MaterialTheme.printwise.paper)
            .drawBehind { drawLine(divider, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx()) }
    ) {
        if (image != null) {
            Image(
                image,
                contentDescription = "Piece $number",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 22.dp, bottom = 8.dp)
            )
        }
        Box(
            Modifier
                .padding(6.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** Name, then the current printed size as a quieter note on the same line. */
@Composable
private fun PieceTitle(vm: PrintwiseViewModel, piece: PrintPiece, modifier: Modifier = Modifier) {
    val unit = vm.unit
    val height = vm.heightOf(piece)
    val width = PrintPlan.widthOf(piece, height)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            vm.nameOf(piece),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${unit.formatNumber(width)} × ${unit.format(height)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** Rename, duplicate and delete: less frequent actions, kept out of the way. */
@Composable
private fun PieceMenu(vm: PrintwiseViewModel, piece: PrintPiece, onRename: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val name = vm.nameOf(piece)
    Box {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides MENU_SIZE) {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More for $name")
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = { menuOpen = false; onRename() }
            )
            DropdownMenuItem(
                text = { Text("Duplicate") },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                onClick = { menuOpen = false; vm.duplicate(piece.id) }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menuOpen = false; vm.remove(piece.id) }
            )
        }
    }
}

/** Height and Copies, each as label · minus · value · plus, in fixed columns so every card lines up. */
@Composable
private fun SizeControls(vm: PrintwiseViewModel, piece: PrintPiece, number: Int) {
    val unit = vm.unit
    val height = vm.heightOf(piece)
    val shown = unit.fromInches(height)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides CONTROL_SIZE) {
        ControlRow("Height") {
            Stepper(
                value = unit.formatNumber(height),
                onMinus = { vm.setHeight(piece.id, unit.toInches(snap(shown - unit.step, unit.step))) },
                onPlus = { vm.setHeight(piece.id, unit.toInches(snap(shown + unit.step, unit.step))) },
                onValueCommit = { text ->
                    text.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }
                        ?.let { vm.setHeight(piece.id, unit.toInches(it)) }
                },
                minusEnabled = height > MIN_PIECE_IN + 1e-3f,
                plusEnabled = height < vm.maxHeightIn - 1e-3f,
                valueWidth = VALUE_WIDTH,
                contentDescription = "height of piece $number",
                suffix = unit.label,
                decimal = true
            )
        }
        ControlRow("Copies") {
            Stepper(
                value = "${piece.copies}",
                onMinus = { vm.setCopies(piece.id, piece.copies - 1) },
                onPlus = { vm.setCopies(piece.id, piece.copies + 1) },
                onValueCommit = { text -> text.toIntOrNull()?.let { vm.setCopies(piece.id, it) } },
                minusEnabled = piece.copies > 1,
                plusEnabled = piece.copies < 99,
                valueWidth = VALUE_WIDTH,
                contentDescription = "copies of piece $number"
            )
        }
    }
}

@Composable
private fun ControlRow(label: String, control: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(LABEL_WIDTH)
        )
        control()
    }
}

@Composable
private fun RenameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename piece") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                singleLine = true,
                label = { Text("Name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

internal fun snap(value: Float, step: Float): Float = (value / step).roundToInt() * step
