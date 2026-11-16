package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.model.GraphicKind
import com.example.model.MotionPreset
import com.example.model.OverlayElement
import com.example.model.OverlayType
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class OverlayCanvasRenderer(private val context: Context) {

    private val imageBitmapCache = mutableMapOf<String, Bitmap>()
    private val videoRetrieverCache = mutableMapOf<String, MediaMetadataRetriever>()
    private val videoFrameCache = mutableMapOf<String, Pair<Long, Bitmap>>()

    private val bengaliTypeface: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.noto_sans_bengali) ?: Typeface.DEFAULT_BOLD
    }

    private val displayTypeface: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.space_grotesk) ?: Typeface.DEFAULT_BOLD
    }

    fun renderOverlays(
        canvas: Canvas,
        width: Int,
        height: Int,
        overlays: List<OverlayElement>,
        currentTimeSec: Float,
        selectedOverlayId: String? = null,
        drawSelectionChrome: Boolean = false
    ) {
        val activeOverlays = overlays.filter {
            currentTimeSec >= it.startSec && currentTimeSec <= it.endSec
        }

        for (overlay in activeOverlays) {
            val localSec = (currentTimeSec - overlay.startSec).coerceAtLeast(0f)
            val durationSec = (overlay.endSec - overlay.startSec).coerceAtLeast(0.1f)
            val motionTransform = computeMotionTransform(overlay.motion, localSec, durationSec, width, height)

            val cx = overlay.xNorm * width + motionTransform.translateX
            val cy = overlay.yNorm * height + motionTransform.translateY
            val effectiveScale = overlay.scale * motionTransform.scale
            val effectiveAlpha = (overlay.alpha * motionTransform.alpha).coerceIn(0f, 1f)

            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(overlay.rotationDeg)
            canvas.scale(effectiveScale, effectiveScale)

            val bounds = when (overlay.type) {
                OverlayType.TEXT -> drawTextOverlay(canvas, width, overlay, effectiveAlpha)
                OverlayType.IMAGE -> drawImageOverlay(canvas, width, height, overlay, effectiveAlpha)
                OverlayType.VIDEO -> drawVideoOverlay(canvas, width, height, overlay, localSec, effectiveAlpha)
                OverlayType.GRAPHIC -> drawGraphicOverlay(canvas, width, overlay, localSec, effectiveAlpha)
            }

            if (drawSelectionChrome && overlay.id == selectedOverlayId && bounds != null) {
                drawSelectionBox(canvas, bounds)
            }

            canvas.restore()
        }
    }

    private data class MotionTransform(
        val alpha: Float = 1f,
        val scale: Float = 1f,
        val translateX: Float = 0f,
        val translateY: Float = 0f
    )

    private fun computeMotionTransform(
        preset: MotionPreset,
        localSec: Float,
        durationSec: Float,
        w: Int,
        h: Int
    ): MotionTransform {
        val enterProgress = (localSec / 0.45f).coerceIn(0f, 1f)
        val easeOut = 1f - (1f - enterProgress) * (1f - enterProgress) * (1f - enterProgress)
        return when (preset) {
            MotionPreset.NONE -> MotionTransform()
            MotionPreset.FADE_IN -> MotionTransform(alpha = easeOut)
            MotionPreset.SLIDE_UP -> MotionTransform(
                alpha = easeOut,
                translateY = (1f - easeOut) * (h * 0.06f)
            )
            MotionPreset.POP_SCALE -> {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                val x = enterProgress - 1f
                val back = 1f + c3 * x * x * x + c1 * x * x
                MotionTransform(alpha = enterProgress.coerceIn(0f, 1f), scale = back.coerceAtLeast(0.1f))
            }
            MotionPreset.PULSE -> {
                val wave = 1f + 0.06f * sin(localSec * 2f * PI.toFloat() * 1.4f)
                MotionTransform(alpha = easeOut, scale = wave)
            }
            MotionPreset.DRIFT_ZOOM -> {
                val p = (localSec / durationSec).coerceIn(0f, 1f)
                MotionTransform(alpha = easeOut, scale = 1f + p * 0.14f)
            }
        }
    }

    private fun drawTextOverlay(
        canvas: Canvas,
        canvasW: Int,
        overlay: OverlayElement,
        alpha: Float
    ): RectF {
        val scaleFactor = canvasW / 380f
        val textSizePx = overlay.fontSizeSp * scaleFactor
        val maxTextWidth = (canvasW * 0.82f).toInt().coerceAtLeast(100)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = applyAlpha(overlay.textColor.toInt(), alpha)
            textSize = textSizePx
            typeface = bengaliTypeface
            setShadowLayer(4f * scaleFactor, 0f, 2f * scaleFactor, applyAlpha(Color.BLACK, alpha * 0.65f))
        }

        val displayStr = overlay.text.ifBlank { "Sample Text" }
        val layout = StaticLayout.Builder
            .obtain(displayStr, 0, displayStr.length, textPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()

        var maxLineW = 0f
        for (i in 0 until layout.lineCount) {
            maxLineW = max(maxLineW, layout.getLineWidth(i))
        }
        val boxW = maxLineW.coerceAtLeast(60f * scaleFactor)
        val boxH = layout.height.toFloat().coerceAtLeast(30f * scaleFactor)
        val padH = 16f * scaleFactor
        val padV = 10f * scaleFactor

        val rect = RectF(
            -boxW / 2f - padH,
            -boxH / 2f - padV,
            boxW / 2f + padH,
            boxH / 2f + padV
        )

        if (overlay.hasBgPill) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = applyAlpha(overlay.bgColor.toInt(), alpha)
                style = Paint.Style.FILL
            }
            val radius = 14f * scaleFactor
            canvas.drawRoundRect(rect, radius, radius, bgPaint)
        }

        canvas.save()
        canvas.translate(-maxTextWidth / 2f, -boxH / 2f)
        layout.draw(canvas)
        canvas.restore()

        return rect
    }

    private fun drawImageOverlay(
        canvas: Canvas,
        canvasW: Int,
        canvasH: Int,
        overlay: OverlayElement,
        alpha: Float
    ): RectF? {
        val uriStr = overlay.mediaUri ?: return null
        val bmp = getOrLoadImageBitmap(uriStr) ?: return null

        val targetMaxW = canvasW * 0.48f
        val targetMaxH = canvasH * 0.36f
        val aspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
        val drawW: Float
        val drawH: Float
        if (targetMaxW / aspect <= targetMaxH) {
            drawW = targetMaxW
            drawH = targetMaxW / aspect
        } else {
            drawH = targetMaxH
            drawW = targetMaxH * aspect
        }

        val destRect = RectF(-drawW / 2f, -drawH / 2f, drawW / 2f, drawH / 2f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.alpha = (alpha * 255).toInt()
        }
        val path = Path().apply {
            addRoundRect(destRect, 18f, 18f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bmp, null, destRect, paint)
        canvas.restore()

        return destRect
    }

    private fun drawVideoOverlay(
        canvas: Canvas,
        canvasW: Int,
        canvasH: Int,
        overlay: OverlayElement,
        localSec: Float,
        alpha: Float
    ): RectF? {
        val uriStr = overlay.mediaUri ?: return null
        val frameBmp = getVideoFrameAt(uriStr, localSec)

        val drawW = canvasW * 0.48f
        val drawH = if (frameBmp != null) {
            val aspect = frameBmp.width.toFloat() / frameBmp.height.coerceAtLeast(1)
            min(canvasH * 0.40f, drawW / aspect)
        } else {
            drawW * 0.75f
        }

        val destRect = RectF(-drawW / 2f, -drawH / 2f, drawW / 2f, drawH / 2f)
        if (frameBmp != null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                this.alpha = (alpha * 255).toInt()
            }
            val path = Path().apply {
                addRoundRect(destRect, 18f, 18f, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(frameBmp, null, destRect, paint)
            canvas.restore()
        } else {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = applyAlpha(Color.parseColor("#1E293B"), alpha)
            }
            canvas.drawRoundRect(destRect, 18f, 18f, p)
        }
        return destRect
    }

    private fun drawGraphicOverlay(
        canvas: Canvas,
        canvasW: Int,
        overlay: OverlayElement,
        localSec: Float,
        alpha: Float
    ): RectF {
        val sf = canvasW / 380f
        val accent = applyAlpha(overlay.bgColor.toInt(), alpha)
        val textCol = applyAlpha(overlay.textColor.toInt(), alpha)
        val label = overlay.text.ifBlank { "BREAKING NEWS" }

        return when (overlay.graphicKind) {
            GraphicKind.LOWER_THIRD -> {
                val w = canvasW * 0.84f
                val h = 54f * sf
                val rect = RectF(-w / 2f, -h / 2f, w / 2f, h / 2f)
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = applyAlpha(Color.parseColor("#0F172A"), alpha * 0.94f)
                }
                canvas.drawRoundRect(rect, 12f * sf, 12f * sf, bgPaint)

                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
                canvas.drawRoundRect(
                    RectF(-w / 2f, -h / 2f, -w / 2f + 10f * sf, h / 2f),
                    6f * sf,
                    6f * sf,
                    barPaint
                )

                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textCol
                    textSize = 18f * sf
                    typeface = bengaliTypeface
                    textAlign = Paint.Align.LEFT
                }
                canvas.drawText(label, -w / 2f + 22f * sf, 6f * sf, tp)
                rect
            }

            GraphicKind.ALERT_BADGE -> {
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textCol
                    textSize = 16f * sf
                    typeface = displayTypeface
                    textAlign = Paint.Align.CENTER
                }
                val tw = tp.measureText(label)
                val w = max(110f * sf, tw + 42f * sf)
                val h = 38f * sf
                val rect = RectF(-w / 2f, -h / 2f, w / 2f, h / 2f)
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
                canvas.drawRoundRect(rect, h / 2f, h / 2f, bgPaint)

                val dotAlpha = (0.55f + 0.45f * sin(localSec * 6f)) * alpha
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = applyAlpha(Color.WHITE, dotAlpha)
                }
                canvas.drawCircle(-w / 2f + 18f * sf, 0f, 5f * sf, dotPaint)
                canvas.drawText(label, 6f * sf, 6f * sf, tp)
                rect
            }

            GraphicKind.CALLOUT_ARROW -> {
                val w = 88f * sf
                val h = 44f * sf
                val rect = RectF(-w / 2f, -h / 2f, w / 2f, h / 2f)
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accent
                    style = Paint.Style.FILL
                }
                val arrow = Path().apply {
                    moveTo(-w / 2f, -10f * sf)
                    lineTo(w * 0.12f, -10f * sf)
                    lineTo(w * 0.12f, -h / 2f)
                    lineTo(w / 2f, 0f)
                    lineTo(w * 0.12f, h / 2f)
                    lineTo(w * 0.12f, 10f * sf)
                    lineTo(-w / 2f, 10f * sf)
                    close()
                }
                canvas.drawPath(arrow, p)
                rect
            }

            GraphicKind.HIGHLIGHT_RING -> {
                val r = 56f * sf
                val pulseR = r * (1f + 0.08f * sin(localSec * 4.5f))
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accent
                    style = Paint.Style.STROKE
                    strokeWidth = 5f * sf
                }
                canvas.drawCircle(0f, 0f, pulseR, p)
                val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = applyAlpha(accent, alpha * 0.15f)
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(0f, 0f, pulseR, inner)
                RectF(-r, -r, r, r)
            }

            GraphicKind.PROGRESS_PILL -> {
                val w = canvasW * 0.6f
                val h = 36f * sf
                val rect = RectF(-w / 2f, -h / 2f, w / 2f, h / 2f)
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = applyAlpha(Color.parseColor("#0F172A"), alpha * 0.9f)
                }
                canvas.drawRoundRect(rect, h / 2f, h / 2f, bg)
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accent
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f * sf
                }
                canvas.drawRoundRect(rect, h / 2f, h / 2f, stroke)
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textCol
                    textSize = 15f * sf
                    typeface = bengaliTypeface
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(label, 0f, 5f * sf, tp)
                rect
            }
        }
    }

    private fun drawSelectionBox(canvas: Canvas, bounds: RectF) {
        val pad = 10f
        val r = RectF(bounds.left - pad, bounds.top - pad, bounds.right + pad, bounds.bottom + pad)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(r, 8f, 8f, borderPaint)

        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        listOf(
            r.left to r.top,
            r.right to r.top,
            r.left to r.bottom,
            r.right to r.bottom
        ).forEach { (hx, hy) ->
            canvas.drawCircle(hx, hy, 8f, handlePaint)
            canvas.drawCircle(hx, hy, 8f, handleStroke)
        }
    }

    private fun getOrLoadImageBitmap(uriStr: String): Bitmap? {
        imageBitmapCache[uriStr]?.let { return it }
        val uri = Uri.parse(uriStr)
        val bmp = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, opts)
                val maxDim = max(opts.outWidth, opts.outHeight)
                val sample = if (maxDim > 1440) (maxDim / 1440).coerceAtLeast(1) else 1
                context.contentResolver.openInputStream(uri)?.use { stream2 ->
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeStream(stream2, null, decodeOpts)
                }
            }
        }.getOrNull()
        if (bmp != null) {
            imageBitmapCache[uriStr] = bmp
        }
        return bmp
    }

    private fun getVideoFrameAt(uriStr: String, localSec: Float): Bitmap? {
        val quantizedMs = ((localSec * 15f).toLong() * 66L).coerceAtLeast(0L)
        val cached = videoFrameCache[uriStr]
        if (cached != null && cached.first == quantizedMs) {
            return cached.second
        }
        val retriever = videoRetrieverCache.getOrPut(uriStr) {
            MediaMetadataRetriever().apply {
                runCatching { setDataSource(context, Uri.parse(uriStr)) }
            }
        }
        val frame = runCatching {
            retriever.getFrameAtTime(quantizedMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.let { scaleDownIfNeeded(it, 720) }
        }.getOrNull()
        if (frame != null) {
            videoFrameCache[uriStr] = quantizedMs to frame
        }
        return frame
    }

    private fun scaleDownIfNeeded(bmp: Bitmap, maxDim: Int): Bitmap {
        val m = max(bmp.width, bmp.height)
        if (m <= maxDim) return bmp
        val ratio = maxDim.toFloat() / m
        val nw = (bmp.width * ratio).toInt().coerceAtLeast(2)
        val nh = (bmp.height * ratio).toInt().coerceAtLeast(2)
        val matrix = Matrix().apply { postScale(nw.toFloat() / bmp.width, nh.toFloat() / bmp.height) }
        val scaled = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        if (scaled != bmp) bmp.recycle()
        return scaled
    }

    private fun applyAlpha(argb: Int, alpha: Float): Int {
        val baseA = Color.alpha(argb)
        val finalA = ((baseA * alpha).toInt()).coerceIn(0, 255)
        return Color.argb(finalA, Color.red(argb), Color.green(argb), Color.blue(argb))
    }

    fun release() {
        imageBitmapCache.values.forEach { if (!it.isRecycled) it.recycle() }
        imageBitmapCache.clear()
        videoFrameCache.values.forEach { if (!it.second.isRecycled) it.second.recycle() }
        videoFrameCache.clear()
        videoRetrieverCache.values.forEach { runCatching { it.release() } }
        videoRetrieverCache.clear()
    }
}
