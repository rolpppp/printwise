package com.printwise.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.printwise.PrintwiseViewModel
import com.printwise.model.LengthUnit
import com.printwise.model.MIN_PIECE_IN
import com.printwise.model.PrintPiece
import com.printwise.model.PrintPlan
import kotlin.math.roundToInt

@Composable
fun SizeScreen(vm: PrintwiseViewModel, onNext: () -> Unit) {
    val layout = vm.layout
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "overall") { OverallSizeCard(vm) }
            itemsIndexed(vm.pieces, key = { _, piece -> piece.id }) { index, piece ->
                PieceSizeCard(vm, index, piece, tooBig = piece.id in layout.tooBig)
            }
        }
        BottomActionBar {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (vm.totalCopies == 1) "1 piece" else "${vm.totalCopies} pieces",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "fits on ${if (layout.pageCount == 1) "1 page" else "${layout.pageCount} pages"} of ${vm.paper.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onNext,
                    enabled = layout.placements.isNotEmpty(),
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Preview pages", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverallSizeCard(vm: PrintwiseViewModel) {
    val unit = vm.unit
    Card(
        Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("How big?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                SingleChoiceSegmentedButtonRow {
                    LengthUnit.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = unit == option,
                            onClick = { vm.unit = option },
                            shape = SegmentedButtonDefaults.itemShape(index, LengthUnit.entries.size)
                        ) { Text(option.label) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Tallest piece", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(unit.format(vm.tallestIn), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Slider(
                value = unit.fromInches(vm.tallestIn),
                onValueChange = { vm.setTallest(unit.toInches(snap(it, unit.step))) },
                valueRange = unit.fromInches(0.5f)..unit.fromInches(vm.maxHeightIn)
            )
            Text(
                "Every other piece keeps its size relative to this one, just like on your sheet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Copies of each", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Change one piece below to set it alone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                val same = vm.pieces.map { it.copies }.distinct().singleOrNull()
                val shown = same ?: vm.pieces.minOfOrNull { it.copies } ?: 1
                Stepper(
                    value = if (same != null) "×$same" else "mixed",
                    onMinus = { vm.setAllCopies(shown - 1) },
                    onPlus = { vm.setAllCopies(shown + 1) },
                    minusEnabled = shown > 1,
                    valueWidth = 64.dp,
                    contentDescription = "copies of every piece"
                )
            }
        }
    }
}

@Composable
private fun PieceSizeCard(vm: PrintwiseViewModel, index: Int, piece: PrintPiece, tooBig: Boolean) {
    val unit = vm.unit
    val height = vm.heightOf(piece)
    val width = PrintPlan.widthOf(piece, height)
    Card(
        Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PieceTile(vm.thumbnail(piece), 72.dp, number = index + 1)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("Piece ${index + 1}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${unit.format(width)} wide × ${unit.format(height)} tall",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (tooBig) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 6.dp))
                    Text(
                        "Too big for ${vm.paper.label} paper. Make it smaller.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Height", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val shown = unit.fromInches(height)
                    Stepper(
                        value = unit.format(height),
                        onMinus = { vm.setHeight(piece.id, unit.toInches(snap(shown - unit.step, unit.step))) },
                        onPlus = { vm.setHeight(piece.id, unit.toInches(snap(shown + unit.step, unit.step))) },
                        minusEnabled = height > MIN_PIECE_IN + 1e-3f,
                        plusEnabled = height < vm.maxHeightIn - 1e-3f,
                        valueWidth = 76.dp,
                        contentDescription = "height"
                    )
                }
                Box {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Copies", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Stepper(
                            value = "×${piece.copies}",
                            onMinus = { vm.setCopies(piece.id, piece.copies - 1) },
                            onPlus = { vm.setCopies(piece.id, piece.copies + 1) },
                            minusEnabled = piece.copies > 1,
                            plusEnabled = piece.copies < 99,
                            valueWidth = 48.dp,
                            contentDescription = "copies"
                        )
                    }
                }
            }
        }
    }
}

private fun snap(value: Float, step: Float): Float = (value / step).roundToInt() * step
