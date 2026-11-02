package com.printwise.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.printwise.Detection
import com.printwise.PrintwiseViewModel
import com.printwise.TapResult
import com.printwise.model.Box
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun SelectScreen(vm: PrintwiseViewModel, onNext: () -> Unit) {
    val image = vm.sourceImage ?: return
    val colors = MaterialTheme.colorScheme
    val viewport = remember(image) { Viewport(image.width.toFloat(), image.height.toFloat()) }
    val detection = vm.detection
    val allDetectedSelected = vm.detected.isNotEmpty() &&
        vm.detected.all { d -> vm.pieces.any { it.detectedId == d.id } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        detection is Detection.Running -> "Finding pieces…"
                        vm.pieces.isEmpty() -> "Nothing selected yet"
                        vm.pieces.size == 1 -> "1 piece selected"
                        else -> "${vm.pieces.size} pieces selected"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Tap a piece to add or edit it · Drag to draw a box",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            IconButton(onClick = vm::undo, enabled = vm.canUndo) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo")
            }
            if (allDetectedSelected || (vm.detected.isEmpty() && vm.pieces.isNotEmpty())) {
                TextButton(onClick = vm::clearAll) { Text("Clear") }
            } else if (vm.detected.isNotEmpty()) {
                TextButton(onClick = vm::selectAll) { Text("Select all") }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .background(colors.surfaceContainerHigh)
        ) {
            SheetEditor(vm, image, viewport)
            DetectionBanner(detection, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
            if (viewport.zoom > 1.01f) {
                FilledTonalButton(
                    onClick = viewport::reset,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                ) {
                    Icon(Icons.Rounded.ZoomOutMap, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Fit")
                }
            }
        }

        PieceTray(vm)

        BottomActionBar {
            Button(
                onClick = onNext,
                enabled = vm.pieces.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Next: choose sizes", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun DetectionBanner(detection: Detection, modifier: Modifier) {
    var showResult by remember { mutableStateOf(false) }
    LaunchedEffect(detection) {
        if (detection is Detection.Done || detection is Detection.Failed) {
            showResult = true
            delay(if (detection is Detection.Failed) 6000 else 3200)
            showResult = false
        }
    }
    val visible = detection is Detection.Running || showResult
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it }
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                when (detection) {
                    Detection.Running -> CircularProgressIndicator(
                        Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.inversePrimary
                    )
                    else -> Icon(
                        Icons.Rounded.AutoAwesome, contentDescription = null,
                        tint = MaterialTheme.colorScheme.inversePrimary, modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    when (detection) {
                        Detection.Running, Detection.Idle -> "Finding pieces on your sheet…"
                        is Detection.Done -> when (detection.found) {
                            0 -> "No pieces found. Drag on the sheet to draw one."
                            1 -> "Found 1 piece and selected it"
                            else -> "Found ${detection.found} pieces · all selected"
                        }
                        is Detection.Failed -> detection.message
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun PieceTray(vm: PrintwiseViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        if (vm.pieces.isEmpty()) {
            Text(
                "Selected pieces will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 30.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(vm.pieces, key = { _, piece -> piece.id }) { index, piece ->
                    PieceTile(
                        image = vm.thumbnail(piece),
                        size = 64.dp,
                        number = index + 1,
                        highlighted = piece.id == vm.activeId,
                        modifier = Modifier
                            .animateItem()
                            .clickable { vm.activate(if (vm.activeId == piece.id) null else piece.id) }
                    )
                }
            }
        }
    }
}

// ---- Editor canvas ----------------------------------------------------------------------------------------

/** Maps between image pixels and screen pixels, with pinch-zoom on top of "fit to view". */
@Stable
private class Viewport(val imageWidth: Float, val imageHeight: Float) {
    var size by mutableStateOf(IntSize.Zero)
    var zoom by mutableFloatStateOf(1f)
        private set
    var pan by mutableStateOf(Offset.Zero)
        private set

    private val fitScale: Float
        get() = if (size.width == 0 || size.height == 0) 1f
        else min(size.width / imageWidth, size.height / imageHeight) * 0.94f
    private val fitOffset: Offset
        get() = Offset((size.width - imageWidth * fitScale) / 2f, (size.height - imageHeight * fitScale) / 2f)

    val scale: Float get() = fitScale * zoom
    val origin: Offset get() = fitOffset + pan

    fun toImage(point: Offset) = Offset((point.x - origin.x) / scale, (point.y - origin.y) / scale)

    fun rect(box: Box): Rect {
        val o = origin
        val s = scale
        return Rect(o.x + box.left * s, o.y + box.top * s, o.x + box.right * s, o.y + box.bottom * s)
    }

    fun transform(centroid: Offset, panChange: Offset, zoomChange: Float) {
        val anchor = toImage(centroid)
        val newZoom = (zoom * zoomChange).coerceIn(1f, 8f)
        zoom = newZoom
        if (newZoom <= 1.001f) {
            pan = Offset.Zero
            return
        }
        val newScale = fitScale * newZoom
        val w = imageWidth * newScale
        val h = imageHeight * newScale
        // Keep the image under the fingers, but never let it leave the centre of the view entirely.
        val originX = (centroid.x + panChange.x - anchor.x * newScale).coerceIn(size.width / 2f - w, size.width / 2f)
        val originY = (centroid.y + panChange.y - anchor.y * newScale).coerceIn(size.height / 2f - h, size.height / 2f)
        pan = Offset(originX, originY) - fitOffset
    }

    fun reset() {
        zoom = 1f
        pan = Offset.Zero
    }
}

private sealed interface Hit {
    data class Remove(val id: String) : Hit
    data class Handle(val id: String, val handle: Int) : Hit
    data class Body(val id: String) : Hit
    data object Empty : Hit
}

private sealed interface DragMode {
    data class Resize(val id: String, val handle: Int, val original: Box) : DragMode
    data class Move(val id: String, val original: Box) : DragMode
    data object Draw : DragMode
    data object Ignore : DragMode
}

/** A box being dragged; [id] is null while drawing a brand-new one. */
private data class EditPreview(val id: String?, val box: Box)

// Handles 0..3 are corners (TL, TR, BR, BL); 4..7 are edge midpoints (T, R, B, L).
private fun handlePoints(r: Rect, withEdges: Boolean): List<Offset> {
    val corners = listOf(r.topLeft, r.topRight, r.bottomRight, r.bottomLeft)
    if (!withEdges) return corners
    return corners + listOf(
        Offset(r.center.x, r.top), Offset(r.right, r.center.y), Offset(r.center.x, r.bottom), Offset(r.left, r.center.y)
    )
}

private fun resized(original: Box, handle: Int, dx: Float, dy: Float, imageWidth: Float, imageHeight: Float): Box {
    var l = original.left
    var t = original.top
    var r = original.right
    var b = original.bottom
    if (handle == 0 || handle == 3 || handle == 7) l += dx
    if (handle == 1 || handle == 2 || handle == 5) r += dx
    if (handle == 0 || handle == 1 || handle == 4) t += dy
    if (handle == 2 || handle == 3 || handle == 6) b += dy
    return Box.spanning(l, t, r, b).clampTo(imageWidth, imageHeight)
}

@Composable
private fun SheetEditor(vm: PrintwiseViewModel, image: ImageBitmap, viewport: Viewport) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val handleHitPx = with(density) { 26.dp.toPx() }
    val edgeHandlesMinPx = with(density) { 72.dp.toPx() }
    val tapRadiusPx = with(density) { 14.dp.toPx() }
    val minDrawPx = with(density) { 16.dp.toPx() }
    val pillWidthPx = with(density) { 104.dp.toPx() }
    val pillHeightPx = with(density) { 36.dp.toPx() }
    val pillGapPx = with(density) { 22.dp.toPx() }
    var preview by remember { mutableStateOf<EditPreview?>(null) }
    val imageWidth = image.width.toFloat()
    val imageHeight = image.height.toFloat()

    fun removePill(r: Rect): Rect {
        val above = r.top - pillGapPx - pillHeightPx
        val top = if (above >= 4f) above else min(r.bottom + pillGapPx, viewport.size.height - pillHeightPx - 4f)
        val cx = r.center.x.coerceIn(pillWidthPx / 2f, viewport.size.width - pillWidthPx / 2f)
        return Rect(cx - pillWidthPx / 2f, top, cx + pillWidthPx / 2f, top + pillHeightPx)
    }

    fun hitTest(point: Offset): Hit {
        val active = vm.pieces.firstOrNull { it.id == vm.activeId }
        if (active != null) {
            val r = viewport.rect(active.box)
            if (removePill(r).inflate(6f).contains(point)) return Hit.Remove(active.id)
            val handles = handlePoints(r, r.width >= edgeHandlesMinPx && r.height >= edgeHandlesMinPx)
            val nearest = handles.withIndex().minByOrNull { (it.value - point).getDistance() }
            if (nearest != null && (nearest.value - point).getDistance() <= handleHitPx) return Hit.Handle(active.id, nearest.index)
            if (r.contains(point)) return Hit.Body(active.id)
        }
        val inside = vm.pieces.filter { viewport.rect(it.box).contains(point) }.minByOrNull { it.box.area }
        return inside?.let { Hit.Body(it.id) } ?: Hit.Empty
    }

    fun clampToImage(p: Offset) = Offset(p.x.coerceIn(0f, imageWidth), p.y.coerceIn(0f, imageHeight))

    fun startDrag(hit: Hit): DragMode = when (hit) {
        is Hit.Handle -> vm.pieces.firstOrNull { it.id == hit.id }?.let { DragMode.Resize(it.id, hit.handle, it.box) } ?: DragMode.Ignore
        // Dragging the active box moves it; dragging anywhere else draws a new box (even over a big group).
        is Hit.Body -> if (hit.id == vm.activeId) {
            vm.pieces.firstOrNull { it.id == hit.id }?.let { DragMode.Move(it.id, it.box) } ?: DragMode.Ignore
        } else DragMode.Draw
        is Hit.Remove -> DragMode.Ignore
        Hit.Empty -> DragMode.Draw
    }

    fun updateDrag(mode: DragMode, start: Offset, current: Offset) {
        val dx = current.x - start.x
        val dy = current.y - start.y
        preview = when (mode) {
            is DragMode.Resize -> EditPreview(mode.id, resized(mode.original, mode.handle, dx, dy, imageWidth, imageHeight))
            is DragMode.Move -> {
                val o = mode.original
                EditPreview(mode.id, o.offset(dx.coerceIn(-o.left, imageWidth - o.right), dy.coerceIn(-o.top, imageHeight - o.bottom)))
            }
            DragMode.Draw -> {
                val a = clampToImage(start)
                val b = clampToImage(current)
                EditPreview(null, Box.spanning(a.x, a.y, b.x, b.y))
            }
            DragMode.Ignore -> null
        }
    }

    fun endDrag(mode: DragMode) {
        val result = preview
        when (mode) {
            is DragMode.Resize, is DragMode.Move -> if (result?.id != null) vm.updateBox(result.id, result.box)
            DragMode.Draw -> if (result != null &&
                result.box.width * viewport.scale >= minDrawPx && result.box.height * viewport.scale >= minDrawPx
            ) {
                vm.addDrawn(result.box)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            DragMode.Ignore -> Unit
        }
        preview = null
    }

    fun onTap(hit: Hit, position: Offset) {
        when (hit) {
            is Hit.Remove -> {
                vm.remove(hit.id)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            is Hit.Handle -> Unit
            is Hit.Body -> if (vm.activeId == hit.id) vm.activate(null) else {
                vm.activate(hit.id)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            Hit.Empty -> {
                val p = viewport.toImage(position)
                if (p.x in 0f..imageWidth && p.y in 0f..imageHeight) {
                    if (vm.selectAt(p.x, p.y, tapRadiusPx / viewport.scale) == TapResult.ADDED) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                } else {
                    vm.activate(null)
                }
            }
        }
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { viewport.size = it }
            .semantics { contentDescription = "Your sheet. ${vm.pieces.size} pieces selected." }
            .pointerInput(image) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val hit = hitTest(down.position)
                    val startImage = viewport.toImage(down.position)
                    var mode: DragMode? = null
                    var multiTouch = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2 || multiTouch) {
                            if (!multiTouch) {
                                multiTouch = true
                                preview = null
                            }
                            if (pressed >= 2) {
                                viewport.transform(event.calculateCentroid(useCurrent = true), event.calculatePan(), event.calculateZoom())
                            }
                            event.changes.forEach { it.consume() }
                        } else {
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change != null && change.pressed) {
                                if (mode == null && (change.position - down.position).getDistance() > touchSlop) {
                                    mode = startDrag(hit)
                                }
                                mode?.let {
                                    updateDrag(it, startImage, viewport.toImage(change.position))
                                    change.consume()
                                }
                            }
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    if (!multiTouch) {
                        val finished = mode
                        if (finished == null) onTap(hit, down.position) else endDrag(finished)
                    }
                }
            }
    ) {
        val o = viewport.origin
        val s = viewport.scale
        val imageRect = Rect(o.x, o.y, o.x + imageWidth * s, o.y + imageHeight * s)
        drawRect(Color.White, imageRect.topLeft, imageRect.size)
        drawImage(
            image,
            dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt()),
            dstSize = IntSize(imageRect.width.roundToInt(), imageRect.height.roundToInt()),
            filterQuality = FilterQuality.Medium
        )

        val edit = preview
        val selected = vm.pieces.map { piece -> piece to viewport.rect(if (edit?.id == piece.id) edit.box else piece.box) }

        // Dim what won't be printed so the chosen pieces stand out.
        if (selected.isNotEmpty()) {
            val keep = Path().apply { selected.forEach { addRect(it.second) } }
            clipPath(keep, ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.38f), imageRect.topLeft, imageRect.size)
            }
        }

        // Detected pieces not (yet) selected: a light dashed outline invites a tap.
        val chosen = vm.pieces.mapNotNull { it.detectedId }.toSet()
        val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))
        vm.detected.filter { it.id !in chosen }.forEach { detected ->
            val r = viewport.rect(detected.box)
            drawRoundRect(Color.White.copy(alpha = 0.95f), r.topLeft, r.size, CornerRadius(6.dp.toPx()), style = Stroke(2.dp.toPx(), pathEffect = dash))
        }

        selected.forEachIndexed { index, (piece, r) ->
            val active = piece.id == vm.activeId
            drawRoundRect(
                colors.primary, r.topLeft, r.size, CornerRadius(4.dp.toPx()),
                style = Stroke(if (active) 3.dp.toPx() else 2.dp.toPx())
            )
            drawBadge(textMeasurer, "${index + 1}", r.topLeft, colors.primary, colors.onPrimary)
        }

        vm.pieces.firstOrNull { it.id == vm.activeId }?.let { active ->
            val r = viewport.rect(if (edit?.id == active.id) edit.box else active.box)
            handlePoints(r, r.width >= edgeHandlesMinPx && r.height >= edgeHandlesMinPx).forEachIndexed { index, point ->
                val radius = if (index < 4) 8.dp.toPx() else 6.dp.toPx()
                drawCircle(Color.White, radius, point)
                drawCircle(colors.primary, radius, point, style = Stroke(2.5.dp.toPx()))
            }
            if (edit == null) {
                val pill = removePill(r)
                drawRoundRect(colors.inverseSurface, pill.topLeft, pill.size, CornerRadius(pill.height / 2f))
                val label = textMeasurer.measure(
                    "✕  Remove",
                    TextStyle(color = colors.inverseOnSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                )
                drawText(label, topLeft = pill.center - Offset(label.size.width / 2f, label.size.height / 2f))
            }
        }

        if (edit != null && edit.id == null) {
            val r = viewport.rect(edit.box)
            drawRect(colors.primary.copy(alpha = 0.14f), r.topLeft, r.size)
            drawRect(colors.primary, r.topLeft, r.size, style = Stroke(2.dp.toPx(), pathEffect = dash))
        }
    }
}

private fun DrawScope.drawBadge(measurer: TextMeasurer, text: String, center: Offset, fill: Color, onFill: Color) {
    val radius = 11.dp.toPx()
    drawCircle(Color.White, radius + 1.5.dp.toPx(), center)
    drawCircle(fill, radius, center)
    val layout = measurer.measure(text, TextStyle(color = onFill, fontSize = 11.sp, fontWeight = FontWeight.Bold))
    drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
}
