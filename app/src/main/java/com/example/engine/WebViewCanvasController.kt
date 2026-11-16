package com.example.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Base64
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.model.HtmlCanvasElement
import com.example.model.HtmlElementKind
import com.example.model.HtmlElementOverride
import com.example.model.HtmlSceneItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

class WebViewCanvasController(private val context: Context) {

    private var webView: WebView? = null
    private var isPageLoaded = false
    private var pendingHtml: String? = null
    private var lastLoadedRawHash: Int = 0

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(onReady: () -> Unit = {}): WebView {
        webView?.let { return it }

        val wv = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#070B12"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean = true
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    isPageLoaded = true
                    onReady()
                }
            }
            setOnTouchListener { _, _ -> true }
        }
        webView = wv
        pendingHtml?.let { html ->
            loadInjectedHtmlInternal(wv, html)
            pendingHtml = null
        }
        return wv
    }

    fun loadHtml(rawHtml: String) {
        val hash = rawHtml.hashCode()
        if (hash == lastLoadedRawHash && webView != null) return
        lastLoadedRawHash = hash
        isPageLoaded = false
        val injected = MotionBridgeInjector.inject(rawHtml)
        val wv = webView
        if (wv != null) {
            loadInjectedHtmlInternal(wv, injected)
        } else {
            pendingHtml = injected
        }
    }

    private fun loadInjectedHtmlInternal(wv: WebView, injectedHtml: String) {
        wv.loadDataWithBaseURL(
            "https://framecraft.local/",
            injectedHtml,
            "text/html",
            "UTF-8",
            null
        )
    }

    fun seekTo(
        timeSec: Float,
        onElementsDetected: (List<HtmlCanvasElement>) -> Unit = {}
    ) {
        val wv = webView ?: return
        if (!isPageLoaded) return
        wv.evaluateJavascript("window.__mcSeekTo(${timeSec});") { result ->
            parseSeekResult(result, onElementsDetected)
        }
    }

    suspend fun setExportMode(enabled: Boolean) {
        val wv = webView ?: return
        if (!isPageLoaded) return
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                wv.evaluateJavascript("window.__mcSetExportMode($enabled);") {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    fun syncScenes(scenes: List<HtmlSceneItem>, totalDurationSec: Float) {
        val wv = webView ?: return
        if (!isPageLoaded) return
        val arr = JSONArray()
        scenes.forEach { s ->
            val obj = JSONObject().apply {
                put("id", s.id)
                put("start", s.startSec.toDouble())
                put("end", s.endSec.toDouble())
                put("headline", s.headline)
                put("sub", s.sub)
                put("caption", s.caption)
                put("isDeleted", s.isDeleted)
            }
            arr.put(obj)
        }
        val escaped = JSONObject.quote(arr.toString())
        wv.evaluateJavascript("window.__mcUpdateScenes($escaped, $totalDurationSec);", null)
    }

    fun syncElementOverrides(overrides: Map<String, HtmlElementOverride>) {
        val wv = webView ?: return
        if (!isPageLoaded) return
        val arr = JSONArray()
        overrides.values.forEach { ov ->
            val obj = JSONObject().apply {
                put("key", ov.key)
                put("customText", ov.customText ?: "")
                put("offsetXNorm", ov.offsetXNorm.toDouble())
                put("offsetYNorm", ov.offsetYNorm.toDouble())
                put("scale", ov.scale.toDouble())
                if (ov.colorHex != null) put("colorHex", ov.colorHex)
                put("deleted", ov.isDeleted)
            }
            arr.put(obj)
        }
        val escaped = JSONObject.quote(arr.toString())
        wv.evaluateJavascript("window.__mcSetElementOverrides($escaped);", null)
    }

    suspend fun renderAndCaptureFrame(
        timeSec: Float,
        targetW: Int,
        targetH: Int,
        quality: Float,
        isCanvasDriven: Boolean,
        reuseBitmap: Bitmap? = null
    ): Bitmap? {
        val wv = webView ?: return null
        if (isCanvasDriven) {
            val dataUrl = withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<String?> { cont ->
                    wv.evaluateJavascript(
                        "window.__mcRenderAndCapture($timeSec, $targetW, $targetH, $quality);"
                    ) { raw ->
                        val parsed = decodeJsString(raw)
                        if (cont.isActive) cont.resume(parsed)
                    }
                }
            }
            if (!dataUrl.isNullOrBlank() && dataUrl.startsWith("data:image")) {
                val base64Part = dataUrl.substringAfter("base64,", "")
                if (base64Part.isNotEmpty()) {
                    return withContext(Dispatchers.Default) {
                        runCatching {
                            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                            val opts = BitmapFactory.Options().apply {
                                inMutable = true
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                                if (reuseBitmap != null && !reuseBitmap.isRecycled &&
                                    reuseBitmap.width == targetW && reuseBitmap.height == targetH
                                ) {
                                    inBitmap = reuseBitmap
                                }
                            }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                        }.getOrElse {
                            // Fallback without inBitmap if a device codec rejects reuse
                            runCatching {
                                val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }.getOrNull()
                        }
                    }
                }
            }
        }

        // Fallback for pure DOM/CSS HTML files
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                wv.evaluateJavascript("window.__mcSeekTo($timeSec);") {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            runCatching {
                val vw = wv.width.coerceAtLeast(1)
                val vh = wv.height.coerceAtLeast(1)
                val rawBmp = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888)
                val c = Canvas(rawBmp)
                wv.draw(c)
                if (vw == targetW && vh == targetH) {
                    rawBmp
                } else {
                    val scaled = Bitmap.createScaledBitmap(rawBmp, targetW, targetH, true)
                    rawBmp.recycle()
                    scaled
                }
            }.getOrNull()
        }
    }

    private fun parseSeekResult(
        rawResult: String?,
        onElementsDetected: (List<HtmlCanvasElement>) -> Unit
    ) {
        val jsonStr = decodeJsString(rawResult) ?: return
        runCatching {
            val root = JSONObject(jsonStr)
            val arr = root.optJSONArray("elements") ?: return
            val result = mutableListOf<HtmlCanvasElement>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val kind = when (item.optString("kind")) {
                    "IMAGE" -> HtmlElementKind.IMAGE
                    "GRAPHIC" -> HtmlElementKind.GRAPHIC
                    else -> HtmlElementKind.TEXT
                }
                result.add(
                    HtmlCanvasElement(
                        key = item.optString("key"),
                        kind = kind,
                        originalText = item.optString("originalText"),
                        displayText = item.optString("displayText"),
                        xNorm = item.optDouble("xNorm", 0.0).toFloat(),
                        yNorm = item.optDouble("yNorm", 0.0).toFloat(),
                        wNorm = item.optDouble("wNorm", 0.2).toFloat(),
                        hNorm = item.optDouble("hNorm", 0.05).toFloat()
                    )
                )
            }
            onElementsDetected(result)
        }
    }

    private fun decodeJsString(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching {
            val tokener = JSONTokener(raw).nextValue()
            tokener as? String
        }.getOrNull()
    }

    fun destroy() {
        webView?.destroy()
        webView = null
        isPageLoaded = false
    }
}
