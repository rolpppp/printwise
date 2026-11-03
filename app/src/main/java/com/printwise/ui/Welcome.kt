package com.printwise.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.printwise.image.SampleSheet
import com.printwise.ui.theme.Motion
import kotlinx.coroutines.launch

private data class WelcomePage(val icon: ImageVector?, val title: String, val body: String)

private val PAGES = listOf(
    WelcomePage(
        null, "Cake toppers, printed the right size",
        "Got a sheet of topper designs? Printwise turns it into a page you can print, with every piece exactly as big as you want."
    ),
    WelcomePage(
        Icons.Rounded.TouchApp, "1. Pick your pieces",
        "Choose a picture of your sheet. We find each piece for you. Tap one to take it out or put it back."
    ),
    WelcomePage(
        Icons.Rounded.Height, "2. Choose the size",
        "Say how tall each piece should be and how many you need. Type the number or tap − and +."
    ),
    WelcomePage(
        Icons.Rounded.ContentCut, "3. Print and cut",
        "We fit everything onto your paper. Print it, ideally on photo paper, then cut the pieces out."
    )
)

/**
 * First-launch welcome: what the app is for and its three steps, ending in an offer to watch it work on a sample
 * sheet. Also reachable from Home ("See how it works"), and skippable from every page.
 */
@Composable
fun WelcomeScreen(onTrySample: () -> Unit, onUseOwnPicture: () -> Unit, onSkip: () -> Unit) {
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES.lastIndex
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize().background(colors.background).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
            if (!last) TextButton(onClick = onSkip) { Text("Skip") }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
            Page(PAGES[index])
        }
        PageDots(pager.currentPage, PAGES.size)
        Spacer(Modifier.height(24.dp))
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
            if (last) {
                Button(onClick = onTrySample, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Rounded.PlayCircle, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Show me with a sample", style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onUseOwnPicture, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("I'll use my own picture")
                }
            } else {
                Button(
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Next", style = MaterialTheme.typography.titleMedium)
                }
                // Same height as the text button on the last page, so the Next button doesn't jump.
                Spacer(Modifier.height(48.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Page(page: WelcomePage) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (page.icon == null) {
            SamplePreview()
        } else {
            Box(Modifier.size(160.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(page.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(80.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(page.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = colors.onBackground)
        Spacer(Modifier.height(12.dp))
        Text(
            page.body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = colors.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 420.dp)
        )
    }
}

/** The tour's sample sheet on a paper card: shows the kind of picture the app expects. */
@Composable
private fun SamplePreview() {
    val image = remember { SampleSheet.render(width = 480).asImageBitmap() }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 4.dp
    ) {
        Image(
            image,
            contentDescription = "A sample sheet of birthday cake topper pieces",
            contentScale = ContentScale.Fit,
            modifier = Modifier.padding(12.dp).height(220.dp)
        )
    }
}

@Composable
private fun PageDots(current: Int, count: Int) {
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "Page ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, tween(Motion.BASE_MS, easing = Motion.EaseOut), label = "dot")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dotColor"
            )
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}
