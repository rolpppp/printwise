package com.printwise.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.printwise.PrintwiseViewModel
import com.printwise.layout.PageLayout
import com.printwise.model.Orientation
import com.printwise.model.PaperSize
import kotlin.math.roundToInt

@Composable
fun PrintScreen(
    vm: PrintwiseViewModel,
    onPrint: () -> Unit,
    onShare: () -> Unit,
    onSavePdf: () -> Unit,
    onSaveDocx: () -> Unit
) {
    val layout = vm.layout
    val busy = vm.exportStatus is com.printwise.ExportStatus.Working
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
                        Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "${layout.tooBig.size} piece(s) are bigger than the paper and are left out. Go back and make them smaller.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            items(List(layout.pageCount) { it }, key = { "page-$it" }) { page ->
                PagePreview(vm, layout, page)
            }
            item(key = "tip") {
                Row(
                    Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "When printing, choose 100% or \"Actual size\" (not \"Fit to page\") so every piece comes out exactly the size you set.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        BottomActionBar {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { menuOpen = true }, enabled = !busy, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(18.dp)) {
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
                FilledTonalButton(onClick = onShare, enabled = !busy, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share")
                }
                Button(
                    onClick = onPrint,
                    enabled = !busy && layout.placements.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Rounded.Print, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Print", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaperOptions(vm: PrintwiseViewModel, layout: PageLayout) {
    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "${if (layout.pageCount == 1) "1 page" else "${layout.pageCount} pages"} · ${layout.placements.size} pieces",
            style = MaterialTheme.typography.headlineSmall
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Paper", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PaperSize.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = vm.paper == option,
                        onClick = { vm.paper = option },
                        shape = SegmentedButtonDefaults.itemShape(index, PaperSize.entries.size)
                    ) { Text(option.label) }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Page direction", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Orientation.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = vm.orientation == option,
                        onClick = { vm.orientation = option },
                        shape = SegmentedButtonDefaults.itemShape(index, Orientation.entries.size),
                        icon = {}
                    ) { Text(option.label, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun PagePreview(vm: PrintwiseViewModel, layout: PageLayout, page: Int) {
    val pieces = vm.pieces.associateBy { it.id }
    val onPage = layout.placements.filter { it.page == page }
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            shadowElevation = 6.dp,
            color = Color.White,
            modifier = Modifier.fillMaxWidth().aspectRatio(layout.pageWidthIn / layout.pageHeightIn)
        ) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Page ${page + 1} with ${onPage.size} pieces" }) {
                val scale = size.width / layout.pageWidthIn
                onPage.forEach { placed ->
                    val thumb = pieces[placed.pieceId]?.let { vm.thumbnail(it) } ?: return@forEach
                    drawImage(
                        thumb,
                        dstOffset = IntOffset((placed.xIn * scale).roundToInt(), (placed.yIn * scale).roundToInt()),
                        dstSize = IntSize((placed.widthIn * scale).roundToInt().coerceAtLeast(1), (placed.heightIn * scale).roundToInt().coerceAtLeast(1)),
                        filterQuality = FilterQuality.Medium
                    )
                }
            }
        }
        Text(
            "Page ${page + 1} of ${layout.pageCount}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
