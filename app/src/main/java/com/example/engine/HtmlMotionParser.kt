package com.example.engine

import com.example.model.HtmlSceneItem
import com.example.model.ProjectConfig
import java.util.regex.Pattern

object HtmlMotionParser {

    fun parse(rawHtml: String, fileName: String): Pair<ProjectConfig, List<HtmlSceneItem>> {
        val title = extractTitle(rawHtml) ?: fileName.removeSuffix(".html").removeSuffix(".htm")
        val (width, height) = extractCanvasDimensions(rawHtml)
        val scenes = extractScenes(rawHtml)
        val extractedDuration = extractTotalDuration(rawHtml)
            ?: scenes.maxOfOrNull { it.endSec }
            ?: 30f

        val isCanvas = rawHtml.contains("<canvas", ignoreCase = true)

        val config = ProjectConfig(
            title = title.ifBlank { "Untitled Motion Project" },
            sourceName = fileName,
            rawHtml = rawHtml,
            canvasWidth = width,
            canvasHeight = height,
            totalDurationSec = extractedDuration.coerceIn(3f, 600f),
            isCanvasDriven = isCanvas
        )
        return config to scenes
    }

    private fun extractTitle(html: String): String? {
        val matcher = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
            .matcher(html)
        return if (matcher.find()) matcher.group(1)?.trim()?.takeIf { it.isNotEmpty() } else null
    }

    private fun extractCanvasDimensions(html: String): Pair<Int, Int> {
        val wMatcher = Pattern.compile("""(?:const|let|var)\s+W\s*=\s*(\d{3,4})""").matcher(html)
        val hMatcher = Pattern.compile("""(?:const|let|var)\s+H\s*=\s*(\d{3,4})""").matcher(html)
        if (wMatcher.find() && hMatcher.find()) {
            val w = wMatcher.group(1)?.toIntOrNull()
            val h = hMatcher.group(1)?.toIntOrNull()
            if (w != null && h != null && w in 240..3840 && h in 240..3840) {
                return w to h
            }
        }

        val cwMatcher = Pattern.compile("""canvas\.width\s*=\s*(\d{3,4})""").matcher(html)
        val chMatcher = Pattern.compile("""canvas\.height\s*=\s*(\d{3,4})""").matcher(html)
        if (cwMatcher.find() && chMatcher.find()) {
            val w = cwMatcher.group(1)?.toIntOrNull()
            val h = chMatcher.group(1)?.toIntOrNull()
            if (w != null && h != null && w in 240..3840 && h in 240..3840) {
                return w to h
            }
        }

        return 1080 to 1920
    }

    private fun extractTotalDuration(html: String): Float? {
        val patterns = listOf(
            """(?:const|let|var)\s+TOTAL_DURATION\s*=\s*(\d+(?:\.\d+)?)""",
            """(?:const|let|var)\s+totalDuration\s*=\s*(\d+(?:\.\d+)?)""",
            """(?:const|let|var)\s+DURATION\s*=\s*(\d+(?:\.\d+)?)"""
        )
        for (p in patterns) {
            val m = Pattern.compile(p).matcher(html)
            if (m.find()) {
                val value = m.group(1)?.toFloatOrNull()
                if (value != null && value > 0f) return value
            }
        }
        return null
    }

    private fun extractScenes(html: String): List<HtmlSceneItem> {
        val scenesStartPattern = Pattern.compile("""(?:const|let|var)\s+SCENES\s*=\s*\[""")
        val startMatcher = scenesStartPattern.matcher(html)
        if (!startMatcher.find()) return emptyList()

        val arrayStartIdx = startMatcher.end() - 1
        val arrayBlock = extractBalancedBrackets(html, arrayStartIdx, '[', ']') ?: return emptyList()

        val items = mutableListOf<HtmlSceneItem>()
        var i = 0
        while (i < arrayBlock.length) {
            if (arrayBlock[i] == '{') {
                val objBlock = extractBalancedBrackets(arrayBlock, i, '{', '}')
                if (objBlock != null) {
                    parseSceneObject(objBlock, items.size)?.let { items.add(it) }
                    i += objBlock.length
                    continue
                }
            }
            i++
        }
        return items
    }

    private fun extractBalancedBrackets(text: String, startIndex: Int, openCh: Char, closeCh: Char): String? {
        if (startIndex < 0 || startIndex >= text.length || text[startIndex] != openCh) return null
        var depth = 0
        var inQuote: Char? = null
        var escaped = false
        for (idx in startIndex until text.length) {
            val ch = text[idx]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\' && inQuote != null) {
                escaped = true
                continue
            }
            if (inQuote != null) {
                if (ch == inQuote) inQuote = null
                continue
            }
            if (ch == '"' || ch == '\'' || ch == '`') {
                inQuote = ch
                continue
            }
            if (ch == openCh) depth++
            else if (ch == closeCh) {
                depth--
                if (depth == 0) {
                    return text.substring(startIndex, idx + 1)
                }
            }
        }
        return null
    }

    private fun parseSceneObject(obj: String, fallbackId: Int): HtmlSceneItem? {
        val id = extractNumberField(obj, "id")?.toInt() ?: fallbackId
        val start = extractNumberField(obj, "start") ?: return null
        val end = extractNumberField(obj, "end") ?: return null
        val headline = extractStringField(obj, "headline")
            ?: extractStringField(obj, "title")
            ?: "Scene ${id + 1}"
        val sub = extractStringField(obj, "sub")
            ?: extractStringField(obj, "subtitle")
            ?: ""
        val caption = extractStringField(obj, "caption")
            ?: extractStringField(obj, "text")
            ?: ""

        return HtmlSceneItem(
            id = id,
            startSec = start,
            endSec = end,
            headline = headline,
            sub = sub,
            caption = caption
        )
    }

    private fun extractNumberField(obj: String, key: String): Float? {
        val m = Pattern.compile("""(?:^|[,{\s])$key\s*:\s*(-?\d+(?:\.\d+)?)""").matcher(obj)
        return if (m.find()) m.group(1)?.toFloatOrNull() else null
    }

    private fun extractStringField(obj: String, key: String): String? {
        val m = Pattern.compile("""(?:^|[,{\s])$key\s*:\s*(["'`])((?:\\.|(?!\1).)*)\1""", Pattern.DOTALL)
            .matcher(obj)
        return if (m.find()) {
            m.group(2)
                ?.replace("\\\"", "\"")
                ?.replace("\\'", "'")
                ?.replace("\\n", "\n")
        } else null
    }
}
