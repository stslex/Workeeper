// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.acceptance

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import io.github.stslex.workeeper.wear.ui.WearPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

internal fun WearAcceptanceSession.assertEditorSigns() {
    listOf("editor_increase", "editor_decrease").forEach { tag ->
        val button = node(tag, merged = true)
        val glyph = node("${tag}_glyph")
        assertEquals("$tag horizontal center", button.boundsInRoot.center.x, glyph.boundsInRoot.center.x, 0.5f)
        assertEquals("$tag vertical center", button.boundsInRoot.center.y, glyph.boundsInRoot.center.y, 0.5f)
        val expectedInk = if (button.config.contains(SemanticsProperties.Disabled)) {
            WearPalette.textMuted
        } else {
            WearPalette.onAccent
        }.toArgb()
        val image = rule.onNodeWithTag("${tag}_glyph", useUnmergedTree = true).captureToImage()
        val pixels = image.toPixelMap()
        val ink = buildList {
            repeat(image.height) { y ->
                repeat(image.width) { x ->
                    if (pixels[x, y].toArgb() == expectedInk) add(x to y)
                }
            }
        }
        assertTrue("$tag painted sign exists", ink.isNotEmpty())
        val x = (ink.minOf { it.first } + ink.maxOf { it.first } + 1) / 2f
        val y = (ink.minOf { it.second } + ink.maxOf { it.second } + 1) / 2f
        assertEquals("$tag painted horizontal center", image.width / 2f, x, 0.5f)
        assertEquals("$tag painted vertical center", image.height / 2f, y, 0.5f)
    }
}

internal fun assertWholeWords(layout: TextLayoutResult, label: String) {
    val text = layout.layoutInput.text.text
    repeat((layout.lineCount - 1).coerceAtLeast(0)) { line ->
        val end = layout.getLineEnd(line, visibleEnd = true)
        val split = end > 0 && end < text.length && text[end - 1].isLetter() && text[end].isLetter()
        assertFalse("$label splits a word inside '$text' at $end", split)
    }
}
