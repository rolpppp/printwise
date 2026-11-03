package com.printwise.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    loading: Boolean,
    error: String?,
    continueImage: ImageBitmap?,
    continueCount: Int,
    onPick: () -> Unit,
    onContinue: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(colors.background).systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            Box(
                Modifier.size(112.dp).clip(CircleShape).background(colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Cake, contentDescription = null, tint = colors.primary, modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Cake toppers,\nprinted the right size.",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = colors.onBackground
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Choose a picture of your sheet. We'll find each piece, size it, and lay it out on paper for you.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(36.dp))
            // A returning user most likely wants their sheet back, so it comes before starting over.
            if (continueImage != null) {
                OutlinedCard(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.outlinedCardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.outlineVariant)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PieceTile(continueImage, 56.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text("Continue your sheet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (continueCount == 1) "1 piece selected" else "$continueCount pieces selected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = colors.primary)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Button(
                onClick = onPick,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = colors.onPrimary, strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Opening…", style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (continueImage != null) "Choose a new picture" else "Choose a picture",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = colors.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Works best with sheets on a plain background, like most printable topper designs.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
