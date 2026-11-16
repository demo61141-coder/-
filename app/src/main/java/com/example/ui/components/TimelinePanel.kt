package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.model.AudioTrackItem
import com.example.model.HtmlSceneItem
import com.example.model.OverlayElement
import com.example.model.OverlayType
import com.example.ui.theme.AmberCut
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldRender
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceCard
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletMotion
import kotlin.math.roundToInt

@Composable
fun TimelinePanel(
    totalDurationSec: Float,
    currentTimeSec: Float,
    scenes: List<HtmlSceneItem>,
    overlays: List<OverlayElement>,
    audioTracks: List<AudioTrackItem>,
    selectedOverlayId: String?,
    selectedAudioId: String?,
    onSeekTo: (Float) -> Unit,
    onOpenSceneEditor: (Int) -> Unit,
    onSelectOverlay: (String) -> Unit,
    onShiftOverlay: (String, Float) -> Unit,
    onSelectAudio: (String) -> Unit,
    onShiftAudio: (String, Float) -> Unit,
    onDeleteOverlay: (String) -> Unit,
    onDeleteAudio: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pxPerSecDp: Dp = 24.dp
    val density = LocalDensity.current
    val pxPerSec = with(density) { pxPerSecDp.toPx() }
    val timelineWidthDp = (totalDurationSec.coerceAtLeast(10f) * pxPerSecDp.value).dp + 48.dp
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioSurface)
            .border(width = 1.dp, color = StudioBorder)
    ) {
        // Track legend bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurfaceElevated)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TIMELINE • ক্লিপ টেনে আগে-পিছে নিন",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = "${overlays.size} Overlays • ${audioTracks.size} Audio",
                style = MaterialTheme.typography.labelSmall,
                color = CyanPulse
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Box(modifier = Modifier.width(timelineWidthDp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 1. Scrubbable Ruler
                    TimeRulerBar(
                        totalDurationSec = totalDurationSec,
                        pxPerSecDp = pxPerSecDp,
                        onSeekTo = onSeekTo
                    )

                    // 2. HTML Scenes Track
                    if (scenes.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            scenes.forEach { scene ->
                                val leftDp = (scene.startSec * pxPerSecDp.value).dp
                                val widthDp = ((scene.endSec - scene.startSec).coerceAtLeast(1f) * pxPerSecDp.value).dp
                                val isActive = currentTimeSec >= scene.startSec && currentTimeSec < scene.endSec
                                val bgColor = when {
                                    scene.isDeleted -> Color(0xFF2B131A)
                                    isActive -> Color(0xFF1E3A5F)
                                    else -> StudioSurfaceCard
                                }
                                val borderColor = when {
                                    scene.isDeleted -> CoralAlert.copy(alpha = 0.6f)
                                    isActive -> CyanPulse
                                    else -> StudioBorder
                                }

                                Surface(
                                    color = bgColor,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, borderColor),
                                    modifier = Modifier
                                        .offset(x = leftDp)
                                        .width(widthDp)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpenSceneEditor(scene.id) }
                                        .testTag("timeline_scene_${scene.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (scene.isDeleted) {
                                            Icon(
                                                imageVector = Icons.Filled.VisibilityOff,
                                                contentDescription = "Deleted scene",
                                                tint = CoralAlert,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        Text(
                                            text = "${scene.id + 1}. ${scene.headline}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (scene.isDeleted) TextMuted else TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Edit Scene",
                                            tint = CyanPulse,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Base HTML Track bar when no explicit SCENES array
                        Surface(
                            color = StudioSurfaceCard,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CyanPulse.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .width((totalDurationSec * pxPerSecDp.value).dp)
                                .height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "HTML Motion Canvas (${totalDurationSec.toInt()}s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    // 3. Overlays Track (Text / Photo / Video / Motion Graphics) - Draggable horizontally!
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        if (overlays.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StudioSurfaceElevated.copy(alpha = 0.5f))
                                    .border(1.dp, StudioBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "  + নিচ থেকে টেক্সট / ফটো / ভিডিও / গ্রাফিক্স যোগ করুন",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        } else {
                            overlays.forEach { ov ->
                                val leftDp = (ov.startSec * pxPerSecDp.value).dp
                                val widthDp = ((ov.endSec - ov.startSec).coerceAtLeast(1f) * pxPerSecDp.value).dp
                                val isSel = ov.id == selectedOverlayId
                                val chipColor = when (ov.type) {
                                    OverlayType.TEXT -> Color(0xFF1D3557)
                                    OverlayType.IMAGE -> Color(0xFF164E63)
                                    OverlayType.VIDEO -> Color(0xFF312E81)
                                    OverlayType.GRAPHIC -> Color(0xFF4C1D95)
                                }
                                val accent = when (ov.type) {
                                    OverlayType.TEXT -> CyanPulse
                                    OverlayType.IMAGE -> EmeraldRender
                                    OverlayType.VIDEO -> VioletMotion
                                    OverlayType.GRAPHIC -> AmberCut
                                }
                                val icon = when (ov.type) {
                                    OverlayType.TEXT -> Icons.Filled.TextFields
                                    OverlayType.IMAGE -> Icons.Filled.Image
                                    OverlayType.VIDEO -> Icons.Filled.Movie
                                    OverlayType.GRAPHIC -> Icons.Filled.Layers
                                }

                                var dragAccPx by remember(ov.id) { mutableFloatStateOf(0f) }

                                Surface(
                                    color = chipColor,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) AmberCut else accent),
                                    modifier = Modifier
                                        .offset(x = leftDp)
                                        .width(widthDp)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSelectOverlay(ov.id) }
                                        .pointerInput(ov.id) {
                                            detectHorizontalDragGestures(
                                                onDragStart = {
                                                    dragAccPx = 0f
                                                    onSelectOverlay(ov.id)
                                                },
                                                onHorizontalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragAccPx += dragAmount
                                                    val deltaSec = dragAccPx / pxPerSec
                                                    if (kotlin.math.abs(deltaSec) >= 0.15f) {
                                                        onShiftOverlay(ov.id, deltaSec)
                                                        dragAccPx = 0f
                                                    }
                                                }
                                            )
                                        }
                                        .testTag("timeline_overlay_${ov.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = ov.type.label,
                                            tint = accent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = ov.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSel) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Delete Overlay",
                                                tint = CoralAlert,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { onDeleteOverlay(ov.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Audio Track (with waveform & horizontal drag)
                    if (audioTracks.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            audioTracks.forEach { tr ->
                                val leftDp = (tr.startSec * pxPerSecDp.value).dp
                                val widthDp = ((tr.endSec - tr.startSec).coerceAtLeast(1f) * pxPerSecDp.value).dp
                                val isSel = tr.id == selectedAudioId
                                var dragAccPx by remember(tr.id) { mutableFloatStateOf(0f) }

                                Surface(
                                    color = Color(0xFF064E3B),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) AmberCut else EmeraldRender),
                                    modifier = Modifier
                                        .offset(x = leftDp)
                                        .width(widthDp)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSelectAudio(tr.id) }
                                        .pointerInput(tr.id) {
                                            detectHorizontalDragGestures(
                                                onDragStart = {
                                                    dragAccPx = 0f
                                                    onSelectAudio(tr.id)
                                                },
                                                onHorizontalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragAccPx += dragAmount
                                                    val deltaSec = dragAccPx / pxPerSec
                                                    if (kotlin.math.abs(deltaSec) >= 0.15f) {
                                                        onShiftAudio(tr.id, deltaSec)
                                                        dragAccPx = 0f
                                                    }
                                                }
                                            )
                                        }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        // Waveform background
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val bars = tr.waveform.ifEmpty { listOf(0.4f, 0.7f, 0.5f, 0.8f) }
                                            val barW = (size.width / bars.size).coerceAtLeast(3f)
                                            bars.forEachIndexed { idx, amp ->
                                                val bh = size.height * 0.7f * amp
                                                val bx = idx * barW
                                                drawLine(
                                                    color = EmeraldRender.copy(alpha = 0.45f),
                                                    start = Offset(bx, (size.height - bh) / 2f),
                                                    end = Offset(bx, (size.height + bh) / 2f),
                                                    strokeWidth = (barW * 0.55f).coerceAtMost(4f)
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Audiotrack,
                                                contentDescription = "Audio",
                                                tint = Color(0xFF6EE7B7),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = tr.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isSel) {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = "Delete Audio",
                                                    tint = CoralAlert,
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable { onDeleteAudio(tr.id) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Vertical Playhead Needle across all tracks
                val playheadOffsetPx = (currentTimeSec * pxPerSec).roundToInt()
                Box(
                    modifier = Modifier
                        .offset { IntOffset(playheadOffsetPx - 1, 0) }
                        .width(2.5.dp)
                        .height(if (audioTracks.isNotEmpty()) 144.dp else 104.dp)
                        .background(AmberCut)
                )
            }
        }
    }
}

@Composable
private fun TimeRulerBar(
    totalDurationSec: Float,
    pxPerSecDp: Dp,
    onSeekTo: (Float) -> Unit
) {
    val density = LocalDensity.current
    val pxPerSec = with(density) { pxPerSecDp.toPx() }
    val widthDp = (totalDurationSec.coerceAtLeast(10f) * pxPerSecDp.value).dp

    Box(
        modifier = Modifier
            .width(widthDp)
            .height(22.dp)
            .pointerInput(totalDurationSec) {
                detectTapGestures { offset ->
                    val t = (offset.x / pxPerSec).coerceIn(0f, totalDurationSec)
                    onSeekTo(t)
                }
            }
            .pointerInput(totalDurationSec) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val t = (change.position.x / pxPerSec).coerceIn(0f, totalDurationSec)
                    onSeekTo(t)
                }
            }
            .testTag("timeline_ruler")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalSecs = totalDurationSec.toInt().coerceAtLeast(1)
            for (s in 0..totalSecs) {
                val x = s * pxPerSec
                val major = s % 5 == 0
                val h = if (major) size.height * 0.65f else size.height * 0.32f
                drawLine(
                    color = if (major) Color(0xFF94A3B8) else Color(0xFF475569),
                    start = Offset(x, size.height - h),
                    end = Offset(x, size.height),
                    strokeWidth = if (major) 2f else 1f
                )
            }
        }
        val totalSecs = totalDurationSec.toInt()
        for (s in 0..totalSecs step 5) {
            Text(
                text = formatShortTime(s),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.offset(x = (s * pxPerSecDp.value + 3).dp, y = 0.dp)
            )
        }
    }
}

fun formatShortTime(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

fun formatTimecode(sec: Float): String {
    val safe = sec.coerceAtLeast(0f)
    val mins = (safe / 60f).toInt()
    val secs = (safe % 60f).toInt()
    val tenths = ((safe * 10f).toInt()) % 10
    return "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}.$tenths"
}
