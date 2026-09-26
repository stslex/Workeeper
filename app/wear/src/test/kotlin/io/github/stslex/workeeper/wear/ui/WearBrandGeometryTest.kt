package io.github.stslex.workeeper.wear.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "ru-w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearBrandGeometryTest {

    @Test
    fun editorIconsAreCenteredAndRussianInstructionsStayInsideTheCircle() = runComposeUiTest {
        val active = fixture(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY).copy(reps = 8, weightHundredthsKg = 10_000)
        var model by mutableStateOf(active)
        var screen by mutableStateOf(WearScreen.SMALL_ROUND)
        var scale by mutableFloatStateOf(1f)
        setContent {
            WearGateHost(screen, scale) {
                WearControllerScreen(state = model, onAction = {})
            }
        }
        WearScreen.entries.forEach { nextScreen ->
            listOf(1f, LARGEST_WEAR_FONT_SCALE).forEach { nextScale ->
                screen = nextScreen
                scale = nextScale
                model = active
                waitForIdle()
                onNodeWithTag("reps_card").performScrollTo().performClick()
                waitForIdle()
                assertIconCentered("editor_increase")
                assertIconCentered("editor_decrease")
                model = fixture(SyntheticSurfaceFixtures.COMPLETE).copy(trainingName = "Результаты тренировки")
                waitForIdle()
                onNodeWithTag("training_name").assertTextEquals("Результаты тренировки")
                assertRoundViewport()
                assertWordsAreNotSplit()
                onNodeWithTag("finish_on_phone").performScrollTo()
                waitForIdle()
                val finish = onNodeWithTag("finish_on_phone").getUnclippedBoundsInRoot()
                val viewport = onNodeWithTag("controller_scroll").getUnclippedBoundsInRoot()
                assertTrue(finish.top >= viewport.top - 1.dp && finish.bottom <= viewport.bottom + 1.dp)
            }
        }
    }

    private fun ComposeUiTest.assertIconCentered(tag: String) {
        val button = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val glyph = onNodeWithTag("${tag}_glyph", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(button.center.x, glyph.center.x, 0.5f, "$tag horizontal center")
        assertEquals(button.center.y, glyph.center.y, 0.5f, "$tag vertical center")
        val target = with(density) { 48.dp.toPx() }
        assertTrue(button.width >= target && button.height >= target, "$tag must retain a 48dp target")
        val buttonImage = onNodeWithTag(tag).captureToImage()
        val corner = buttonImage.width / 8
        assertEquals(
            WearPalette.textPrimary.toArgb(),
            buttonImage.toPixelMap()[corner, corner].toArgb(),
            "$tag uses the shared rounded shape",
        )
        val image = onNodeWithTag("${tag}_glyph", useUnmergedTree = true).captureToImage()
        val pixels = image.toPixelMap()
        val ink = buildList {
            repeat(image.height) { y ->
                repeat(image.width) { x ->
                    if (pixels[x, y].toArgb() == WearPalette.onAccent.toArgb()) add(x to y)
                }
            }
        }
        assertTrue(ink.isNotEmpty(), "$tag sign must be drawn")
        val inkCenterX = (ink.minOf { it.first } + ink.maxOf { it.first } + 1) / 2f
        val inkCenterY = (ink.minOf { it.second } + ink.maxOf { it.second } + 1) / 2f
        assertEquals(image.width / 2f, inkCenterX, 0.5f, "$tag painted horizontal center")
        assertEquals(image.height / 2f, inkCenterY, 0.5f, "$tag painted vertical center")
    }

    private fun ComposeUiTest.assertRoundViewport() {
        val surface = onNodeWithTag("wear_surface").fetchSemanticsNode().boundsInRoot
        val viewport = onNodeWithTag("controller_scroll").fetchSemanticsNode().boundsInRoot
        val radius = minOf(surface.width, surface.height) / 2
        listOf(viewport.left, viewport.right).forEach { x ->
            listOf(viewport.top, viewport.bottom).forEach { y ->
                val dx = x - surface.center.x
                val dy = y - surface.center.y
                assertTrue(dx * dx + dy * dy <= radius * radius, "instruction viewport crosses the round edge")
            }
        }
    }

    private fun ComposeUiTest.assertWordsAreNotSplit() {
        val nodes = onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertTrue(nodes.isNotEmpty())
        nodes.forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            layouts.forEach { layout ->
                val text = layout.layoutInput.text.text
                assertTrue(!layout.hasVisualOverflow, text)
                repeat((layout.lineCount - 1).coerceAtLeast(0)) { line ->
                    val end = layout.getLineEnd(line, visibleEnd = true)
                    val split = end > 0 && end < text.length && text[end - 1].isLetter() && text[end].isLetter()
                    assertTrue(!split, "word split inside '$text' at $end")
                }
            }
        }
    }

    private fun fixture(id: String) = requireNotNull(SyntheticSurfaceFixtures.find(id))
}
