package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ExportResolution
import com.example.model.ExportUiState
import com.example.model.GraphicKind
import com.example.model.HtmlCanvasElement
import com.example.model.HtmlElementKind
import com.example.model.HtmlElementOverride
import com.example.model.HtmlSceneItem
import com.example.model.MotionPreset
import com.example.model.OverlayElement
import com.example.model.OverlayType
import com.example.model.ProjectConfig
import com.example.model.StudioPalettes
import com.example.ui.theme.AmberCut
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldRender
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceCard
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TextOverlaySheet(
    existingOverlay: OverlayElement?,
    totalDurationSec: Float,
    onDismiss: () -> Unit,
    onConfirmNew: (String, Long, Long, Boolean, Float, MotionPreset) -> Unit,
    onConfirmUpdate: (OverlayElement) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember(existingOverlay) { mutableStateOf(existingOverlay?.text ?: "") }
    var textColor by remember(existingOverlay) { mutableLongStateOf(existingOverlay?.textColor ?: 0xFFFFFFFFL) }
    var bgColor by remember(existingOverlay) { mutableLongStateOf(existingOverlay?.bgColor ?: 0xCC0F172AL) }
    var hasBgPill by remember(existingOverlay) { mutableStateOf(existingOverlay?.hasBgPill ?: true) }
    var fontSize by remember(existingOverlay) { mutableFloatStateOf(existingOverlay?.fontSizeSp ?: 26f) }
    var motion by remember(existingOverlay) { mutableStateOf(existingOverlay?.motion ?: MotionPreset.FADE_IN) }
    var startSec by remember(existingOverlay) { mutableFloatStateOf(existingOverlay?.startSec ?: 0f) }
    var endSec by remember(existingOverlay) { mutableFloatStateOf(existingOverlay?.endSec ?: 5f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingOverlay == null) "নতুন টেক্সট যোগ করুন" else "টেক্সট এডিট করুন",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                if (existingOverlay != null && onDelete != null) {
                    IconButton(onClick = { onDelete(existingOverlay.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete Text", tint = CoralAlert)
                    }
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("টেক্সট লিখুন (বাংলা / English)") },
                placeholder = { Text("যেমন: বিশেষ প্রতিবেদন • ডামুড্যা") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("text_overlay_input"),
                maxLines = 3
            )

            Text("টেক্সট কালার", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StudioPalettes.swatchColors.forEach { argb ->
                    val selected = textColor == argb
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(StudioPalettes.toComposeColor(argb))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) CyanPulse else StudioBorder,
                                shape = CircleShape
                            )
                            .clickable { textColor = argb }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ব্যাকগ্রাউন্ড পিল (Background Box)", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Switch(checked = hasBgPill, onCheckedChange = { hasBgPill = it })
            }

            Column {
                Text("ফন্ট সাইজ: ${fontSize.toInt()}sp", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 14f..54f,
                    colors = SliderDefaults.colors(thumbColor = CyanPulse, activeTrackColor = CyanPulse)
                )
            }

            Text("মোশন অ্যানিমেশন", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MotionPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = motion == preset,
                        onClick = { motion = preset },
                        label = { Text(preset.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPulse.copy(alpha = 0.2f),
                            selectedLabelColor = CyanPulse
                        )
                    )
                }
            }

            if (existingOverlay != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "টাইমলাইন সময়: ${formatTimecode(startSec)} – ${formatTimecode(endSec)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberCut
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Start (${startSec.roundToInt()}s)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Slider(
                                value = startSec,
                                onValueChange = {
                                    startSec = it.coerceAtMost(endSec - 0.5f).coerceAtLeast(0f)
                                },
                                valueRange = 0f..totalDurationSec.coerceAtLeast(1f)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("End (${endSec.roundToInt()}s)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Slider(
                                value = endSec,
                                onValueChange = {
                                    endSec = it.coerceAtLeast(startSec + 0.5f).coerceAtMost(totalDurationSec)
                                },
                                valueRange = 0.5f..totalDurationSec.coerceAtLeast(1f)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val clean = text.ifBlank { "New Headline" }
                    if (existingOverlay == null) {
                        onConfirmNew(clean, textColor, bgColor, hasBgPill, fontSize, motion)
                    } else {
                        onConfirmUpdate(
                            existingOverlay.copy(
                                name = clean.take(18),
                                text = clean,
                                textColor = textColor,
                                bgColor = bgColor,
                                hasBgPill = hasBgPill,
                                fontSizeSp = fontSize,
                                motion = motion,
                                startSec = startSec,
                                endSec = endSec
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPulse, contentColor = Color(0xFF04131E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_text_overlay_button")
            ) {
                Text(if (existingOverlay == null) "টেক্সট যোগ করুন" else "পরিবর্তন সেভ করুন", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddGraphicSheet(
    onDismiss: () -> Unit,
    onAddGraphic: (GraphicKind, String, Long, MotionPreset) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedKind by remember { mutableStateOf(GraphicKind.LOWER_THIRD) }
    var label by remember { mutableStateOf("সর্বশেষ সংবাদ • সরাসরি প্রতিবেদন") }
    var accent by remember { mutableLongStateOf(0xFFF59E0BL) }
    var motion by remember { mutableStateOf(MotionPreset.SLIDE_UP) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("মোশন গ্রাফিক্স যোগ করুন", style = MaterialTheme.typography.titleLarge, color = TextPrimary)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GraphicKind.entries.forEach { kind ->
                    FilterChip(
                        selected = selectedKind == kind,
                        onClick = { selectedKind = kind },
                        label = { Text(kind.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberCut.copy(alpha = 0.22f),
                            selectedLabelColor = AmberCut
                        )
                    )
                }
            }

            if (selectedKind != GraphicKind.CALLOUT_ARROW && selectedKind != GraphicKind.HIGHLIGHT_RING) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("গ্রাফিক্স টেক্সট") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text("অ্যাকসেন্ট কালার", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0xFFF59E0BL, 0xFF38BDF8L, 0xFFEF4444L, 0xFF10B981L, 0xFF818CF8L).forEach { col ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(StudioPalettes.toComposeColor(col))
                            .border(
                                width = if (accent == col) 3.dp else 1.dp,
                                color = if (accent == col) Color.White else StudioBorder,
                                shape = CircleShape
                            )
                            .clickable { accent = col }
                    )
                }
            }

            Text("অ্যানিমেশন স্টাইল", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MotionPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = motion == preset,
                        onClick = { motion = preset },
                        label = { Text(preset.displayName) }
                    )
                }
            }

            Button(
                onClick = { onAddGraphic(selectedKind, label, accent, motion) },
                colors = ButtonDefaults.buttonColors(containerColor = AmberCut, contentColor = Color(0xFF1A0E00)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("টাইমলাইনে যোগ করুন", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSceneSheet(
    scene: HtmlSceneItem,
    onDismiss: () -> Unit,
    onSave: (HtmlSceneItem) -> Unit,
    onToggleDelete: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var headline by remember(scene) { mutableStateOf(scene.headline) }
    var sub by remember(scene) { mutableStateOf(scene.sub) }
    var caption by remember(scene) { mutableStateOf(scene.caption) }
    var startSec by remember(scene) { mutableFloatStateOf(scene.startSec) }
    var endSec by remember(scene) { mutableFloatStateOf(scene.endSec) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "দৃশ্য ০${scene.id + 1} এডিট করুন",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                OutlinedButton(
                    onClick = {
                        onToggleDelete(scene.id)
                        onDismiss()
                    },
                    border = BorderStroke(1.dp, if (scene.isDeleted) EmeraldRender else CoralAlert)
                ) {
                    Icon(
                        imageVector = if (scene.isDeleted) Icons.Filled.Restore else Icons.Filled.Delete,
                        contentDescription = null,
                        tint = if (scene.isDeleted) EmeraldRender else CoralAlert,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (scene.isDeleted) "Restore" else "Delete Scene",
                        color = if (scene.isDeleted) EmeraldRender else CoralAlert
                    )
                }
            }

            OutlinedTextField(
                value = headline,
                onValueChange = { headline = it },
                label = { Text("Headline") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = sub,
                onValueChange = { sub = it },
                label = { Text("Subtitle") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Voiceover / Caption") },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Timing: ${formatTimecode(startSec)} → ${formatTimecode(endSec)}",
                style = MaterialTheme.typography.labelMedium,
                color = CyanPulse
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Start (${startSec.roundToInt()}s)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Slider(
                        value = startSec,
                        onValueChange = { startSec = it.coerceAtMost(endSec - 1f).coerceAtLeast(0f) },
                        valueRange = 0f..endSec.coerceAtLeast(1f)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("End (${endSec.roundToInt()}s)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Slider(
                        value = endSec,
                        onValueChange = { endSec = it.coerceAtLeast(startSec + 1f) },
                        valueRange = (startSec + 1f)..(endSec + 20f)
                    )
                }
            }

            Button(
                onClick = {
                    onSave(
                        scene.copy(
                            headline = headline,
                            sub = sub,
                            caption = caption,
                            startSec = startSec,
                            endSec = endSec
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPulse, contentColor = Color(0xFF04131E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("দৃশ্য আপডেট করুন", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaOverlayEditSheet(
    overlay: OverlayElement,
    totalDurationSec: Float,
    onDismiss: () -> Unit,
    onSave: (OverlayElement) -> Unit,
    onDelete: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var scale by remember(overlay) { mutableFloatStateOf(overlay.scale) }
    var rotation by remember(overlay) { mutableFloatStateOf(overlay.rotationDeg) }
    var alpha by remember(overlay) { mutableFloatStateOf(overlay.alpha) }
    var motion by remember(overlay) { mutableStateOf(overlay.motion) }
    var startSec by remember(overlay) { mutableFloatStateOf(overlay.startSec) }
    var endSec by remember(overlay) { mutableFloatStateOf(overlay.endSec) }
    var labelText by remember(overlay) { mutableStateOf(overlay.text) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${overlay.type.label} এডিট: ${overlay.name}",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                IconButton(onClick = { onDelete(overlay.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = CoralAlert)
                }
            }

            if (overlay.type == OverlayType.GRAPHIC) {
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    label = { Text("গ্রাফিক্স টেক্সট") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column {
                Text("সাইজ (Scale): ${String.format("%.2f", scale)}x", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Slider(
                    value = scale,
                    onValueChange = { scale = it },
                    valueRange = 0.35f..2.6f,
                    colors = SliderDefaults.colors(thumbColor = CyanPulse, activeTrackColor = CyanPulse)
                )
            }

            Column {
                Text("ঘূর্ণন (Rotation): ${rotation.roundToInt()}°", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Slider(
                    value = rotation,
                    onValueChange = { rotation = it },
                    valueRange = -180f..180f
                )
            }

            Column {
                Text("স্বচ্ছতা (Opacity): ${(alpha * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Slider(
                    value = alpha,
                    onValueChange = { alpha = it },
                    valueRange = 0.15f..1f
                )
            }

            Text("মোশন অ্যানিমেশন", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MotionPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = motion == preset,
                        onClick = { motion = preset },
                        label = { Text(preset.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPulse.copy(alpha = 0.2f),
                            selectedLabelColor = CyanPulse
                        )
                    )
                }
            }

            Text(
                text = "টাইমলাইন সময়: ${formatTimecode(startSec)} – ${formatTimecode(endSec)}",
                style = MaterialTheme.typography.labelMedium,
                color = AmberCut
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Start (${startSec.roundToInt()}s)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Slider(
                        value = startSec,
                        onValueChange = { startSec = it.coerceAtMost(endSec - 0.5f).coerceAtLeast(0f) },
                        valueRange = 0f..totalDurationSec.coerceAtLeast(1f)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("End (${endSec.roundToInt()}s)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Slider(
                        value = endSec,
                        onValueChange = { endSec = it.coerceAtLeast(startSec + 0.5f).coerceAtMost(totalDurationSec) },
                        valueRange = 0.5f..totalDurationSec.coerceAtLeast(1f)
                    )
                }
            }

            Button(
                onClick = {
                    onSave(
                        overlay.copy(
                            scale = scale,
                            rotationDeg = rotation,
                            alpha = alpha,
                            motion = motion,
                            startSec = startSec,
                            endSec = endSec,
                            text = labelText
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPulse, contentColor = Color(0xFF04131E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("পরিবর্তন সেভ করুন", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlElementsInspectorSheet(
    detectedElements: List<HtmlCanvasElement>,
    overrides: Map<String, HtmlElementOverride>,
    initialSelectedKey: String?,
    onDismiss: () -> Unit,
    onUpdateElement: (key: String, orig: String, customText: String?, colorHex: String?, scale: Float?, isDeleted: Boolean) -> Unit,
    onRestoreElement: (String) -> Unit,
    onResetPosition: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingKey by remember(initialSelectedKey) { mutableStateOf(initialSelectedKey) }
    var filterTab by remember { mutableStateOf(0) } // 0=All, 1=Graphics/Pictures, 2=Text

    val mergedKeys = remember(detectedElements, overrides, filterTab) {
        val all = (detectedElements.map { it.key } + overrides.keys).distinct()
        all.filter { key ->
            val det = detectedElements.firstOrNull { it.key == key }
            val ov = overrides[key]
            val kind = ov?.kind ?: det?.kind ?: HtmlElementKind.TEXT
            when (filterTab) {
                1 -> kind == HtmlElementKind.GRAPHIC || kind == HtmlElementKind.IMAGE
                2 -> kind == HtmlElementKind.TEXT
                else -> true
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "HTML ছবি, গ্রাফিক্স ও টেক্সট এডিটর (${mergedKeys.size})",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Text(
                text = "ভিডিও ফুটেজের যেকোনো ছবি, ইলাস্ট্রেশন, কার্ড বা টেক্সট এখান থেকে (অথবা প্রিভিউতে ট্যাপ করে) এডিট, সাইজ পরিবর্তন বা ডিলিট করুন।",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("সব", "ছবি ও গ্রাফিক্স", "টেক্সট").forEachIndexed { idx, title ->
                    FilterChip(
                        selected = filterTab == idx,
                        onClick = { filterTab = idx },
                        label = { Text(title) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPulse.copy(alpha = 0.2f),
                            selectedLabelColor = CyanPulse
                        )
                    )
                }
            }

            if (mergedKeys.isEmpty()) {
                Surface(
                    color = StudioSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "এই ফ্রেমে কোনো এলিমেন্ট পাওয়া যায়নি। টাইমলাইন একটু এগিয়ে দেখুন বা নতুন ফটো/টেক্সট/গ্রাফিক্স যোগ করুন।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            mergedKeys.forEach { key ->
                val det = detectedElements.firstOrNull { it.key == key }
                val ov = overrides[key]
                val kind = ov?.kind ?: det?.kind ?: HtmlElementKind.TEXT
                val orig = ov?.originalText ?: det?.originalText ?: key
                val currentText = ov?.customText?.takeIf { it.isNotBlank() } ?: det?.displayText ?: orig
                val isDeleted = ov?.isDeleted == true
                val isExpanded = editingKey == key

                Surface(
                    color = if (isDeleted) Color(0xFF281218) else StudioSurfaceCard,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        when {
                            isDeleted -> CoralAlert.copy(alpha = 0.5f)
                            isExpanded -> CyanPulse
                            else -> StudioBorder
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = if (kind == HtmlElementKind.TEXT) CyanPulse.copy(alpha = 0.18f) else AmberCut.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = kind.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (kind == HtmlElementKind.TEXT) CyanPulse else AmberCut,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = currentText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isDeleted) TextSecondary else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { editingKey = if (isExpanded) null else key }
                            )
                            if (isDeleted) {
                                IconButton(onClick = { onRestoreElement(key) }) {
                                    Icon(Icons.Filled.Restore, contentDescription = "Restore", tint = EmeraldRender)
                                }
                            } else {
                                IconButton(onClick = { editingKey = if (isExpanded) null else key }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = CyanPulse)
                                }
                                IconButton(onClick = {
                                    onUpdateElement(key, orig, ov?.customText, ov?.colorHex, ov?.scale, true)
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = CoralAlert)
                                }
                            }
                        }

                        if (isExpanded && !isDeleted) {
                            var draftText by remember(key) { mutableStateOf(currentText) }
                            var draftColor by remember(key) { mutableStateOf(ov?.colorHex) }
                            var draftScale by remember(key) { mutableFloatStateOf(ov?.scale ?: 1f) }

                            if (kind == HtmlElementKind.TEXT) {
                                OutlinedTextField(
                                    value = draftText,
                                    onValueChange = { draftText = it },
                                    label = { Text("নতুন টেক্সট") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Column {
                                Text(
                                    text = "সাইজ (Scale): ${String.format("%.2f", draftScale)}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Slider(
                                    value = draftScale,
                                    onValueChange = { draftScale = it },
                                    valueRange = 0.4f..2.2f
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(null, "#FFFFFF", "#38BDF8", "#F59E0B", "#10B981", "#F43F5E").forEach { hex ->
                                    val c = if (hex == null) StudioSurfaceElevated else Color(android.graphics.Color.parseColor(hex))
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .border(
                                                width = if (draftColor == hex) 2.5.dp else 1.dp,
                                                color = if (draftColor == hex) CyanPulse else StudioBorder,
                                                shape = CircleShape
                                            )
                                            .clickable { draftColor = hex }
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                OutlinedButton(onClick = {
                                    draftScale = 1f
                                    onResetPosition(key)
                                }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reset Pos")
                                }
                            }

                            Button(
                                onClick = {
                                    onUpdateElement(
                                        key,
                                        orig,
                                        if (kind == HtmlElementKind.TEXT) draftText else null,
                                        draftColor,
                                        draftScale,
                                        false
                                    )
                                    editingKey = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanPulse, contentColor = Color(0xFF04131E)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("অ্যাপ্লাই করুন", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportVideoSheet(
    project: ProjectConfig,
    selectedResolution: ExportResolution,
    selectedFps: Int,
    exportState: ExportUiState,
    onSelectResolution: (ExportResolution) -> Unit,
    onSelectFps: (Int) -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit,
    onResetExport: () -> Unit,
    onPlayExported: (String) -> Unit,
    onShareExported: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            if (exportState !is ExportUiState.Exporting) onDismiss()
        },
        sheetState = sheetState,
        containerColor = StudioSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "ভিডিও এক্সপোর্ট ও সেভ (MP4)",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )

            when (exportState) {
                is ExportUiState.Idle -> {
                    Text(
                        text = "যেকোনো পরিবর্তন সহ অথবা সরাসরি আসল মোশন ভিডিও ফরম্যাটে এক্সপোর্ট করুন।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    Text("রেজোলিউশন সিলেক্ট করুন", style = MaterialTheme.typography.labelLarge, color = TextPrimary)

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ExportResolution.entries.forEach { res ->
                            val (w, h) = res.computeDimensions(project.canvasWidth, project.canvasHeight)
                            val isSel = selectedResolution == res
                            Surface(
                                color = if (isSel) CyanPulse.copy(alpha = 0.16f) else StudioSurfaceCard,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, if (isSel) CyanPulse else StudioBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSelectResolution(res) }
                                    .testTag("export_res_${res.label}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = res.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSel) CyanPulse else TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${w}×${h}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Text("ফ্রেম রেট (FPS) — দ্রুত সেভের জন্য 15 FPS বেছে নিন", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            15 to "15 FPS (Fast)",
                            24 to "24 FPS",
                            30 to "30 FPS"
                        ).forEach { (fps, label) ->
                            FilterChip(
                                selected = selectedFps == fps,
                                onClick = { onSelectFps(fps) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberCut.copy(alpha = 0.22f),
                                    selectedLabelColor = AmberCut
                                )
                            )
                        }
                    }

                    Button(
                        onClick = onStartExport,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldRender, contentColor = Color(0xFF041C12)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("start_export_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("MP4 এক্সপোর্ট ও গ্যালারিতে সেভ করুন", fontWeight = FontWeight.Bold)
                    }
                }

                is ExportUiState.Exporting -> {
                    Surface(
                        color = StudioSurfaceElevated,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("রেন্ডারিং চলছে...", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(
                                    text = "${(exportState.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CyanPulse
                                )
                            }
                            LinearProgressIndicator(
                                progress = { exportState.progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = CyanPulse,
                                trackColor = StudioSurfaceCard
                            )
                            Text(
                                text = exportState.statusLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onCancelExport,
                        border = BorderStroke(1.dp, CoralAlert),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("রেন্ডার বাতিল করুন", color = CoralAlert)
                    }
                }

                is ExportUiState.Success -> {
                    Surface(
                        color = Color(0xFF062E22),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, EmeraldRender),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Success",
                                tint = EmeraldRender,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "ভিডিও সেভ সম্পন্ন হয়েছে!",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "${exportState.fileName} (${exportState.resolutionLabel} • ${exportState.dimensionsText})",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldRender
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onPlayExported(exportState.outputUriString) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPulse, contentColor = Color(0xFF04131E)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Filled.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ভিডিও দেখুন", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { onShareExported(exportState.outputUriString) },
                            border = BorderStroke(1.dp, CyanPulse),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, tint = CyanPulse, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("শেয়ার", color = CyanPulse)
                        }
                    }

                    OutlinedButton(
                        onClick = onResetExport,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("আরেকটি রেজোলিউশনে এক্সপোর্ট করুন", color = TextSecondary)
                    }
                }

                is ExportUiState.Error -> {
                    Surface(
                        color = Color(0xFF331018),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CoralAlert),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = exportState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Button(onClick = onResetExport, modifier = Modifier.fillMaxWidth()) {
                        Text("আবার চেষ্টা করুন")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
