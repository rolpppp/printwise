package com.printwise.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.printwise.ui.theme.Motion
import com.printwise.ui.theme.printwise

/** Three-segment progress shown under the top bar: Pick, Size, Print. The top bar title names the step. */
@Composable
fun StepProgress(current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = "Step $current of 3" },
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(3) { index ->
            val color by animateColorAsState(
                if (index + 1 <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "step"
            )
            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(color))
        }
    }
}

/** A bar pinned to the bottom of a step, holding its primary action. */
@Composable
fun BottomActionBar(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.widthIn(max = 640.dp).fillMaxWidth()) { content() }
            }
        }
    }
}

/**
 * Text that rolls when its number changes: up when it grows, down when it shrinks. Small and quick, because it
 * fires on every stepper tap; it confirms the tap landed without drawing the eye.
 */
@Composable
fun RollingText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            val up = (leadingNumber(targetState) ?: 0f) >= (leadingNumber(initialState) ?: 0f)
            val spec = tween<Float>(Motion.FAST_MS, easing = Motion.EaseOut)
            (slideInVertically(tween(Motion.FAST_MS, easing = Motion.EaseOut)) { if (up) it / 2 else -it / 2 } + fadeIn(spec)) togetherWith
                (slideOutVertically(tween(Motion.FAST_MS, easing = Motion.EaseOut)) { if (up) -it / 2 else it / 2 } + fadeOut(spec))
        },
        contentAlignment = Alignment.Center,
        label = "rolling"
    ) { shown ->
        Text(shown, style = style, color = color)
    }
}

private fun leadingNumber(text: String): Float? =
    Regex("""\d+(?:[.,]\d+)?""").find(text)?.value?.replace(',', '.')?.toFloatOrNull()

/**
 * A minus/plus pair around a number that can also be typed. [value] is the plain number (no unit); [prefix] and [suffix]
 * decorate it. Typing is only handed to [onValueCommit] on IME Done or when focus is lost, never per keystroke, so a
 * half-typed "2." doesn't reach the model. Whatever the model ends up holding is shown afterwards, which also reverts
 * invalid or empty input.
 */
@Composable
fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onValueCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
    valueWidth: Dp = 72.dp,
    contentDescription: String = "",
    prefix: String = "",
    suffix: String = "",
    decimal: Boolean = false
) {
    var text by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    fun commit() {
        if (text != value) onValueCommit(text)
        text = value
    }
    val textStyle = MaterialTheme.typography.titleMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )
    val fieldColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "stepperField"
    )
    val fieldBorder by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "stepperBorder"
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // No size override: the default 40dp button keeps its 48dp touch target.
        FilledTonalIconButton(onClick = onMinus, enabled = minusEnabled) {
            Icon(Icons.Rounded.Remove, contentDescription = "Less $contentDescription")
        }
        BasicTextField(
            value = text,
            onValueChange = { input ->
                // Keep only what a number can contain; the full parse happens on commit.
                var dotSeen = false
                text = input.filter { ch ->
                    when {
                        ch.isDigit() -> true
                        decimal && (ch == '.' || ch == ',') && !dotSeen -> { dotSeen = true; true }
                        else -> false
                    }
                }.take(6)
            },
            modifier = Modifier
                .padding(horizontal = 2.dp)
                // A fixed width: a single-line text field otherwise grabs all the space the row offers.
                .width(valueWidth)
                .onFocusChanged {
                    if (focused && !it.isFocused) commit()
                    focused = it.isFocused
                }
                .semantics { this.contentDescription = contentDescription },
            textStyle = textStyle,
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(fieldColor)
                        .border(1.5.dp, fieldBorder, MaterialTheme.shapes.small)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // The editable field, visible only while typing; the unit sits beside it.
                    Row(Modifier.alpha(if (focused) 1f else 0f), verticalAlignment = Alignment.CenterVertically) {
                        if (prefix.isNotEmpty()) Text(prefix, style = textStyle)
                        Box(Modifier.weight(1f, fill = false)) { inner() }
                        if (suffix.isNotEmpty()) Text(" $suffix", style = textStyle)
                    }
                    // Otherwise a centred "1.5 in" that rolls on +/- taps.
                    if (!focused) {
                        RollingText(
                            prefix + text + if (suffix.isNotEmpty()) " $suffix" else "",
                            textStyle,
                            modifier = Modifier.clearAndSetSemantics {}
                        )
                    }
                }
            }
        )
        FilledTonalIconButton(onClick = onPlus, enabled = plusEnabled) {
            Icon(Icons.Rounded.Add, contentDescription = "More $contentDescription")
        }
    }
}

/** A piece preview on a white "paper" tile with an optional number badge. */
@Composable
fun PieceTile(
    image: ImageBitmap?,
    size: Dp,
    modifier: Modifier = Modifier,
    number: Int? = null,
    highlighted: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val shape = MaterialTheme.shapes.small
    val borderColor by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.printwise.unselectedBorder,
        label = "tileBorder"
    )
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.printwise.paper)
            .border(if (highlighted) 2.5.dp else 1.dp, borderColor, shape)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(image, contentDescription = number?.let { "Piece $it" }, contentScale = ContentScale.Fit)
        }
        if (number != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("$number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        overlay()
    }
}
