package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.HtmlMotionParser
import com.example.engine.MediaAudioPlayer
import com.example.engine.MediaMetadataHelper
import com.example.engine.OverlayCanvasRenderer
import com.example.engine.VideoExportEngine
import com.example.engine.WebViewCanvasController
import com.example.model.ActiveSheet
import com.example.model.AudioTrackItem
import com.example.model.ExportResolution
import com.example.model.ExportUiState
import com.example.model.GraphicKind
import com.example.model.HtmlCanvasElement
import com.example.model.HtmlElementOverride
import com.example.model.HtmlSceneItem
import com.example.model.MotionPreset
import com.example.model.OverlayElement
import com.example.model.OverlayType
import com.example.model.ProjectConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class StudioUiState(
    val project: ProjectConfig? = null,
    val scenes: List<HtmlSceneItem> = emptyList(),
    val detectedHtmlElements: List<HtmlCanvasElement> = emptyList(),
    val htmlElementOverrides: Map<String, HtmlElementOverride> = emptyMap(),
    val overlays: List<OverlayElement> = emptyList(),
    val audioTracks: List<AudioTrackItem> = emptyList(),
    val currentTimeSec: Float = 0f,
    val isPlaying: Boolean = false,
    val isMuted: Boolean = false,
    val selectedOverlayId: String? = null,
    val selectedHtmlElementKey: String? = null,
    val selectedSceneId: Int? = null,
    val selectedAudioId: String? = null,
    val activeSheet: ActiveSheet = ActiveSheet.NONE,
    val selectedResolution: ExportResolution = ExportResolution.R720P,
    val selectedFps: Int = 24,
    val showTapGuides: Boolean = false,
    val exportState: ExportUiState = ExportUiState.Idle,
    val statusBanner: String? = null
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    val webController = WebViewCanvasController(appContext)
    val overlayRenderer = OverlayCanvasRenderer(appContext)
    private val audioPlayer = MediaAudioPlayer(appContext)
    private val exportEngine = VideoExportEngine(appContext)

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var exportJob: Job? = null

    fun loadHtmlFromUri(uri: Uri) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val name = MediaMetadataHelper.getFileName(appContext, uri, "motion_story.html")
                    val text = appContext.contentResolver.openInputStream(uri)?.bufferedReader()
                        ?.use { it.readText() } ?: ""
                    name to text
                }.getOrNull()
            }
            if (loaded == null || loaded.second.isBlank()) {
                _uiState.update { it.copy(statusBanner = "Could not read HTML file.") }
                return@launch
            }
            openParsedHtml(loaded.second, loaded.first)
        }
    }

    fun loadSampleStoryHtml() {
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.assets.open("sample_story.html").bufferedReader().use { it.readText() }
                }.getOrNull()
            }
            if (raw.isNullOrBlank()) {
                _uiState.update { it.copy(statusBanner = "Sample story asset unavailable.") }
                return@launch
            }
            openParsedHtml(raw, "reporter_hasan_story.html")
        }
    }

    private fun openParsedHtml(rawHtml: String, fileName: String) {
        stopPlayback()
        val (config, scenes) = HtmlMotionParser.parse(rawHtml, fileName)
        _uiState.value = StudioUiState(
            project = config,
            scenes = scenes,
            currentTimeSec = 0f,
            isPlaying = false
        )
        webController.loadHtml(rawHtml)
    }

    fun onWebViewReady() {
        val state = _uiState.value
        val project = state.project ?: return
        webController.syncScenes(state.scenes, project.totalDurationSec)
        webController.syncElementOverrides(state.htmlElementOverrides)
        seekTo(state.currentTimeSec)
    }

    fun closeProject() {
        stopPlayback()
        audioPlayer.pauseAll()
        _uiState.value = StudioUiState()
    }

    fun togglePlayPause() {
        val state = _uiState.value
        val project = state.project ?: return
        if (state.isPlaying) {
            stopPlayback()
        } else {
            val startAt = if (state.currentTimeSec >= project.totalDurationSec - 0.05f) 0f else state.currentTimeSec
            _uiState.update { it.copy(isPlaying = true, currentTimeSec = startAt) }
            startPlaybackLoop()
        }
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var lastNano = System.nanoTime()
            while (isActive && _uiState.value.isPlaying) {
                delay(33L)
                val nowNano = System.nanoTime()
                val dt = ((nowNano - lastNano) / 1_000_000_000f).coerceIn(0.01f, 0.1f)
                lastNano = nowNano

                val cur = _uiState.value
                val maxDur = cur.project?.totalDurationSec ?: 30f
                val nextTime = cur.currentTimeSec + dt
                if (nextTime >= maxDur) {
                    _uiState.update { it.copy(currentTimeSec = maxDur, isPlaying = false) }
                    seekWebAndAudio(maxDur, isPlaying = false)
                    break
                } else {
                    _uiState.update { it.copy(currentTimeSec = nextTime) }
                    seekWebAndAudio(nextTime, isPlaying = true)
                }
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlaying = false) }
        audioPlayer.pauseAll()
    }

    fun seekTo(timeSec: Float) {
        val maxDur = _uiState.value.project?.totalDurationSec ?: 30f
        val clamped = timeSec.coerceIn(0f, maxDur)
        _uiState.update { it.copy(currentTimeSec = clamped) }
        seekWebAndAudio(clamped, isPlaying = _uiState.value.isPlaying)
    }

    fun jumpBy(deltaSec: Float) {
        seekTo(_uiState.value.currentTimeSec + deltaSec)
    }

    private fun seekWebAndAudio(timeSec: Float, isPlaying: Boolean) {
        webController.seekTo(timeSec) { elements ->
            _uiState.update { it.copy(detectedHtmlElements = elements) }
        }
        val state = _uiState.value
        audioPlayer.sync(state.audioTracks, timeSec, isPlaying, state.isMuted)
    }

    fun toggleMute() {
        val next = !_uiState.value.isMuted
        _uiState.update { it.copy(isMuted = next) }
        val s = _uiState.value
        audioPlayer.sync(s.audioTracks, s.currentTimeSec, s.isPlaying, next)
    }

    // --- Selection & Interactive Canvas Hit-Testing ---

    fun selectOverlay(id: String?) {
        _uiState.update {
            it.copy(
                selectedOverlayId = id,
                selectedHtmlElementKey = if (id != null) null else it.selectedHtmlElementKey,
                selectedAudioId = null
            )
        }
    }

    fun selectHtmlElement(key: String?) {
        _uiState.update {
            it.copy(
                selectedHtmlElementKey = key,
                selectedOverlayId = if (key != null) null else it.selectedOverlayId,
                selectedAudioId = null
            )
        }
    }

    fun selectAudioTrack(id: String?) {
        _uiState.update {
            it.copy(
                selectedAudioId = id,
                selectedOverlayId = null,
                selectedHtmlElementKey = null
            )
        }
    }

    fun handlePreviewTap(normX: Float, normY: Float) {
        val state = _uiState.value
        val t = state.currentTimeSec

        // 1. Check user-added overlays (top-most first)
        val hitOverlay = state.overlays.asReversed().firstOrNull { ov ->
            t in ov.startSec..ov.endSec &&
                kotlin.math.abs(normX - ov.xNorm) < 0.22f * ov.scale &&
                kotlin.math.abs(normY - ov.yNorm) < 0.12f * ov.scale
        }
        if (hitOverlay != null) {
            selectOverlay(hitOverlay.id)
            return
        }

        // 2. Check detected HTML Canvas elements at current playhead (prefer tightest/smallest hit)
        val matchingHtml = state.detectedHtmlElements.filter { el ->
            val ov = state.htmlElementOverrides[el.key]
            if (ov?.isDeleted == true) return@filter false
            val dx = ov?.offsetXNorm ?: 0f
            val dy = ov?.offsetYNorm ?: 0f
            val left = (el.xNorm + dx - 0.025f).coerceIn(0f, 1f)
            val right = (el.xNorm + el.wNorm + dx + 0.025f).coerceIn(0f, 1f)
            val top = (el.yNorm + dy - 0.02f).coerceIn(0f, 1f)
            val bottom = (el.yNorm + el.hNorm + dy + 0.02f).coerceIn(0f, 1f)
            normX in left..right && normY in top..bottom
        }

        val hitHtml = matchingHtml.minByOrNull { el ->
            val area = el.wNorm * el.hNorm
            // Slight priority for TEXT when areas are similar, otherwise smallest box wins
            if (el.kind == com.example.model.HtmlElementKind.TEXT) area * 0.65f else area
        }

        if (hitHtml != null) {
            selectHtmlElement(hitHtml.key)
        } else {
            _uiState.update { it.copy(selectedOverlayId = null, selectedHtmlElementKey = null) }
        }
    }

    fun handlePreviewDoubleTap(normX: Float, normY: Float) {
        handlePreviewTap(normX, normY)
        val state = _uiState.value
        val ovId = state.selectedOverlayId
        if (ovId != null) {
            val ov = state.overlays.firstOrNull { it.id == ovId } ?: return
            setActiveSheet(
                if (ov.type == OverlayType.TEXT) ActiveSheet.EDIT_TEXT
                else ActiveSheet.EDIT_MEDIA_OVERLAY
            )
            return
        }
        if (state.selectedHtmlElementKey != null) {
            setActiveSheet(ActiveSheet.HTML_ELEMENTS)
        }
    }

    fun toggleTapGuides() {
        _uiState.update { it.copy(showTapGuides = !it.showTapGuides) }
    }

    fun moveSelectedElementBy(deltaXNorm: Float, deltaYNorm: Float) {
        val state = _uiState.value
        val selOverlay = state.selectedOverlayId
        if (selOverlay != null) {
            _uiState.update { cur ->
                cur.copy(
                    overlays = cur.overlays.map { ov ->
                        if (ov.id == selOverlay) {
                            ov.copy(
                                xNorm = (ov.xNorm + deltaXNorm).coerceIn(0.05f, 0.95f),
                                yNorm = (ov.yNorm + deltaYNorm).coerceIn(0.05f, 0.95f)
                            )
                        } else ov
                    }
                )
            }
            return
        }

        val selHtmlKey = state.selectedHtmlElementKey
        if (selHtmlKey != null) {
            val baseEl = state.detectedHtmlElements.firstOrNull { it.key == selHtmlKey }
            val existing = state.htmlElementOverrides[selHtmlKey] ?: HtmlElementOverride(
                key = selHtmlKey,
                kind = baseEl?.kind ?: com.example.model.HtmlElementKind.TEXT,
                originalText = baseEl?.originalText ?: selHtmlKey
            )
            val updated = existing.copy(
                offsetXNorm = (existing.offsetXNorm + deltaXNorm).coerceIn(-0.8f, 0.8f),
                offsetYNorm = (existing.offsetYNorm + deltaYNorm).coerceIn(-0.8f, 0.8f)
            )
            val newMap = state.htmlElementOverrides + (selHtmlKey to updated)
            _uiState.update { it.copy(htmlElementOverrides = newMap) }
            webController.syncElementOverrides(newMap)
        }
    }

    fun scaleSelectedElement(scaleDelta: Float) {
        val state = _uiState.value
        val selOverlay = state.selectedOverlayId
        if (selOverlay != null) {
            _uiState.update { cur ->
                cur.copy(
                    overlays = cur.overlays.map { ov ->
                        if (ov.id == selOverlay) {
                            ov.copy(scale = (ov.scale + scaleDelta).coerceIn(0.35f, 3.0f))
                        } else ov
                    }
                )
            }
            return
        }

        val selHtmlKey = state.selectedHtmlElementKey ?: return
        val baseEl = state.detectedHtmlElements.firstOrNull { it.key == selHtmlKey }
        val existing = state.htmlElementOverrides[selHtmlKey] ?: HtmlElementOverride(
            key = selHtmlKey,
            originalText = baseEl?.originalText ?: selHtmlKey
        )
        val updated = existing.copy(scale = (existing.scale + scaleDelta).coerceIn(0.4f, 2.5f))
        val newMap = state.htmlElementOverrides + (selHtmlKey to updated)
        _uiState.update { it.copy(htmlElementOverrides = newMap) }
        webController.syncElementOverrides(newMap)
    }

    fun deleteSelectedItem() {
        val state = _uiState.value
        state.selectedOverlayId?.let { id ->
            deleteOverlay(id)
            return
        }
        state.selectedHtmlElementKey?.let { key ->
            deleteHtmlElement(key)
            return
        }
        state.selectedAudioId?.let { id ->
            deleteAudioTrack(id)
        }
    }

    // --- HTML Elements & Scenes Editing ---

    fun updateHtmlElementOverride(
        key: String,
        originalText: String,
        customText: String?,
        colorHex: String?,
        scale: Float? = null,
        isDeleted: Boolean = false
    ) {
        val state = _uiState.value
        val baseEl = state.detectedHtmlElements.firstOrNull { it.key == key }
        val cur = state.htmlElementOverrides[key] ?: HtmlElementOverride(
            key = key,
            kind = baseEl?.kind ?: com.example.model.HtmlElementKind.TEXT,
            originalText = originalText
        )
        val updated = cur.copy(
            customText = customText,
            colorHex = colorHex,
            scale = scale ?: cur.scale,
            isDeleted = isDeleted
        )
        val newMap = state.htmlElementOverrides + (key to updated)
        _uiState.update {
            it.copy(
                htmlElementOverrides = newMap,
                selectedHtmlElementKey = if (isDeleted) null else key
            )
        }
        webController.syncElementOverrides(newMap)
    }

    fun deleteHtmlElement(key: String) {
        val base = _uiState.value.detectedHtmlElements.firstOrNull { it.key == key }
        val existing = _uiState.value.htmlElementOverrides[key]
        updateHtmlElementOverride(
            key = key,
            originalText = existing?.originalText ?: base?.originalText ?: key,
            customText = existing?.customText,
            colorHex = existing?.colorHex,
            isDeleted = true
        )
    }

    fun deleteHtmlArea(key: String) {
        val state = _uiState.value
        val target = state.detectedHtmlElements.firstOrNull { it.key == key }
        if (target == null) {
            deleteHtmlElement(key)
            return
        }
        val boxOv = state.htmlElementOverrides[key]
        val bx0 = target.xNorm + (boxOv?.offsetXNorm ?: 0f) - 0.01f
        val by0 = target.yNorm + (boxOv?.offsetYNorm ?: 0f) - 0.01f
        val bx1 = bx0 + target.wNorm + 0.02f
        val by1 = by0 + target.hNorm + 0.02f

        val newMap = state.htmlElementOverrides.toMutableMap()
        state.detectedHtmlElements.forEach { el ->
            val ov = newMap[el.key]
            val cx = el.xNorm + (ov?.offsetXNorm ?: 0f) + el.wNorm * 0.5f
            val cy = el.yNorm + (ov?.offsetYNorm ?: 0f) + el.hNorm * 0.5f
            val inside = (el.key == key) || (cx in bx0..bx1 && cy in by0..by1 && (el.wNorm * el.hNorm) <= (target.wNorm * target.hNorm * 1.05f))
            if (inside) {
                newMap[el.key] = (ov ?: HtmlElementOverride(
                    key = el.key,
                    kind = el.kind,
                    originalText = el.originalText
                )).copy(isDeleted = true)
            }
        }
        _uiState.update {
            it.copy(
                htmlElementOverrides = newMap,
                selectedHtmlElementKey = null
            )
        }
        webController.syncElementOverrides(newMap)
    }

    fun restoreHtmlElement(key: String) {
        val existing = _uiState.value.htmlElementOverrides[key] ?: return
        val updated = existing.copy(isDeleted = false)
        val newMap = _uiState.value.htmlElementOverrides + (key to updated)
        _uiState.update { it.copy(htmlElementOverrides = newMap) }
        webController.syncElementOverrides(newMap)
    }

    fun resetHtmlElementPosition(key: String) {
        val existing = _uiState.value.htmlElementOverrides[key] ?: return
        val updated = existing.copy(offsetXNorm = 0f, offsetYNorm = 0f, scale = 1f)
        val newMap = _uiState.value.htmlElementOverrides + (key to updated)
        _uiState.update { it.copy(htmlElementOverrides = newMap) }
        webController.syncElementOverrides(newMap)
    }

    fun openSceneEditor(sceneId: Int) {
        val scene = _uiState.value.scenes.firstOrNull { it.id == sceneId } ?: return
        seekTo(scene.startSec + 0.2f)
        _uiState.update {
            it.copy(selectedSceneId = sceneId, activeSheet = ActiveSheet.EDIT_SCENE)
        }
    }

    fun updateScene(updated: HtmlSceneItem) {
        val scenes = _uiState.value.scenes.map { if (it.id == updated.id) updated else it }
        val maxEnd = scenes.maxOfOrNull { it.endSec } ?: _uiState.value.project?.totalDurationSec ?: 30f
        val updatedProject = _uiState.value.project?.copy(totalDurationSec = maxEnd.coerceAtLeast(3f))
        _uiState.update { it.copy(scenes = scenes, project = updatedProject, activeSheet = ActiveSheet.NONE) }
        webController.syncScenes(scenes, updatedProject?.totalDurationSec ?: maxEnd)
    }

    fun toggleSceneDeleted(sceneId: Int) {
        val scenes = _uiState.value.scenes.map {
            if (it.id == sceneId) it.copy(isDeleted = !it.isDeleted) else it
        }
        val dur = _uiState.value.project?.totalDurationSec ?: 30f
        _uiState.update { it.copy(scenes = scenes) }
        webController.syncScenes(scenes, dur)
    }

    // --- Overlays: Add, Edit, Move on Timeline, Delete ---

    fun addTextOverlay(
        text: String,
        textColor: Long,
        bgColor: Long,
        hasBgPill: Boolean,
        fontSizeSp: Float,
        motion: MotionPreset
    ) {
        val state = _uiState.value
        val dur = state.project?.totalDurationSec ?: 30f
        val start = state.currentTimeSec.coerceIn(0f, (dur - 1f).coerceAtLeast(0f))
        val end = (start + 5f).coerceAtMost(dur)
        val item = OverlayElement(
            id = UUID.randomUUID().toString(),
            type = OverlayType.TEXT,
            name = text.take(18).ifBlank { "Text" },
            startSec = start,
            endSec = end,
            xNorm = 0.5f,
            yNorm = 0.45f,
            motion = motion,
            text = text,
            textColor = textColor,
            bgColor = bgColor,
            hasBgPill = hasBgPill,
            fontSizeSp = fontSizeSp
        )
        _uiState.update {
            it.copy(
                overlays = it.overlays + item,
                selectedOverlayId = item.id,
                activeSheet = ActiveSheet.NONE
            )
        }
    }

    fun updateOverlay(updated: OverlayElement) {
        _uiState.update { cur ->
            cur.copy(
                overlays = cur.overlays.map { if (it.id == updated.id) updated else it },
                activeSheet = ActiveSheet.NONE
            )
        }
    }

    fun addPhotoOverlay(uri: Uri) {
        val state = _uiState.value
        val dur = state.project?.totalDurationSec ?: 30f
        val start = state.currentTimeSec.coerceIn(0f, (dur - 1f).coerceAtLeast(0f))
        val end = (start + 6f).coerceAtMost(dur)
        val name = MediaMetadataHelper.getFileName(appContext, uri, "Photo")
        val item = OverlayElement(
            id = UUID.randomUUID().toString(),
            type = OverlayType.IMAGE,
            name = name.take(18),
            startSec = start,
            endSec = end,
            xNorm = 0.5f,
            yNorm = 0.42f,
            motion = MotionPreset.POP_SCALE,
            mediaUri = uri.toString()
        )
        _uiState.update {
            it.copy(overlays = it.overlays + item, selectedOverlayId = item.id)
        }
    }

    fun addVideoOverlay(uri: Uri) {
        viewModelScope.launch {
            val (name, mediaDur) = withContext(Dispatchers.IO) {
                MediaMetadataHelper.getFileName(appContext, uri, "Video Clip") to
                    MediaMetadataHelper.getMediaDurationSec(appContext, uri, 6f)
            }
            val state = _uiState.value
            val projDur = state.project?.totalDurationSec ?: 30f
            val start = state.currentTimeSec.coerceIn(0f, (projDur - 1f).coerceAtLeast(0f))
            val end = (start + mediaDur.coerceAtMost(15f)).coerceAtMost(projDur)
            val item = OverlayElement(
                id = UUID.randomUUID().toString(),
                type = OverlayType.VIDEO,
                name = name.take(18),
                startSec = start,
                endSec = end,
                xNorm = 0.5f,
                yNorm = 0.42f,
                motion = MotionPreset.FADE_IN,
                mediaUri = uri.toString()
            )
            _uiState.update {
                it.copy(overlays = it.overlays + item, selectedOverlayId = item.id)
            }
        }
    }

    fun addGraphicOverlay(kind: GraphicKind, label: String, accentColor: Long, motion: MotionPreset) {
        val state = _uiState.value
        val dur = state.project?.totalDurationSec ?: 30f
        val start = state.currentTimeSec.coerceIn(0f, (dur - 1f).coerceAtLeast(0f))
        val end = (start + 6f).coerceAtMost(dur)
        val defaultY = when (kind) {
            GraphicKind.LOWER_THIRD -> 0.72f
            GraphicKind.ALERT_BADGE -> 0.16f
            GraphicKind.PROGRESS_PILL -> 0.80f
            else -> 0.48f
        }
        val item = OverlayElement(
            id = UUID.randomUUID().toString(),
            type = OverlayType.GRAPHIC,
            name = kind.displayName,
            startSec = start,
            endSec = end,
            xNorm = 0.5f,
            yNorm = defaultY,
            motion = motion,
            text = label,
            textColor = 0xFFFFFFFFL,
            bgColor = accentColor,
            graphicKind = kind
        )
        _uiState.update {
            it.copy(
                overlays = it.overlays + item,
                selectedOverlayId = item.id,
                activeSheet = ActiveSheet.NONE
            )
        }
    }

    fun deleteOverlay(id: String) {
        _uiState.update { cur ->
            cur.copy(
                overlays = cur.overlays.filterNot { it.id == id },
                selectedOverlayId = if (cur.selectedOverlayId == id) null else cur.selectedOverlayId
            )
        }
    }

    fun shiftOverlayOnTimeline(id: String, deltaSec: Float) {
        val maxDur = _uiState.value.project?.totalDurationSec ?: 30f
        _uiState.update { cur ->
            cur.copy(
                overlays = cur.overlays.map { ov ->
                    if (ov.id == id) {
                        val len = (ov.endSec - ov.startSec).coerceAtLeast(0.5f)
                        val newStart = (ov.startSec + deltaSec).coerceIn(0f, (maxDur - len).coerceAtLeast(0f))
                        ov.copy(startSec = newStart, endSec = (newStart + len).coerceAtMost(maxDur))
                    } else ov
                }
            )
        }
    }

    fun updateOverlayTiming(id: String, newStart: Float, newEnd: Float) {
        val maxDur = _uiState.value.project?.totalDurationSec ?: 30f
        val s = newStart.coerceIn(0f, maxDur - 0.5f)
        val e = newEnd.coerceIn(s + 0.5f, maxDur)
        _uiState.update { cur ->
            cur.copy(
                overlays = cur.overlays.map { ov ->
                    if (ov.id == id) ov.copy(startSec = s, endSec = e) else ov
                }
            )
        }
    }

    // --- Audio Tracks ---

    fun addAudioTrack(uri: Uri) {
        viewModelScope.launch {
            val (name, dur) = withContext(Dispatchers.IO) {
                MediaMetadataHelper.getFileName(appContext, uri, "Audio Track") to
                    MediaMetadataHelper.getMediaDurationSec(appContext, uri, 12f)
            }
            val projDur = _uiState.value.project?.totalDurationSec ?: 30f
            val start = _uiState.value.currentTimeSec.coerceIn(0f, (projDur - 1f).coerceAtLeast(0f))
            val end = (start + dur).coerceAtMost(projDur)
            val waveform = MediaMetadataHelper.buildWaveformProfile(uri.toString())
            val track = AudioTrackItem(
                id = UUID.randomUUID().toString(),
                title = name,
                uri = uri.toString(),
                startSec = start,
                endSec = end,
                sourceDurationSec = dur,
                waveform = waveform
            )
            _uiState.update {
                it.copy(
                    audioTracks = it.audioTracks + track,
                    selectedAudioId = track.id
                )
            }
            val s = _uiState.value
            audioPlayer.sync(s.audioTracks, s.currentTimeSec, s.isPlaying, s.isMuted)
        }
    }

    fun shiftAudioTrackOnTimeline(id: String, deltaSec: Float) {
        val maxDur = _uiState.value.project?.totalDurationSec ?: 30f
        _uiState.update { cur ->
            cur.copy(
                audioTracks = cur.audioTracks.map { tr ->
                    if (tr.id == id) {
                        val len = (tr.endSec - tr.startSec).coerceAtLeast(0.5f)
                        val newStart = (tr.startSec + deltaSec).coerceIn(0f, (maxDur - len).coerceAtLeast(0f))
                        tr.copy(startSec = newStart, endSec = (newStart + len).coerceAtMost(maxDur))
                    } else tr
                }
            )
        }
    }

    fun updateAudioVolume(id: String, volume: Float) {
        _uiState.update { cur ->
            cur.copy(
                audioTracks = cur.audioTracks.map { tr ->
                    if (tr.id == id) tr.copy(volume = volume.coerceIn(0f, 1f)) else tr
                }
            )
        }
        val s = _uiState.value
        audioPlayer.sync(s.audioTracks, s.currentTimeSec, s.isPlaying, s.isMuted)
    }

    fun deleteAudioTrack(id: String) {
        _uiState.update { cur ->
            cur.copy(
                audioTracks = cur.audioTracks.filterNot { it.id == id },
                selectedAudioId = if (cur.selectedAudioId == id) null else cur.selectedAudioId
            )
        }
        val s = _uiState.value
        audioPlayer.sync(s.audioTracks, s.currentTimeSec, s.isPlaying, s.isMuted)
    }

    // --- Sheet & Export Controls ---

    fun setActiveSheet(sheet: ActiveSheet) {
        if (sheet != ActiveSheet.NONE && _uiState.value.isPlaying) {
            stopPlayback()
        }
        _uiState.update { it.copy(activeSheet = sheet) }
    }

    fun selectExportResolution(res: ExportResolution) {
        _uiState.update { it.copy(selectedResolution = res) }
    }

    fun selectExportFps(fps: Int) {
        _uiState.update { it.copy(selectedFps = fps) }
    }

    fun startExport() {
        val state = _uiState.value
        val project = state.project ?: return
        stopPlayback()
        exportJob?.cancel()

        exportJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    exportState = ExportUiState.Exporting(
                        progress = 0f,
                        currentFrame = 0,
                        totalFrames = (project.totalDurationSec * state.selectedFps).toInt(),
                        statusLabel = "Initializing encoder..."
                    )
                )
            }
            runCatching {
                exportEngine.exportVideo(
                    config = project,
                    resolution = state.selectedResolution,
                    fps = state.selectedFps,
                    webController = webController,
                    overlayRenderer = overlayRenderer,
                    overlays = state.overlays,
                    audioTracks = state.audioTracks
                ) { progress, frame, total, status ->
                    _uiState.update {
                        it.copy(
                            exportState = ExportUiState.Exporting(
                                progress = progress,
                                currentFrame = frame,
                                totalFrames = total,
                                statusLabel = status
                            )
                        )
                    }
                }
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        exportState = ExportUiState.Success(
                            outputUriString = result.outputUri.toString(),
                            fileName = result.fileName,
                            resolutionLabel = state.selectedResolution.label,
                            dimensionsText = "${result.width}×${result.height}"
                        )
                    )
                }
                // Restore preview to current playhead
                seekTo(state.currentTimeSec)
            }.onFailure { err ->
                if (err is kotlinx.coroutines.CancellationException) {
                    _uiState.update { it.copy(exportState = ExportUiState.Idle) }
                } else {
                    _uiState.update {
                        it.copy(
                            exportState = ExportUiState.Error(
                                err.localizedMessage ?: "Export failed on this device."
                            )
                        )
                    }
                }
                seekTo(state.currentTimeSec)
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _uiState.update { it.copy(exportState = ExportUiState.Idle) }
    }

    fun resetExportState() {
        _uiState.update { it.copy(exportState = ExportUiState.Idle) }
    }

    fun clearBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
        exportJob?.cancel()
        audioPlayer.release()
        overlayRenderer.release()
        webController.destroy()
    }
}
