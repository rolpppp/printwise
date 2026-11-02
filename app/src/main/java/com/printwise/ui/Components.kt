package com.printwise.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Three-segment progress shown under the top bar: Pick, Size, Print. */
@Composable
fun StepProgress(current: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Pick", "Size", "Print").forEachIndexed { index, label ->
            val reached = index + 1 <= current
            val color by animateColorAsState(
                if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                label = "step"
            )
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(color))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index + 1 == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** A raised bar pinned to the bottom of a step, holding its primary action. */
@Composable
fun BottomActionBar(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Box(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.widthIn(max = 640.dp).fillMaxWidth()) { content() }
        }
    }
}

@Composable
fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
    valueWidth: Dp = 72.dp,
    contentDescription: String = ""
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onMinus, enabled = minusEnabled, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Remove, contentDescription = "Less $contentDescription")
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = valueWidth)
        )
        FilledTonalIconButton(onClick = onPlus, enabled = plusEnabled, modifier = Modifier.size(40.dp)) {
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
    val shape = RoundedCornerShape(14.dp)
    val borderColor by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "tileBorder"
    )
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Color.White)
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
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("$number", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        overlay()
    }
}
