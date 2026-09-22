// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import io.github.stslex.workeeper.wear.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File
import java.util.Locale

/** GUARD: one composition per sandbox; every case recreates its subtree before measuring at rest. */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "en-w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearFirstViewGateTest {
    @Test
    @DisplayName("English primary values, actions and reasons are wholly visible before scrolling")
    fun mandatoryContentIsVisibleAtRest() = runComposeUiTest {
        assertFirstViewContract(Locale.forLanguageTag("en"))
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.assertFirstViewContract(locale: Locale) {
    val fixtures = listOf(
        SyntheticSurfaceFixtures.ACTIVE_BOUNDARY,
        SyntheticSurfaceFixtures.WEIGHTLESS,
        SyntheticSurfaceFixtures.UNSET_WEIGHT,
        SyntheticSurfaceFixtures.FIELD_ERROR,
        SyntheticSurfaceFixtures.WEIGHT_ERROR,
        SyntheticSurfaceFixtures.REFRESH_REQUIRED,
        SyntheticSurfaceFixtures.DISCONNECTED,
        SyntheticSurfaceFixtures.COMMAND_IN_FLIGHT,
    ).map { id -> id to firstViewFixture(id, locale) } + listOf(
        "active_minimum" to firstViewFixture(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY, locale)
            .copy(reps = 1, weightHundredthsKg = 0),
        "maximum_set_count" to firstViewFixture(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY, locale)
            .copy(setOrdinal = Int.MAX_VALUE, totalSets = Int.MAX_VALUE),
    )
    var cell by mutableStateOf(FirstViewCell(0, WearScreen.SMALL_ROUND, 1f, fixtures.first().second))
    setContent {
        key(cell.id) {
            WearGateHost(cell.screen, cell.scale) {
                Box(Modifier.fillMaxSize().testTag(FIRST_VIEW_HOST)) {
                    WearControllerScreen(state = cell.model, onAction = {})
                }
            }
        }
    }

    val configurations = WearScreen.entries.flatMap { screen ->
        listOf(1f, LARGEST_WEAR_FONT_SCALE).map { scale -> screen to scale }
    }
    configurations.forEach { (screen, scale) ->
        var enabledActionSize: Size? = null
        fixtures.forEach { (fixture, model) ->
            cell = FirstViewCell(cell.id + 1, screen, scale, model)
            waitForIdle()
            val where = "${locale.toLanguageTag()} $screen scale=$scale fixture=$fixture"
            val scroll = onNodeWithTag("controller_scroll").fetchSemanticsNode()
            assertEquals(0f, scroll.config[SemanticsProperties.VerticalScrollAxisRange].value(), where)
            saveFirstViewCapture("${locale.language}-$screen-$scale-$fixture")
            val actionSize = assertMandatoryContent(model, where)
            if (enabledActionSize == null) enabledActionSize = actionSize
            assertEquals(enabledActionSize, actionSize, "$where enabled/disabled action sizes must match")
            assertDetailsReachable(model, where)
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.saveFirstViewCapture(name: String) {
    val capture = onNodeWithTag(FIRST_VIEW_HOST).captureToImage().asAndroidBitmap()
    val directory = File("build/reports/wear-first-view").apply { mkdirs() }
    File(directory, "$name.png").outputStream().use { output ->
        capture.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertMandatoryContent(model: WearSurfaceModel, where: String): Size {
    assertWholeTarget("reps_card", where)
    assertMergedCardAccessibility(model, "reps_card", where)
    assertWholeNodeInsideRoundScreen("reps_card_icon", where)
    assertTextVisible("reps_value", requireNotNull(model.formattedValues.reps), where)
    if (model.weighted) {
        assertWholeTarget("weight_card", where)
        assertMergedCardAccessibility(model, "weight_card", where)
        assertWholeNodeInsideRoundScreen("weight_card_icon", where)
        assertTextVisible("weight_value", model.formattedValues.weight ?: "—", where)
    } else {
        onNodeWithTag("weight_card").assertDoesNotExist()
    }
    val primary = onNodeWithTag("complete_set")
    if (model.completeEnabled) primary.assertIsEnabled() else primary.assertIsNotEnabled()
    val action = primary.fetchSemanticsNode().wearVisibleBounds(hostRect())
    assertTrue(action.meetsMinimumSize(MIN_FIRST_VIEW_TARGET_DP), "$where primary: ${action.visibleSizeDp}dp")
    assertWholeNodeInsideRoundScreen("complete_glyph", where)
    val reason = model.completionUnavailableReason
    if (model.completeEnabled) {
        assertNull(reason, "$where an actionable completion must not have a blocking reason")
        onNodeWithTag("completion_reason").assertDoesNotExist()
        assertTextVisible(
            "exercise_context",
            model.exerciseName ?: RuntimeEnvironment.getApplication().getString(R.string.exercise_generic),
            where,
            allowEllipsis = true,
        )
    } else {
        assertTextVisible("completion_reason", expectedCompletionReasonText(requireNotNull(reason)), where)
        onNodeWithTag("exercise_context").assertDoesNotExist()
    }
    return action.declaredSizeDp
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertMergedCardAccessibility(model: WearSurfaceModel, tag: String, where: String) {
    val resources = RuntimeEnvironment.getApplication().resources
    val weight = tag == "weight_card"
    val expectedDescriptions = buildList {
        add(resources.getString(if (weight) R.string.weight_label else R.string.reps_label))
        if (weight) {
            add(
                model.formattedValues.weight?.let { resources.getString(R.string.weight_value, it) }
                    ?: resources.getString(R.string.weight_unset),
            )
        }
    }
    val node = onNodeWithTag(tag, useUnmergedTree = false).fetchSemanticsNode()
    assertEquals(
        expectedDescriptions,
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty(),
        "$where $tag merged field label and full spoken value/unit",
    )
    if (!weight) {
        assertEquals(
            listOf(requireNotNull(model.formattedValues.reps)),
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text },
            "$where $tag numeric value must remain in the merged card",
        )
    }
    assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role), "$where $tag button role")
    assertEquals(
        !model.controlsEnabled,
        node.config.contains(SemanticsProperties.Disabled),
        "$where $tag disabled state",
    )
    assertEquals(
        resources.getString(if (model.controlsEnabled) R.string.control_enabled else R.string.control_disabled),
        node.config.getOrNull(SemanticsProperties.StateDescription),
        "$where $tag spoken availability",
    )
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertWholeTarget(tag: String, where: String) {
    val node = onNodeWithTag(tag).fetchSemanticsNode()
    val visible = node.wearVisibleBounds(hostRect())
    assertTrue(
        visible.meetsMinimumSize(MIN_FIRST_VIEW_TARGET_DP),
        "$where $tag: declared=${visible.declaredSizeDp}dp visible=${visible.visibleSizeDp}dp",
    )
    assertWholeNodeVisible(node, "$where $tag")
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertWholeNodeVisible(node: SemanticsNode, where: String) {
    val visible = node.wearVisibleBounds(hostRect()).screenClippedRect
    assertEquals(node.size.width.toFloat(), visible.width, PIXEL_TOLERANCE, "$where horizontal clipping")
    assertEquals(node.size.height.toFloat(), visible.height, PIXEL_TOLERANCE, "$where vertical clipping")
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertWholeNodeInsideRoundScreen(tag: String, where: String) {
    val node = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    assertWholeNodeVisible(node, "$where $tag")
    assertRectInsideRoundScreen(node.boundsInRoot, hostRect(), "$where $tag")
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertTextVisible(
    tag: String,
    expectedText: String,
    where: String,
    allowEllipsis: Boolean = false,
) {
    val node = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    assertEquals(expectedText, node.config[SemanticsProperties.Text].joinToString { it.text }, "$where $tag text")
    assertWholeNodeVisible(node, "$where $tag")
    val layouts = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    assertEquals(1, layouts.size, "$where $tag must expose the rendered text layout")
    val layout = layouts.single()
    if (allowEllipsis) {
        assertTrue(layout.lineCount <= 2, "$where exercise context may abbreviate on at most two lines")
    } else {
        assertFalse(layout.hasVisualOverflow, "$where $tag overflows its own layout")
        repeat(layout.lineCount) { line ->
            assertFalse(layout.isLineEllipsized(line), "$where $tag must not ellipsize")
        }
    }
    val origin = node.positionInRoot
    repeat(layout.lineCount) { line ->
        val rect = Rect(
            origin.x + layout.getLineLeft(line),
            origin.y + layout.getLineTop(line),
            origin.x + layout.getLineRight(line),
            origin.y + layout.getLineBottom(line),
        )
        assertRectInsideRoundScreen(rect, hostRect(), "$where $tag line=$line")
    }
}

private fun assertRectInsideRoundScreen(rect: Rect, screen: Rect, where: String) {
    val center = screen.center
    val radius = minOf(screen.width, screen.height) / 2f
    listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight).forEach { corner ->
        val delta: Offset = corner - center
        assertTrue(
            delta.getDistance() <= radius + PIXEL_TOLERANCE,
            "$where crosses the round screen edge: rect=$rect screen=$screen corner=$corner",
        )
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertDetailsReachable(model: WearSurfaceModel, where: String) {
    onNodeWithTag("controller_details").assertExists()
    onNodeWithTag("exercise_name").performScrollTo()
    waitForIdle()
    val name = onNodeWithTag("exercise_name", useUnmergedTree = true).fetchSemanticsNode()
    assertEquals(
        model.exerciseName ?: RuntimeEnvironment.getApplication().getString(R.string.exercise_generic),
        name.config[SemanticsProperties.Text].joinToString { it.text },
        "$where full exercise name remains in details",
    )
    val nameBounds = name.wearVisibleBounds(hostRect()).visibleSizeDp
    assertTrue(nameBounds.width > 0f && nameBounds.height > 0f, "$where exercise details cannot be reached")
    onNodeWithTag("set_scale").performScrollTo()
    waitForIdle()
    val scale = onNodeWithTag("set_scale").fetchSemanticsNode()
    assertWholeNodeVisible(scale, "$where set scale after scroll")
    assertEquals(
        listOf(RuntimeEnvironment.getApplication().getString(R.string.set_progress, model.setOrdinal, model.totalSets)),
        scale.config[SemanticsProperties.ContentDescription],
        "$where full set progress remains accessible",
    )
}

internal fun expectedCompletionReasonText(reason: CompletionUnavailableReason): String =
    RuntimeEnvironment.getApplication().getString(
        when (reason) {
            CompletionUnavailableReason.DISCONNECTED -> R.string.complete_reason_disconnected
            CompletionUnavailableReason.REFRESH_REQUIRED -> R.string.complete_reason_refresh
            CompletionUnavailableReason.COMMAND_IN_FLIGHT -> R.string.complete_reason_sending
            CompletionUnavailableReason.INVALID_REPS -> R.string.complete_reason_reps
            CompletionUnavailableReason.INVALID_WEIGHT -> R.string.complete_reason_weight
        },
    )

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.hostRect(): Rect = onNodeWithTag(FIRST_VIEW_HOST).fetchSemanticsNode().boundsInRoot

private fun firstViewFixture(id: String, locale: Locale): WearSurfaceModel =
    requireNotNull(SyntheticSurfaceFixtures.find(id)).copy(selectedLocale = locale)

private data class FirstViewCell(val id: Int, val screen: WearScreen, val scale: Float, val model: WearSurfaceModel)

private const val FIRST_VIEW_HOST = "first_view_host"
private const val MIN_FIRST_VIEW_TARGET_DP = 48f
private const val PIXEL_TOLERANCE = 0.5f
