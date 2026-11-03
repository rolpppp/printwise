package com.printwise.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.printwise.Step
import com.printwise.ui.theme.Motion
import com.printwise.ui.theme.printwise
import kotlin.math.min
import kotlin.math.roundToInt

/** The parts of the real screens the tour points at. */
enum class TourTarget { SHEET, ADD_PICTURE, NEXT, HEIGHT, COPIES, UNITS, PREVIEW, PAGE, TRAY, PAPER, ACTIONS }

data class TourTip(val step: Step, val target: TourTarget, val title: String, val body: String)

/** The walkthrough, in order. Plain words only: no "segmentation", no "DPI". */
val TOUR_TIPS = listOf(
    TourTip(
        Step.SELECT, TourTarget.SHEET, "This is a sample topper sheet",
        "We found each piece and gave it a number. Everything with a number will be printed."
    ),
    TourTip(
        Step.SELECT, TourTarget.SHEET, "Choose what gets printed",
        "Tap a piece to take it out, and tap it again to put it back. Missed something? Drag your finger across it to draw a box."
    ),
    TourTip(
        Step.SELECT, TourTarget.ADD_PICTURE, "Pieces on another picture?",
        "Tap Add picture to bring in another sheet. Its pieces print together with these."
    ),
    TourTip(Step.SELECT, TourTarget.NEXT, "Happy with your pieces?", "Next, you'll choose how big each one prints."),
    TourTip(
        Step.SIZE, TourTarget.HEIGHT, "Set how tall it prints",
        "Tap − or + to change the height, or tap the number and type it in, like 2.5. The width follows by itself."
    ),
    TourTip(Step.SIZE, TourTarget.COPIES, "Need more than one?", "Change Copies to print the same piece a few times."),
    TourTip(Step.SIZE, TourTarget.UNITS, "Inches or centimetres", "Use whichever your ruler shows. Every size switches at once."),
    TourTip(Step.SIZE, TourTarget.PREVIEW, "See it on paper", "Next, you'll see exactly how everything fits on the page."),
    TourTip(
        Step.PRINT, TourTarget.PAGE, "This is your page",
        "It looks exactly like it will print. Tap a piece on the page to make just that one bigger or smaller, or to remove it."
    ),
    TourTip(
        Step.PRINT, TourTarget.TRAY, "Fill the empty space",
        "Tap a piece here to add one more copy where there's room. Out of room? Tap Add page below the last page."
    ),
    TourTip(Step.PRINT, TourTarget.PAPER, "Match your paper", "Choose the paper size you'll print on, like A4 or Letter."),
    TourTip(
        Step.PRINT, TourTarget.ACTIONS, "Print or save",
        "Tap Print to send it to your printer. We'll show a few quick settings for sharp photo paper prints. " +
            "Or tap Save to keep a PDF and print it later."
    )
)

/** Where the tour is ([index] into [TOUR_TIPS], null when it isn't running) and where each target sits on screen. */
@Stable
class TourState {
    var index by mutableStateOf<Int?>(null)
        private set
    internal val bounds = mutableStateMapOf<TourTarget, Rect>()

    val tip: TourTip? get() = index?.let(TOUR_TIPS::getOrNull)

    fun start() {
        index = 0
    }

    fun end() {
        index = null
    }

    /** Moves to tip [to]; returns the step it lives on so the caller can navigate there. */
    fun moveTo(to: Int): Step? {
        val tip = TOUR_TIPS.getOrNull(to) ?: return null.also { end() }
        index = to
        return tip.step
    }

    /** Keeps the tour on the screen the user is on, when they move with the app's own buttons. */
    fun followStep(step: Step) {
        val current = tip ?: return
        if (current.step == step) return
        val first = TOUR_TIPS.indexOfFirst { it.step == step }
        if (first < 0) end() else index = first
    }
}

val LocalTour = staticCompositionLocalOf<TourState?> { null }

/** Marks this element as [target] so the tour can spotlight it. Does nothing outside a tour. */
fun Modifier.tourTarget(target: TourTarget): Modifier = composed {
    val tour = LocalTour.current ?: return@composed this
    DisposableEffect(tour, target) { onDispose { tour.bounds.remove(target) } }
    onGloballyPositioned { tour.bounds[target] = it.boundsInRoot() }
}

/**
 * Dims the screen except for the current tip's target and shows the tip card beside it. The dim layer doesn't
 * take touches, so the user can try the highlighted control for real while the tip is up.
 */
