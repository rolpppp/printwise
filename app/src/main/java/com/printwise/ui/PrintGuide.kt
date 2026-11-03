package com.printwise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.printwise.ui.theme.printwise

/**
 * A plain-language checklist for printing on photo paper: no printer jargon, just what to tap and why.
 * Opens before printing (until switched off) and any time from the "photo paper tips" link.
 *
 * Android's print screen differs by printer, so the steps say "if your printer offers it" rather than
 * promising a setting is there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintGuideSheet(
    paperLabel: String,
    blurryCount: Int,
    beforePrint: Boolean,
    showBeforePrint: Boolean,
    onShowBeforePrintChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text("Print it sharp on photo paper", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "A few quick checks so your toppers come out crisp and the right size.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (blurryCount > 0) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.BlurOn, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (blurryCount == 1) {
                                "1 piece may print blurry because its picture is small for its size. Making it a little smaller helps."
                            } else {
                                "$blurryCount pieces may print blurry because their pictures are small for their size. Making them a little smaller helps."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GuideStep(
                    1, Icons.Rounded.Photo, "Load the photo paper",
                    "Put it in so the printer prints on the shiny side. The paper pack shows which way it goes in."
                )
                GuideStep(
                    2, Icons.Rounded.Description, "Pick your printer and paper size",
                    "On the next screen, choose your printer and set Paper size to $paperLabel."
                )
                GuideStep(
                    3, Icons.Rounded.Straighten, "Keep it actual size",
                    "If you see a Scale option, choose 100% or Actual size, not Fit to page. That keeps every piece the size you set."
                )
                GuideStep(
                    4, Icons.Rounded.Tune, "Choose photo settings",
                    "Tap the small arrow (˅) to see all options. If your printer offers them, set Paper type to Photo or Glossy, " +
                        "and Quality to High or Best. Some printers keep these under More settings or in the printer's own app."
                )
                GuideStep(
                    5, Icons.Rounded.WaterDrop, "Let it dry",
                    "Leave the page for a few minutes before touching or cutting, so the ink doesn't smudge."
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.printwise.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "First time? Print once on plain paper to check the sizes before using photo paper.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(role = Role.Checkbox) { onShowBeforePrintChange(!showBeforePrint) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = showBeforePrint, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                Text("Show this before every print", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (beforePrint) {
                    Icon(Icons.Rounded.Print, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Continue to print", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text("Got it", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun GuideStep(number: Int, icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("$number. $title", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
