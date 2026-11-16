package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.ActiveSheet
import com.example.model.HtmlElementKind
import com.example.model.OverlayType
import com.example.ui.components.AddGraphicSheet
import com.example.ui.components.EditSceneSheet
import com.example.ui.components.ExportVideoSheet
import com.example.ui.components.HtmlElementsInspectorSheet
import com.example.ui.components.MediaOverlayEditSheet
import com.example.ui.components.TextOverlaySheet
import com.example.ui.components.TimelinePanel
import com.example.ui.components.formatTimecode
import com.example.ui.theme.AmberCut
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldRender
import com.example.ui.theme.StudioBg
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceCard
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletMotion
import com.example.viewmodel.StudioUiState
import com.example.viewmodel.StudioViewModel

@Composable
fun EditorWorkspaceScreen(
    uiState: StudioUiState,
    viewModel: StudioViewModel
) {
    val project = uiState.project ?: return
    val context = LocalContext.current

    BackHandler {
        viewModel.closeProject()
    }

    // Zero-permission Photo Picker for Gallery Images
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.addPhotoOverlay(it) }
    }

    // Zero-permission Photo Picker for Gallery Videos
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.addVideoOverlay(it) }
    }

    // Zero-permission SAF Audio Picker
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.addAudioTrack(it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBg)
    ) {
        // 1. Top Studio Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurface)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = { viewModel.closeProject() },
                modifier = Modifier.testTag("editor_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Import",
                    tint = TextPrimary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${project.canvasWidth}×${project.canvasHeight} • ${project.totalDurationSec.toInt()}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanPulse
                )
            }

            IconButton(
                onClick = { viewModel.toggleTapGuides() },
                modifier = Modifier.testTag("toggle_tap_guides_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.CropFree,
                    contentDescription = "Show Tap Boxes",
                    tint = if (uiState.showTapGuides) AmberCut else TextSecondary
                )
            }

            IconButton(
                onClick = { viewModel.toggleMute() },
                modifier = Modifier.testTag("mute_toggle_button")
            ) {
                Icon(
                    imageVector = if (uiState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Toggle Audio",
                    tint = if (uiState.isMuted) CoralAlert else TextSecondary
                )
            }

            Button(
                onClick = { viewModel.setActiveSheet(ActiveSheet.EXPORT) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldRender,
                    contentColor = Color(0xFF041C12)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("open_export_button")
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Export", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }

        // 2. Live Preview Stage (WebView HTML Canvas + Interactive Native Overlays)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val aspect = (project.canvasWidth.toFloat() / project.canvasHeight.coerceAtLeast(1))
                .coerceIn(0.45f, 2.2f)
            var stageSize by remember { mutableStateOf(IntSize(1, 1)) }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(aspect, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, StudioBorder, RoundedCornerShape(16.dp))
                    .background(Color(0xFF070B12))
                    .onSizeChanged { stageSize = it }
                    .testTag("preview_stage")
            ) {
                // 1. HTML Motion WebView Layer
                AndroidView(
                    factory = {
                        viewModel.webController.getOrCreateWebView {
                            viewModel.onWebViewReady()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 2. User Overlays + HTML Selection & Tap-Guide Highlight Layer
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawIntoCanvas { canvas ->
                        viewModel.overlayRenderer.renderOverlays(
                            canvas = canvas.nativeCanvas,
                            width = size.width.toInt(),
                            height = size.height.toInt(),
                            overlays = uiState.overlays,
                            currentTimeSec = uiState.currentTimeSec,
                            selectedOverlayId = uiState.selectedOverlayId,
                            drawSelectionChrome = true
                        )
                    }

                    // Optional Tap Guides showing every editable card/picture/text
                    if (uiState.showTapGuides) {
                        uiState.detectedHtmlElements.forEach { el ->
                            val ov = uiState.htmlElementOverrides[el.key]
                            if (ov?.isDeleted != true) {
                                val dx = (ov?.offsetXNorm ?: 0f) * size.width
                                val dy = (ov?.offsetYNorm ?: 0f) * size.height
                                val left = (el.xNorm * size.width + dx).coerceIn(0f, size.width)
                                val top = (el.yNorm * size.height + dy).coerceIn(0f, size.height)
                                val w = (el.wNorm * size.width).coerceAtLeast(24f)
                                val h = (el.hNorm * size.height).coerceAtLeast(18f)
                                drawRect(
                                    color = if (el.kind == HtmlElementKind.TEXT) CyanPulse.copy(alpha = 0.55f) else AmberCut.copy(alpha = 0.45f),
                                    topLeft = Offset(left, top),
                                    size = Size(w, h),
                                    style = Stroke(
                                        width = 2f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                                    )
                                )
                            }
                        }
                    }

                    // Draw selection box if an HTML element is selected
                    val selKey = uiState.selectedHtmlElementKey
                    if (selKey != null) {
                        val el = uiState.detectedHtmlElements.firstOrNull { it.key == selKey }
                        if (el != null) {
                            val ov = uiState.htmlElementOverrides[selKey]
                            val dx = (ov?.offsetXNorm ?: 0f) * size.width
                            val dy = (ov?.offsetYNorm ?: 0f) * size.height
                            val left = (el.xNorm * size.width + dx - 8f).coerceIn(0f, size.width)
                            val top = (el.yNorm * size.height + dy - 8f).coerceIn(0f, size.height)
                            val w = (el.wNorm * size.width + 16f).coerceAtLeast(48f)
                            val h = (el.hNorm * size.height + 16f).coerceAtLeast(32f)
                            drawRect(
                                color = AmberCut,
                                topLeft = Offset(left, top),
                                size = Size(w, h),
                                style = Stroke(
                                    width = 4.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                )
                            )
                        }
                    }
                }

                // 3. Dedicated Topmost Compose Touch Layer (intercepts taps/drags above WebView!)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val w = stageSize.width.coerceAtLeast(1).toFloat()
                                    val h = stageSize.height.coerceAtLeast(1).toFloat()
                                    viewModel.handlePreviewTap(offset.x / w, offset.y / h)
                                },
                                onDoubleTap = { offset ->
                                    val w = stageSize.width.coerceAtLeast(1).toFloat()
                                    val h = stageSize.height.coerceAtLeast(1).toFloat()
                                    viewModel.handlePreviewDoubleTap(offset.x / w, offset.y / h)
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val w = stageSize.width.coerceAtLeast(1).toFloat()
                                val h = stageSize.height.coerceAtLeast(1).toFloat()
                                viewModel.moveSelectedElementBy(dragAmount.x / w, dragAmount.y / h)
                            }
                        }
                )
            }
        }

        // 3. Contextual Selection Quick Toolbar (Drag / Scale / Shift Time / Edit / Delete)
        val selectedOverlay = uiState.overlays.firstOrNull { it.id == uiState.selectedOverlayId }
        val selectedHtmlEl = uiState.detectedHtmlElements.firstOrNull { it.key == uiState.selectedHtmlElementKey }
        val selectedAudio = uiState.audioTracks.firstOrNull { it.id == uiState.selectedAudioId }

        AnimatedVisibility(visible = selectedOverlay != null || selectedHtmlEl != null || selectedAudio != null) {
            Surface(
                color = StudioSurfaceCard,
                border = BorderStroke(1.dp, AmberCut.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val label = when {
                        selectedOverlay != null -> "${selectedOverlay.type.label}: ${selectedOverlay.name}"
                        selectedHtmlEl != null -> "HTML: ${selectedHtmlEl.displayText.take(18)}"
                        selectedAudio != null -> "Audio: ${selectedAudio.title.take(18)}"
                        else -> ""
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberCut
                    )

                    if (selectedOverlay != null || selectedHtmlEl != null) {
                        QuickActionPill(
                            icon = Icons.Filled.ZoomOut,
                            label = "Smaller",
                            onClick = { viewModel.scaleSelectedElement(-0.12f) }
                        )
                        QuickActionPill(
                            icon = Icons.Filled.ZoomIn,
                            label = "Larger",
                            onClick = { viewModel.scaleSelectedElement(0.12f) }
                        )
                    }

                    if (selectedOverlay != null) {
                        QuickActionPill(
                            icon = Icons.Filled.FastRewind,
                            label = "-1s",
                            onClick = { viewModel.shiftOverlayOnTimeline(selectedOverlay.id, -1f) }
                        )
                        QuickActionPill(
                            icon = Icons.Filled.FastForward,
                            label = "+1s",
                            onClick = { viewModel.shiftOverlayOnTimeline(selectedOverlay.id, 1f) }
                        )
                        QuickActionPill(
                            icon = Icons.Filled.Edit,
                            label = "Edit",
                            onClick = {
                                viewModel.setActiveSheet(
                                    if (selectedOverlay.type == OverlayType.TEXT) ActiveSheet.EDIT_TEXT
                                    else ActiveSheet.EDIT_MEDIA_OVERLAY
                                )
                            }
                        )
                    }

                    if (selectedHtmlEl != null) {
                        QuickActionPill(
                            icon = Icons.Filled.Edit,
                            label = "Edit",
                            onClick = { viewModel.setActiveSheet(ActiveSheet.HTML_ELEMENTS) }
                        )
                        if (selectedHtmlEl.kind != HtmlElementKind.TEXT) {
                            QuickActionPill(
                                icon = Icons.Filled.DeleteSweep,
                                label = "Delete Card+Text",
                                tint = CoralAlert,
                                onClick = { viewModel.deleteHtmlArea(selectedHtmlEl.key) }
                            )
                        }
                    }

                    if (selectedAudio != null) {
                        QuickActionPill(
                            icon = Icons.Filled.FastRewind,
                            label = "-1s",
                            onClick = { viewModel.shiftAudioTrackOnTimeline(selectedAudio.id, -1f) }
                        )
                        QuickActionPill(
                            icon = Icons.Filled.FastForward,
                            label = "+1s",
                            onClick = { viewModel.shiftAudioTrackOnTimeline(selectedAudio.id, 1f) }
                        )
                    }

                    QuickActionPill(
                        icon = Icons.Filled.Delete,
                        label = "Delete",
                        tint = CoralAlert,
                        onClick = { viewModel.deleteSelectedItem() }
                    )
                }
            }
        }

        // 4. Transport Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurfaceElevated)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val prev = uiState.scenes.lastOrNull { it.startSec < uiState.currentTimeSec - 0.5f }
                        if (prev != null) viewModel.seekTo(prev.startSec) else viewModel.seekTo(0f)
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous Scene", tint = TextSecondary)
                }

                IconButton(
                    onClick = { viewModel.jumpBy(-2f) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.FastRewind, contentDescription = "Back 2s", tint = TextPrimary)
                }

                Surface(
                    shape = CircleShape,
                    color = CyanPulse,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { viewModel.togglePlayPause() }
                        .testTag("play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                            tint = Color(0xFF04131E),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.jumpBy(2f) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.FastForward, contentDescription = "Forward 2s", tint = TextPrimary)
                }

                IconButton(
                    onClick = {
                        val next = uiState.scenes.firstOrNull { it.startSec > uiState.currentTimeSec + 0.3f }
                        if (next != null) viewModel.seekTo(next.startSec)
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next Scene", tint = TextSecondary)
                }
            }

            Text(
                text = "${formatTimecode(uiState.currentTimeSec)} / ${formatTimecode(project.totalDurationSec)}",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                modifier = Modifier.testTag("timecode_label")
            )
        }

        // 5. Multi-Track Scrubbable Timeline
        TimelinePanel(
            totalDurationSec = project.totalDurationSec,
            currentTimeSec = uiState.currentTimeSec,
            scenes = uiState.scenes,
            overlays = uiState.overlays,
            audioTracks = uiState.audioTracks,
            selectedOverlayId = uiState.selectedOverlayId,
            selectedAudioId = uiState.selectedAudioId,
            onSeekTo = { viewModel.seekTo(it) },
            onOpenSceneEditor = { viewModel.openSceneEditor(it) },
            onSelectOverlay = { viewModel.selectOverlay(it) },
            onShiftOverlay = { id, delta -> viewModel.shiftOverlayOnTimeline(id, delta) },
            onSelectAudio = { viewModel.selectAudioTrack(it) },
            onShiftAudio = { id, delta -> viewModel.shiftAudioTrackOnTimeline(id, delta) },
            onDeleteOverlay = { viewModel.deleteOverlay(it) },
            onDeleteAudio = { viewModel.deleteAudioTrack(it) }
        )

        // 6. Bottom Studio Tool Dock (with safe navigationBarsPadding)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurface)
                .navigationBarsPadding()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolDockButton(
                icon = Icons.Filled.TextFields,
                label = "টেক্সট",
                accent = CyanPulse,
                testTag = "tool_add_text"
            ) {
                viewModel.setActiveSheet(ActiveSheet.ADD_TEXT)
            }
            ToolDockButton(
                icon = Icons.Filled.Image,
                label = "ফটো",
                accent = EmeraldRender,
                testTag = "tool_add_photo"
            ) {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            ToolDockButton(
                icon = Icons.Filled.Movie,
                label = "ভিডিও",
                accent = VioletMotion,
                testTag = "tool_add_video"
            ) {
                videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            }
            ToolDockButton(
                icon = Icons.Filled.Audiotrack,
                label = "অডিও",
                accent = Color(0xFF34D399),
                testTag = "tool_add_audio"
            ) {
                audioPicker.launch(arrayOf("audio/*"))
            }
            ToolDockButton(
                icon = Icons.Filled.AutoAwesome,
                label = "গ্রাফিক্স",
                accent = AmberCut,
                testTag = "tool_add_graphic"
            ) {
                viewModel.setActiveSheet(ActiveSheet.ADD_GRAPHIC)
            }
            ToolDockButton(
                icon = Icons.Filled.Tune,
                label = "এলিমেন্ট (${uiState.detectedHtmlElements.size})",
                accent = Color(0xFFF472B6),
                testTag = "tool_html_elements"
            ) {
                viewModel.setActiveSheet(ActiveSheet.HTML_ELEMENTS)
            }
        }
    }

    // Active Bottom Sheets
    when (uiState.activeSheet) {
        ActiveSheet.ADD_TEXT -> {
            TextOverlaySheet(
                existingOverlay = null,
                totalDurationSec = project.totalDurationSec,
                onDismiss = { viewModel.setActiveSheet(ActiveSheet.NONE) },
                onConfirmNew = { text, tc, bg, pill, size, motion ->
                    viewModel.addTextOverlay(text, tc, bg, pill, size, motion)
                },
                onConfirmUpdate = {}
            )
        }

        ActiveSheet.EDIT_TEXT -> {
            val ov = uiState.overlays.firstOrNull { it.id == uiState.selectedOverlayId }
            if (ov != null) {
                TextOverlaySheet(
                    existingOverlay = ov,
                    totalDurationSec = project.totalDurationSec,
                    onDismiss = { viewModel.setActiveSheet(ActiveSheet.NONE) },
                    onConfirmNew = { _, _, _, _, _, _ -> },
                    onConfirmUpdate = { updated -> viewModel.updateOverlay(updated) },
                    onDelete = { id ->
                        viewModel.deleteOverlay(id)
                        viewModel.setActiveSheet(ActiveSheet.NONE)
                    }
                )
            }
        }

        ActiveSheet.EDIT_MEDIA_OVERLAY -> {
            val ov = uiState.overlays.firstOrNull { it.id == uiState.selectedOverlayId }
            if (ov != null) {
                MediaOverlayEditSheet(
                    overlay = ov,
                    totalDurationSec = project.totalDurationSec,
                    onDismiss = { viewModel.setActiveSheet(ActiveSheet.NONE) },
                    onSave = { updated -> viewModel.updateOverlay(updated) },
                    onDelete = { id ->
                        viewModel.deleteOverlay(id)
                        viewModel.setActiveSheet(ActiveSheet.NONE)
                    }
                )
            }
        }

        ActiveSheet.ADD_GRAPHIC -> {
            AddGraphicSheet(
                onDismiss = { viewModel.setActiveSheet(ActiveSheet.NONE) },
                onAddGraphic = { kind, label, accent, motion ->
                    viewModel.addGraphicOverlay(kind, label, accent, motion)
                }
            )
        }

        ActiveSheet.EDIT_SCENE -> {
            val scene = uiState.scenes.firstOrNull { it.id == uiState.selectedSceneId }
            if (scene != null) {
                EditSceneSheet(
                    scene = scene,
                    onDismiss = { viewModel.setActiveSheet(ActiveSheet.NONE) },
                    onSave = { updated -> viewModel.updateScene(updated) },
                    onToggleDelete = { id -> viewModel.toggleSceneDeleted(id) }
                )
            }
        }

        ActiveSheet.HTML_ELEMENTS -> {
            HtmlElementsInspectorSheet(
                detectedElements = uiState.detectedHtmlElements,
                overrides = uiState.htmlElementOverrides,
                initialSelectedKey = uiState.selectedHtmlElementKey,
                onDismiss = { viewModel.setActiveSheet(ActiveSheet.NONE) },
                onUpdateElement = { key, orig, customText, colorHex, scale, isDeleted ->
                    viewModel.updateHtmlElementOverride(
                        key = key,
                        originalText = orig,
                        customText = customText,
                        colorHex = colorHex,
                        scale = scale,
                        isDeleted = isDeleted
                    )
                },
                onRestoreElement = { key -> viewModel.restoreHtmlElement(key) },
                onResetPosition = { key -> viewModel.resetHtmlElementPosition(key) }
            )
        }

        ActiveSheet.EXPORT -> {
            ExportVideoSheet(
                project = project,
                selectedResolution = uiState.selectedResolution,
                selectedFps = uiState.selectedFps,
                exportState = uiState.exportState,
                onSelectResolution = { viewModel.selectExportResolution(it) },
                onSelectFps = { viewModel.selectExportFps(it) },
                onStartExport = { viewModel.startExport() },
                onCancelExport = { viewModel.cancelExport() },
                onResetExport = { viewModel.resetExportState() },
                onPlayExported = { uriStr ->
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(uriStr), "video/mp4")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    }
                },
                onShareExported = { uriStr ->
                    runCatching {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStr))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Share Exported MP4"))
                    }
                },
                onDismiss = { viewModel.setActiveSheet(ActiveSheet.NONE) }
            )
        }

        ActiveSheet.NONE -> Unit
    }
}

@Composable
private fun QuickActionPill(
    icon: ImageVector,
    label: String,
    tint: Color = TextPrimary,
    onClick: () -> Unit
) {
    Surface(
        color = StudioSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}

@Composable
private fun ToolDockButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = StudioSurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
        }
    }
}
