package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.model.AudioTrackItem
import com.example.model.ExportResolution
import com.example.model.OverlayElement
import com.example.model.ProjectConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.min

class VideoExportEngine(private val context: Context) {

    data class ExportResult(
        val outputUri: Uri,
        val fileName: String,
        val width: Int,
        val height: Int
    )

    suspend fun exportVideo(
        config: ProjectConfig,
        resolution: ExportResolution,
        fps: Int,
        webController: WebViewCanvasController,
        overlayRenderer: OverlayCanvasRenderer,
        overlays: List<OverlayElement>,
        audioTracks: List<AudioTrackItem>,
        onProgress: (progress: Float, frame: Int, totalFrames: Int, status: String) -> Unit
    ): ExportResult = withContext(Dispatchers.Default) {
        val (outW, outH) = resolution.computeDimensions(config.canvasWidth, config.canvasHeight)
        val durationSec = config.totalDurationSec.coerceAtLeast(1f)
        val totalFrames = (durationSec * fps).toInt().coerceAtLeast(1)

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "FrameCraft_${resolution.label}_$timeStamp.mp4"

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val tempMp4 = File(exportDir, fileName)
        if (tempMp4.exists()) tempMp4.delete()

        val hasAudio = audioTracks.isNotEmpty()
        val decodedAudioPcm = if (hasAudio) {
            onProgress(0.02f, 0, totalFrames, "Preparing audio mix...")
            decodeAndMixAudio(audioTracks, durationSec)
        } else null

        val includeAudioTrack = decodedAudioPcm != null && decodedAudioPcm.isNotEmpty()

        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outW, outH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, resolution.bitrateBps)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = videoEncoder.createInputSurface()
        videoEncoder.start()

