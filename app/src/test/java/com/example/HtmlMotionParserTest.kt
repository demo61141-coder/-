package com.example

import com.example.engine.HtmlMotionParser
import com.example.engine.MotionBridgeInjector
import com.example.model.ExportResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HtmlMotionParserTest {

    @Test
    fun parsesSampleStoryHtmlCorrectly() {
        val sampleFile = File("src/main/assets/sample_story.html")
        val rawHtml = sampleFile.readText()
        val (config, scenes) = HtmlMotionParser.parse(rawHtml, "sample_story.html")

        assertEquals(1080, config.canvasWidth)
        assertEquals(1920, config.canvasHeight)
        assertEquals(72.0f, config.totalDurationSec, 0.01f)
        assertTrue(config.isCanvasDriven)
        assertEquals(7, scenes.size)
        assertEquals("ভূমি অফিসে ঘুষ ও দুদকের অভিযান", scenes[0].headline)
        assertEquals(0.0f, scenes[0].startSec, 0.01f)
        assertEquals(10.5f, scenes[0].endSec, 0.01f)
        assertEquals(72.0f, scenes[6].endSec, 0.01f)
    }

    @Test
    fun injectsMotionBridgeAndExposesScenes() {
        val sampleFile = File("src/main/assets/sample_story.html")
        val injected = MotionBridgeInjector.inject(sampleFile.readText())
        assertTrue(injected.contains("window.__mcSeekTo"))
        assertTrue(injected.contains("window.__mcRenderAndCapture"))
        assertTrue(injected.contains("window.__mcSetExportMode"))
        assertTrue(injected.contains("recordGraphicBox"))
        assertTrue(injected.contains("var SCENES = window.__MC_SCENES ="))
        assertTrue(injected.contains("var TOTAL_DURATION = window.__MC_TOTAL_DURATION ="))
    }

    @Test
    fun computesAllExportResolutionsCleanly() {
        assertEquals(240 to 428, ExportResolution.R240P.computeDimensions(1080, 1920))
        assertEquals(360 to 640, ExportResolution.R360P.computeDimensions(1080, 1920))
        assertEquals(480 to 854, ExportResolution.R480P.computeDimensions(1080, 1920))
        assertEquals(720 to 1280, ExportResolution.R720P.computeDimensions(1080, 1920))
        assertEquals(1080 to 1920, ExportResolution.R1080P.computeDimensions(1080, 1920))
    }
}
