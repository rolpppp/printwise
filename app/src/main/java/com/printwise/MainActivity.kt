package com.printwise

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.printwise.export.PrintExporter
import com.printwise.export.ShareExporter
import com.printwise.export.exportFileName
import com.printwise.ui.HomeScreen
import com.printwise.ui.LocalTour
import com.printwise.ui.PrintScreen
import com.printwise.ui.SelectScreen
import com.printwise.ui.SizeScreen
import com.printwise.ui.StepProgress
import com.printwise.ui.TourOverlay
import com.printwise.ui.TourState
import com.printwise.ui.WelcomeScreen
import com.printwise.ui.theme.PrintwiseTheme

class MainActivity : ComponentActivity() {
    private val vm: PrintwiseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Printwise is light-only for now, so system bar icons stay dark even when the phone is in dark mode.
        val bars = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
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
    val addPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::addSheet)
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let(vm::savePdf)
    }
    val saveDocx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri -> uri?.let(vm::saveDocx) }

    val tour = remember { TourState() }
    // Opens by itself only on the very first launch, and not when the app was opened by sharing a picture to it.
    var showWelcome by rememberSaveable { mutableStateOf(!vm.seenWelcome && vm.step == Step.HOME) }
    var confirmSample by remember { mutableStateOf(false) }
    val pickPicture = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    fun closeWelcome() {
        vm.markWelcomeSeen()
        showWelcome = false
    }
    fun startSampleTour() {
        confirmSample = false
        closeWelcome()
        vm.importSample()
        tour.start()
    }
    LaunchedEffect(vm.step) { tour.followStep(vm.step) }

    BackHandler(enabled = showWelcome) { closeWelcome() }
    BackHandler(enabled = !showWelcome && vm.step != Step.HOME) { vm.back() }

    // Success is confirmed with a snackbar rather than a dialog: nothing to decide, so nothing to tap away.
    val snackbar = remember { SnackbarHostState() }
    val status = vm.exportStatus
    LaunchedEffect(status) {
        if (status is ExportStatus.Done) {
            vm.dismissExportStatus()
            snackbar.showSnackbar("${status.title} ${status.message}", withDismissAction = true, duration = SnackbarDuration.Long)
        }
    }

    CompositionLocalProvider(LocalTour provides tour) {
        Box(Modifier.fillMaxSize()) {
            if (showWelcome) {
                WelcomeScreen(
                    // Starting the sample replaces the current sheet, so ask first if there is one.
                    onTrySample = { if (vm.pieces.isNotEmpty()) confirmSample = true else startSampleTour() },
                    onUseOwnPicture = { closeWelcome(); pickPicture() },
                    onSkip = ::closeWelcome
                )
            } else {
                Steps(
                    vm, activity, snackbar,
                    onPick = pickPicture,
                    onHowItWorks = { showWelcome = true },
                    onAddSheet = { addPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onSavePdf = { savePdf.launch(exportFileName("pdf")) },
                    onSaveDocx = { saveDocx.launch(exportFileName("docx")) }
                )
            }
            TourOverlay(
                tour = tour,
                step = vm.step,
                // Waits for the sample to open and its pieces to be found, so the first tip has something to point at.
                hidden = showWelcome || vm.loading || vm.detection is Detection.Running,
                onNavigate = vm::goTo,
                onUseOwnPicture = { vm.goTo(Step.HOME); pickPicture() }
            )
        }
    }

    ExportStatusDialogs(vm)
    if (confirmSample) {
        AlertDialog(
            onDismissRequest = { confirmSample = false },
            title = { Text("Open the sample?") },
            text = { Text("The sample sheet replaces the sheet you're working on now.") },
            confirmButton = { TextButton(onClick = ::startSampleTour) { Text("Open sample") } },
            dismissButton = { TextButton(onClick = { confirmSample = false }) { Text("Cancel") } }
        )
    }
    if (vm.pendingChange != null) {
        AlertDialog(
            onDismissRequest = vm::cancelPendingChange,
            title = { Text("Re-arrange pages?") },
            text = { Text("You've placed or resized pieces by hand on the pages. This change re-arranges the pages and undoes those edits.") },
            confirmButton = { TextButton(onClick = vm::confirmPendingChange) { Text("Re-arrange") } },
            dismissButton = { TextButton(onClick = vm::cancelPendingChange) { Text("Cancel") } }
        )
    }
}

/** Home, or the current step with its top bar and progress. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Steps(
    vm: PrintwiseViewModel,
    activity: ComponentActivity,
    snackbar: SnackbarHostState,
    onPick: () -> Unit,
    onHowItWorks: () -> Unit,
    onAddSheet: () -> Unit,
    onSavePdf: () -> Unit,
    onSaveDocx: () -> Unit
) {
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
                continueImage = if (vm.pieces.isNotEmpty()) vm.sheets.firstOrNull()?.image else null,
                continueCount = vm.pieces.size,
                onPick = onPick,
                onContinue = { vm.goTo(Step.SELECT) },
                onHowItWorks = onHowItWorks
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
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                        )
                        StepProgress(step.number, Modifier.padding(bottom = 8.dp))
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (step) {
                        Step.SELECT -> SelectScreen(
                            vm,
                            onAddSheet = onAddSheet,
                            onNext = { vm.goTo(Step.SIZE) }
                        )
                        Step.SIZE -> SizeScreen(vm, onAddPiece = { vm.goTo(Step.SELECT) }, onNext = { vm.goTo(Step.PRINT) })
                        Step.PRINT -> PrintScreen(
                            vm,
                            snackbar = snackbar,
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
                            onSavePdf = onSavePdf,
                            onSaveDocx = onSaveDocx
                        )
                        Step.HOME -> Unit
                    }
                }
            }
        }
    }
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
        is ExportStatus.Done -> Unit // shown as a snackbar
        is ExportStatus.Failed -> AlertDialog(
            onDismissRequest = vm::dismissExportStatus,
            icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("That didn't work") },
            text = { Text(status.message) },
            confirmButton = { TextButton(onClick = vm::dismissExportStatus) { Text("OK") } }
        )
    }
}