        var audioEncoder: MediaCodec? = null
        if (includeAudioTrack) {
            runCatching {
                val audioFmt = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC,
                    TARGET_SAMPLE_RATE,
                    TARGET_CHANNELS
                ).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                }
                audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                    configure(audioFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    start()
                }
            }.onFailure {
                audioEncoder?.release()
                audioEncoder = null
            }
        }

        val muxer = MediaMuxer(tempMp4.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIdx = -1
        var audioTrackIdx = -1
        var muxerStarted = false

        val videoBufferInfo = MediaCodec.BufferInfo()
        val audioBufferInfo = MediaCodec.BufferInfo()

        val pendingAudioPackets = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()
        var audioSampleCursor = 0

        // Prime audio encoder with initial PCM chunks so audioTrackIdx is registered before video frames start
        if (audioEncoder != null && decodedAudioPcm != null) {
            val ae = audioEncoder!!
            val totalFramesPerSec = TARGET_SAMPLE_RATE * TARGET_CHANNELS
            var primeAttempts = 0
            while (audioTrackIdx < 0 && primeAttempts < 30) {
                primeAttempts++
                val inIdx = ae.dequeueInputBuffer(10_000)
                if (inIdx >= 0 && audioSampleCursor < decodedAudioPcm.size) {
                    val inBuf = ae.getInputBuffer(inIdx)!!
                    inBuf.clear()
                    val maxShorts = inBuf.capacity() / 2
                    val count = min(maxShorts, decodedAudioPcm.size - audioSampleCursor)
                    inBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        .put(decodedAudioPcm, audioSampleCursor, count)
                    val ptsUs = (audioSampleCursor.toLong() * 1_000_000L) / totalFramesPerSec
                    ae.queueInputBuffer(inIdx, 0, count * 2, ptsUs, 0)
                    audioSampleCursor += count
                }
                val outIdx = ae.dequeueOutputBuffer(audioBufferInfo, 10_000)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    audioTrackIdx = muxer.addTrack(ae.outputFormat)
                } else if (outIdx >= 0) {
                    val outBuf = ae.getOutputBuffer(outIdx)
                    if (outBuf != null && audioBufferInfo.size > 0 &&
                        (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    ) {
                        outBuf.position(audioBufferInfo.offset)
                        outBuf.limit(audioBufferInfo.offset + audioBufferInfo.size)
                        val bytes = ByteArray(audioBufferInfo.size)
                        outBuf.get(bytes)
                        val copyInfo = MediaCodec.BufferInfo().apply {
                            set(0, bytes.size, audioBufferInfo.presentationTimeUs, audioBufferInfo.flags)
                        }
                        pendingAudioPackets.add(bytes to copyInfo)
                    }
                    ae.releaseOutputBuffer(outIdx, false)
                }
            }
            if (audioTrackIdx < 0) {
                runCatching { audioEncoder?.stop() }
                runCatching { audioEncoder?.release() }
                audioEncoder = null
                pendingAudioPackets.clear()
            }
        }

        val jpegQuality = if (outW <= 480) 0.76f else 0.84f
        val reuseBuffers = arrayOfNulls<Bitmap>(2)
        val hasOverlays = overlays.isNotEmpty()
        webController.setExportMode(true)

        try {
            // Pre-capture frame 0
            var curIdx = 0
            var currentBitmap = webController.renderAndCaptureFrame(
                timeSec = 0f,
                targetW = outW,
                targetH = outH,
                quality = jpegQuality,
                isCanvasDriven = config.isCanvasDriven,
                reuseBitmap = reuseBuffers[0]
            )
            if (currentBitmap != null && currentBitmap.isMutable) {
                reuseBuffers[0] = currentBitmap
            }

            // Pipelined render-and-encode loop
            coroutineScope {
            for (frameIdx in 0 until totalFrames) {
                coroutineContext.ensureActive()

                val nextFrameIdx = frameIdx + 1
                val nextCaptureDeferred = if (nextFrameIdx < totalFrames) {
                    val nextTimeSec = (nextFrameIdx.toFloat() / fps).coerceAtMost(durationSec)
                    val nextBufIdx = 1 - curIdx
                    async(Dispatchers.Default) {
                        val bmp = webController.renderAndCaptureFrame(
                            timeSec = nextTimeSec,
                            targetW = outW,
                            targetH = outH,
                            quality = jpegQuality,
                            isCanvasDriven = config.isCanvasDriven,
                            reuseBitmap = reuseBuffers[nextBufIdx]
                        )
                        if (bmp != null && bmp.isMutable) {
                            reuseBuffers[nextBufIdx] = bmp
                        }
                        bmp
                    }
                } else null

                val tSec = (frameIdx.toFloat() / fps).coerceAtMost(durationSec)
                val surfaceCanvas = inputSurface.lockCanvas(null)
                try {
                    if (currentBitmap != null && !currentBitmap.isRecycled) {
                        surfaceCanvas.drawBitmap(currentBitmap, 0f, 0f, null)
                    } else {
                        surfaceCanvas.drawColor(Color.parseColor("#070B12"))
                    }
                    if (hasOverlays) {
                        overlayRenderer.renderOverlays(
                            canvas = surfaceCanvas,
                            width = outW,
                            height = outH,
                            overlays = overlays,
                            currentTimeSec = tSec,
                            selectedOverlayId = null,
                            drawSelectionChrome = false
                        )
                    }
                } finally {
                    inputSurface.unlockCanvasAndPost(surfaceCanvas)
                }

                // Drain video encoder while next frame renders in parallel
                val drainResult = drainVideoEncoder(
                    encoder = videoEncoder,
                    bufferInfo = videoBufferInfo,
                    muxer = muxer,
                    currentVideoTrackIdx = videoTrackIdx,
                    muxerStarted = muxerStarted,
                    canStartMuxer = (audioEncoder == null || audioTrackIdx >= 0),
                    endOfStream = false
                )
                videoTrackIdx = drainResult.trackIdx
                muxerStarted = drainResult.muxerStarted

                val progress = if (audioEncoder != null) {
                    0.05f + 0.80f * ((frameIdx + 1).toFloat() / totalFrames)
                } else {
                    0.02f + 0.94f * ((frameIdx + 1).toFloat() / totalFrames)
                }
                if (frameIdx % 4 == 0 || frameIdx == totalFrames - 1) {
                    onProgress(
                        progress,
                        frameIdx + 1,
                        totalFrames,
                        "Rendering ${outW}×${outH} @ ${fps}fps • ${frameIdx + 1}/$totalFrames"
                    )
                }

                if (nextCaptureDeferred != null) {
                    val prevBmp = currentBitmap
                    currentBitmap = nextCaptureDeferred.await()
                    curIdx = 1 - curIdx
                    if (prevBmp != null && prevBmp !== reuseBuffers[0] && prevBmp !== reuseBuffers[1] && !prevBmp.isRecycled) {
                        prevBmp.recycle()
                    }
                }
            }
            } // end coroutineScope

            // Signal video EOS and drain remaining frames
            videoEncoder.signalEndOfInputStream()
            var videoEos = false
            while (!videoEos) {
                coroutineContext.ensureActive()
                val res = drainVideoEncoder(
                    encoder = videoEncoder,
                    bufferInfo = videoBufferInfo,
                    muxer = muxer,
                    currentVideoTrackIdx = videoTrackIdx,
                    muxerStarted = muxerStarted,
                    canStartMuxer = (audioEncoder == null || audioTrackIdx >= 0),
                    endOfStream = true
                )
                videoTrackIdx = res.trackIdx
                muxerStarted = res.muxerStarted
                videoEos = res.sawEos
            }

            // Encode & mux remaining audio PCM if present
            if (audioEncoder != null && decodedAudioPcm != null && muxerStarted && audioTrackIdx >= 0) {
                onProgress(0.88f, totalFrames, totalFrames, "Encoding AAC audio track...")
                pendingAudioPackets.forEach { (bytes, info) ->
                    muxer.writeSampleData(audioTrackIdx, ByteBuffer.wrap(bytes), info)
                }
                pendingAudioPackets.clear()
                encodeAndMuxAudio(
                    audioEncoder = audioEncoder!!,
                    pcmSamples = decodedAudioPcm,
                    initialSampleCursor = audioSampleCursor,
                    muxer = muxer,
                    audioTrackIdx = audioTrackIdx,
                    bufferInfo = audioBufferInfo
                )
            }

            onProgress(0.97f, totalFrames, totalFrames, "Saving MP4 to Gallery...")
        } finally {
            runCatching { webController.setExportMode(false) }
            reuseBuffers.forEach { bmp ->
                if (bmp != null && !bmp.isRecycled) bmp.recycle()
            }
            runCatching { videoEncoder.stop() }
            runCatching { videoEncoder.release() }
            runCatching { inputSurface.release() }
            runCatching { audioEncoder?.stop() }
            runCatching { audioEncoder?.release() }
            runCatching {
                if (muxerStarted) muxer.stop()
                muxer.release()
            }
        }

        val finalUri = saveMp4ToGalleryOrAppStorage(tempMp4, fileName)
        onProgress(1f, totalFrames, totalFrames, "Export complete!")
        ExportResult(finalUri, fileName, outW, outH)
    }

    private data class DrainResult(
        val trackIdx: Int,
        val muxerStarted: Boolean,
        val sawEos: Boolean
    )

    private fun drainVideoEncoder(
        encoder: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        currentVideoTrackIdx: Int,
        muxerStarted: Boolean,
        canStartMuxer: Boolean,
        endOfStream: Boolean
    ): DrainResult {
        var trackIdx = currentVideoTrackIdx
        var started = muxerStarted
        var sawEos = false
        val timeoutUs = if (endOfStream) 10_000L else 0L

        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                    else break
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (trackIdx < 0) {
                        trackIdx = muxer.addTrack(encoder.outputFormat)
                    }
                    if (!started && canStartMuxer && trackIdx >= 0) {
                        muxer.start()
                        started = true
                    }
                }
                outputIndex >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(outputIndex)
                    if (encodedData != null) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && started && trackIdx >= 0) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIdx, encodedData, bufferInfo)
                        }
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawEos = true
                        break
                    }
                }
            }
        }
        return DrainResult(trackIdx, started, sawEos)
    }

    private fun encodeAndMuxAudio(
        audioEncoder: MediaCodec,
        pcmSamples: ShortArray,
        initialSampleCursor: Int,
        muxer: MediaMuxer,
        audioTrackIdx: Int,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        if (audioTrackIdx < 0) return
        var sampleCursor = initialSampleCursor
        var inputDone = false
        var outputDone = false
        val bytesPerSample = 2
        val totalFramesPerSec = TARGET_SAMPLE_RATE * TARGET_CHANNELS

        while (!outputDone) {
            if (!inputDone) {
                val inIdx = audioEncoder.dequeueInputBuffer(5_000)
                if (inIdx >= 0) {
                    val inBuf = audioEncoder.getInputBuffer(inIdx)!!
                    inBuf.clear()
                    if (sampleCursor >= pcmSamples.size) {
                        val ptsUs = (sampleCursor.toLong() * 1_000_000L) / totalFramesPerSec
                        audioEncoder.queueInputBuffer(
                            inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        val maxShorts = inBuf.capacity() / bytesPerSample
                        val count = min(maxShorts, pcmSamples.size - sampleCursor)
                        inBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            .put(pcmSamples, sampleCursor, count)
                        val ptsUs = (sampleCursor.toLong() * 1_000_000L) / totalFramesPerSec
                        audioEncoder.queueInputBuffer(inIdx, 0, count * bytesPerSample, ptsUs, 0)
                        sampleCursor += count
                    }
                }
            }

            val outIdx = audioEncoder.dequeueOutputBuffer(bufferInfo, 5_000)
            if (outIdx >= 0) {
                val outBuf = audioEncoder.getOutputBuffer(outIdx)
                if (outBuf != null && bufferInfo.size > 0 &&
                    (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                ) {
                    outBuf.position(bufferInfo.offset)
                    outBuf.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(audioTrackIdx, outBuf, bufferInfo)
                }
                audioEncoder.releaseOutputBuffer(outIdx, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputDone = true
                }
            } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                // Keep looping briefly until EOS arrives
            }
        }
    }

    private fun decodeAndMixAudio(
        tracks: List<AudioTrackItem>,
        durationSec: Float
    ): ShortArray? {
        val totalShorts = (durationSec * TARGET_SAMPLE_RATE * TARGET_CHANNELS).toInt().coerceAtLeast(1)
        val mixBuffer = FloatArray(totalShorts)
        var anyDecoded = false

        for (track in tracks) {
            val decoded = runCatching { decodeSingleAudioTrack(Uri.parse(track.uri)) }.getOrNull() ?: continue
            if (decoded.samples.isEmpty()) continue
            anyDecoded = true

            val startFrame = (track.startSec * TARGET_SAMPLE_RATE).toInt().coerceAtLeast(0)
            val endFrame = (track.endSec * TARGET_SAMPLE_RATE).toInt().coerceAtMost(totalShorts / TARGET_CHANNELS)
            val trimFrame = (track.trimStartSec * TARGET_SAMPLE_RATE).toInt().coerceAtLeast(0)

            val srcRate = decoded.sampleRate
            val srcChannels = decoded.channels
            val srcFrameCount = decoded.samples.size / srcChannels

            for (outFrame in startFrame until endFrame) {
                val elapsedOutFrames = outFrame - startFrame
                val srcPos = trimFrame + ((elapsedOutFrames.toDouble() * srcRate) / TARGET_SAMPLE_RATE).toInt()
                if (srcPos >= srcFrameCount) break

                val lSample = decoded.samples[srcPos * srcChannels] / 32768f
                val rSample = if (srcChannels > 1) {
                    decoded.samples[srcPos * srcChannels + 1] / 32768f
                } else lSample

                val outIdx = outFrame * TARGET_CHANNELS
                if (outIdx + 1 < mixBuffer.size) {
                    mixBuffer[outIdx] += lSample * track.volume
                    mixBuffer[outIdx + 1] += rSample * track.volume
                }
            }
        }

        if (!anyDecoded) return null
        return ShortArray(totalShorts) { i ->
            (mixBuffer[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
    }

    private data class DecodedPcm(val samples: ShortArray, val sampleRate: Int, val channels: Int)

    private fun decodeSingleAudioTrack(uri: Uri): DecodedPcm? {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = fmt
                break
            }
        }
        if (trackIndex < 0 || format == null) {
            extractor.release()
            return null
        }

        extractor.selectTrack(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val pcmChunks = mutableListOf<ShortArray>()
        var totalShorts = 0
        val info = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        try {
            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIdx = decoder.dequeueInputBuffer(5_000)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = decoder.dequeueOutputBuffer(info, 5_000)
                if (outIdx >= 0) {
                    val outBuf = decoder.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        val shortCount = info.size / 2
                        val arr = ShortArray(shortCount)
                        outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(arr)
                        pcmChunks.add(arr)
                        totalShorts += shortCount
                    }
                    decoder.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEos = true
                    }
                }
            }
        } finally {
            runCatching { decoder.stop() }
            runCatching { decoder.release() }
            runCatching { extractor.release() }
        }

        val merged = ShortArray(totalShorts)
        var offset = 0
        for (chunk in pcmChunks) {
            System.arraycopy(chunk, 0, merged, offset, chunk.size)
            offset += chunk.size
        }
        return DecodedPcm(merged, sampleRate, channels)
    }

    private fun saveMp4ToGalleryOrAppStorage(tempFile: File, fileName: String): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/FrameCraft")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    tempFile.inputStream().use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                tempFile.delete()
                return uri
            }
        }

        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "exports").apply { mkdirs() }
        val destFile = File(moviesDir, fileName)
        tempFile.copyTo(destFile, overwrite = true)
        tempFile.delete()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
    }

    companion object {
        private const val TARGET_SAMPLE_RATE = 44100
        private const val TARGET_CHANNELS = 2
    }
}