@Composable
fun TourOverlay(tour: TourState, step: Step, hidden: Boolean, onNavigate: (Step) -> Unit, onUseOwnPicture: () -> Unit) {
    val index = tour.index ?: return
    val tip = TOUR_TIPS[index]
    if (hidden || tip.step != step) return
    val target = tour.bounds[tip.target]?.takeIf { it.width > 0f && it.height > 0f }
    val ring = MaterialTheme.printwise.accent
    val density = LocalDensity.current
    val pad = with(density) { 6.dp.toPx() }
    val corner = with(density) { 14.dp.toPx() }
    // The spotlight glides from one target to the next, so the eye follows it instead of hunting. The first one
    // appears in place rather than flying in from a corner.
    val spotlight = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    LaunchedEffect(target) {
        val to = target?.inflate(pad) ?: return@LaunchedEffect
        if (spotlight.value == Rect.Zero) spotlight.snapTo(to)
        else spotlight.animateTo(to, tween(Motion.BASE_MS + 50, easing = Motion.EaseOut))
    }
    val spot = spotlight.value
    val navigate = { to: Int -> tour.moveTo(to)?.takeIf { it != step }?.let(onNavigate) }

    Box(Modifier.fillMaxSize()) {
        if (target != null) {
            Canvas(Modifier.fillMaxSize()) {
                val hole = RoundRect(spot, CornerRadius(corner))
                val scrim = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRoundRect(hole)
                }
                drawPath(scrim, Color.Black.copy(alpha = 0.45f))
                drawPath(Path().apply { addRoundRect(hole) }, ring, style = Stroke(width = 3.dp.toPx()))
            }
        }
        TipPlacement(target?.inflate(pad)) {
            TipCard(
                tip = tip,
                index = index,
                onBack = { navigate(index - 1) },
                onNext = { navigate(index + 1) },
                onSkip = tour::end,
                onUseOwnPicture = { tour.end(); onUseOwnPicture() }
            )
        }
    }
}

/** Puts the card on whichever side of the target has room; over the target's lower edge if neither does. */
@Composable
private fun TipPlacement(target: Rect?, card: @Composable () -> Unit) {
    val insets = WindowInsets.systemBars
    Layout(content = card, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val margin = 16.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val width = min(constraints.maxWidth - 2 * margin, 480.dp.roundToPx())
        val placeable = measurables.first().measure(Constraints(minWidth = width, maxWidth = width))
        val top = insets.getTop(this) + margin
        val bottom = constraints.maxHeight - insets.getBottom(this) - margin
        val h = placeable.height
        val y = if (target == null) {
            bottom - h
        } else {
            val above = target.top.roundToInt() - gap - top
            val below = bottom - (target.bottom.roundToInt() + gap)
            when {
                below >= h && below >= above -> target.bottom.roundToInt() + gap
                above >= h -> target.top.roundToInt() - gap - h
                below >= h -> target.bottom.roundToInt() + gap
                else -> min(target.bottom.roundToInt() - gap - h, bottom - h)
            }.coerceIn(top, (bottom - h).coerceAtLeast(top))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place((constraints.maxWidth - width) / 2, y)
        }
    }
}

@Composable
private fun TipCard(
    tip: TourTip,
    index: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onUseOwnPicture: () -> Unit
) {
    val last = index == TOUR_TIPS.lastIndex
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
            // Same height with or without Skip, so the title doesn't jump up on the last tip.
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                StepDots(index, Modifier.weight(1f))
                if (!last) TextButton(onClick = onSkip) { Text("Skip tour") }
            }
            AnimatedContent(
                targetState = tip,
                transitionSpec = { fadeIn(tween(Motion.BASE_MS)) togetherWith fadeOut(tween(Motion.FAST_MS)) },
                label = "tip"
            ) { shown ->
                Column(Modifier.fillMaxWidth().padding(end = 8.dp)) {
                    Text(shown.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(shown.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (last) {
                    TextButton(onClick = onUseOwnPicture) { Text("Use my own picture") }
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = onSkip, shape = MaterialTheme.shapes.medium) { Text("Done") }
                } else {
                    if (index > 0) TextButton(onClick = onBack) { Text("Back") }
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = onNext, shape = MaterialTheme.shapes.medium) { Text("Next") }
                }
            }
        }
    }
}

/** "Tip 3 of 12" as a row of dots, with the words kept for screen readers. */
@Composable
private fun StepDots(index: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Tip ${index + 1} of ${TOUR_TIPS.size}" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TOUR_TIPS.indices.forEach { i ->
            val color = if (i <= index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(Modifier.size(if (i == index) 8.dp else 6.dp).clip(CircleShape).background(color))
        }
    }
}
