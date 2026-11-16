package com.example.model

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

enum class ExportResolution(val label: String, val shortSidePx: Int, val bitrateBps: Int) {
    R240P("240p", 240, 650_000),
    R360P("360p", 360, 1_100_000),
    R480P("480p", 480, 1_800_000),
    R720P("720p", 720, 3_500_000),
    R1080P("1080p", 1080, 6_500_000);

    fun computeDimensions(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> {
        val sw = sourceWidth.coerceAtLeast(1)
        val sh = sourceHeight.coerceAtLeast(1)
        return if (sw <= sh) {
            val w = alignToEven(shortSidePx)
            val h = alignToEven(((shortSidePx.toFloat() * sh) / sw).roundToInt())
            w to h
        } else {
            val h = alignToEven(shortSidePx)
            val w = alignToEven(((shortSidePx.toFloat() * sw) / sh).roundToInt())
            w to h
        }
    }

    private fun alignToEven(value: Int): Int {
        val v = value.coerceAtLeast(16)
        return if (v % 2 == 0) v else v + 1
    }
}

enum class MotionPreset(val displayName: String) {
    NONE("Static"),
    FADE_IN("Fade In"),
    SLIDE_UP("Slide Up"),
    POP_SCALE("Pop Bounce"),
    PULSE("Pulse"),
    DRIFT_ZOOM("Slow Zoom")
}

enum class OverlayType(val label: String) {
    TEXT("Text"),
    IMAGE("Photo"),
    VIDEO("Video"),
    GRAPHIC("Graphic")
}

enum class GraphicKind(val displayName: String) {
    LOWER_THIRD("News Lower Third"),
    ALERT_BADGE("Breaking Badge"),
    CALLOUT_ARROW("Pointer Arrow"),
    HIGHLIGHT_RING("Focus Ring"),
    PROGRESS_PILL("Info Pill")
}

data class HtmlSceneItem(
    val id: Int,
    val startSec: Float,
    val endSec: Float,
    val headline: String,
    val sub: String,
    val caption: String,
    val isDeleted: Boolean = false
)

enum class HtmlElementKind(val label: String) {
    TEXT("Text"),
    GRAPHIC("Graphic"),
    IMAGE("Image")
}

data class HtmlCanvasElement(
    val key: String,
    val kind: HtmlElementKind = HtmlElementKind.TEXT,
    val originalText: String,
    val displayText: String,
    val xNorm: Float,
    val yNorm: Float,
    val wNorm: Float,
    val hNorm: Float,
    val offsetXNorm: Float = 0f,
    val offsetYNorm: Float = 0f,
    val scale: Float = 1f,
    val colorHex: String? = null,
    val isDeleted: Boolean = false
)

data class HtmlElementOverride(
    val key: String,
    val kind: HtmlElementKind = HtmlElementKind.TEXT,
    val originalText: String,
    val customText: String? = null,
    val offsetXNorm: Float = 0f,
    val offsetYNorm: Float = 0f,
    val scale: Float = 1f,
    val colorHex: String? = null,
    val isDeleted: Boolean = false
)

data class OverlayElement(
    val id: String,
    val type: OverlayType,
    val name: String,
    val startSec: Float,
    val endSec: Float,
    val xNorm: Float = 0.5f,
    val yNorm: Float = 0.5f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val alpha: Float = 1f,
    val motion: MotionPreset = MotionPreset.FADE_IN,
    val text: String = "",
    val textColor: Long = 0xFFFFFFFF,
    val bgColor: Long = 0xCC0F172A,
    val hasBgPill: Boolean = true,
    val fontSizeSp: Float = 26f,
    val mediaUri: String? = null,
    val graphicKind: GraphicKind = GraphicKind.LOWER_THIRD
)

data class AudioTrackItem(
    val id: String,
    val title: String,
    val uri: String,
    val startSec: Float,
    val endSec: Float,
    val sourceDurationSec: Float,
    val trimStartSec: Float = 0f,
    val volume: Float = 0.85f,
    val waveform: List<Float> = emptyList()
)

data class ProjectConfig(
    val title: String,
    val sourceName: String,
    val rawHtml: String,
    val canvasWidth: Int = 1080,
    val canvasHeight: Int = 1920,
    val totalDurationSec: Float = 30f,
    val isCanvasDriven: Boolean = true
)

enum class ActiveSheet {
    NONE,
    ADD_TEXT,
    EDIT_TEXT,
    EDIT_MEDIA_OVERLAY,
    ADD_GRAPHIC,
    EDIT_SCENE,
    HTML_ELEMENTS,
    EXPORT
}

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Exporting(
        val progress: Float,
        val currentFrame: Int,
        val totalFrames: Int,
        val statusLabel: String
    ) : ExportUiState

    data class Success(
        val outputUriString: String,
        val fileName: String,
        val resolutionLabel: String,
        val dimensionsText: String
    ) : ExportUiState

    data class Error(val message: String) : ExportUiState
}

object StudioPalettes {
    val swatchColors = listOf(
        0xFFFFFFFFL,
        0xFFF8FAFC,
        0xFF38BDF8L,
        0xFFF59E0BL,
        0xFF10B981L,
        0xFFF43F5EL,
        0xFF818CF8L,
        0xFF0F172AL
    )

    fun toComposeColor(argb: Long): Color = Color(argb.toInt())
}
