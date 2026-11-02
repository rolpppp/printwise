package com.printwise

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.printwise.export.PrintExporter
import com.printwise.export.ShareExporter
import com.printwise.export.exportFileName
import com.printwise.ui.HomeScreen
import com.printwise.ui.PrintScreen
import com.printwise.ui.SelectScreen
import com.printwise.ui.SizeScreen
import com.printwise.ui.StepProgress
import com.printwise.ui.theme.PrintwiseTheme

class MainActivity : ComponentActivity() {
    private val vm: PrintwiseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) importShared(intent)
        setContent {
            PrintwiseTheme { PrintwiseApp(vm, this) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importShared(intent)
    }

    /** "Share → Printwise" from the gallery or a browser. */
    private fun importShared(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return
        val uri = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        uri?.let(vm::import)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrintwiseApp(vm: PrintwiseViewModel, activity: ComponentActivity) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::import)
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let(vm::savePdf)
    }
    val saveDocx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri -> uri?.let(vm::saveDocx) }

    BackHandler(enabled = vm.step != Step.HOME) { vm.back() }

    AnimatedContent(
        targetState = vm.step,
        transitionSpec = {
            val forward = targetState.number > initialState.number
            (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
        },
        label = "step"
    ) { step ->
        if (step == Step.HOME) {
            HomeScreen(
                loading = vm.loading,
                error = vm.importError,
                continueImage = if (vm.pieces.isNotEmpty()) vm.sourceImage else null,
                continueCount = vm.pieces.size,
                onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onContinue = { vm.goTo(Step.SELECT) }
            )
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                topBar = {
                    Column {
                        CenterAlignedTopAppBar(
                            title = { Text(step.title, style = MaterialTheme.typography.titleLarge) },
                            navigationIcon = {
                                IconButton(onClick = { vm.back() }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                        StepProgress(step.number, Modifier.padding(bottom = 8.dp))
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (step) {
                        Step.SELECT -> SelectScreen(vm) { vm.goTo(Step.SIZE) }
                        Step.SIZE -> SizeScreen(vm) { vm.goTo(Step.PRINT) }
                        Step.PRINT -> PrintScreen(
                            vm,
                            onPrint = {
                                vm.preparePrint { pdf, layout ->
                                    PrintExporter.print(activity, pdf, layout.pageCount, layout.paper, layout.landscape)
                                }
                            },
                            onShare = {
                                vm.prepareShare { uri ->
                                    activity.startActivity(ShareExporter.shareIntent(uri, "application/pdf"))
                                }
                            },
                            onSavePdf = { savePdf.launch(exportFileName("pdf")) },
                            onSaveDocx = { saveDocx.launch(exportFileName("docx")) }
                        )
                        Step.HOME -> Unit
                    }
                }
            }
        }
    }

    ExportStatusDialogs(vm)
}

@Composable
private fun ExportStatusDialogs(vm: PrintwiseViewModel) {
    when (val status = vm.exportStatus) {
        ExportStatus.Idle -> Unit
        is ExportStatus.Working -> Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(18.dp))
                    Text(status.label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        is ExportStatus.Done -> AlertDialog(
            onDismissRequest = vm::dismissExportStatus,
            icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) },
            title = { Text(status.title) },
            text = { Text(status.message) },
            confirmButton = { TextButton(onClick = vm::dismissExportStatus) { Text("Great") } }
        )
        is ExportStatus.Failed -> AlertDialog(
            onDismissRequest = vm::dismissExportStatus,
            icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("That didn't work") },
            text = { Text(status.message) },
            confirmButton = { TextButton(onClick = vm::dismissExportStatus) { Text("OK") } }
        )
    }
}
