// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.acceptance

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import io.github.stslex.workeeper.wear.R
import io.github.stslex.workeeper.wear.ui.CompletionUnavailableReason
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
import io.github.stslex.workeeper.wear.ui.WearSurfaceModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

internal fun WearAcceptanceSession.assertFirstView(model: WearSurfaceModel) {
    assertEquals("First view precedes scrolling", 0f, scrollPosition(), PIXEL_TOLERANCE)
    if (model.controlsVisible) {
        assertPrimaryValues(model)
    } else {
        rule.onNodeWithTag("complete_set").assertDoesNotExist()
        rule.onNodeWithTag("reps_card").assertDoesNotExist()
        rule.onNodeWithTag("weight_card").assertDoesNotExist()
        rule.onNodeWithTag("status").assertIsDisplayed()
        val status = node("status", merged = true)
        assertEquals(activity.getString(statusResource(model.kind)), textOf(status))
        val statusText = rule.onNodeWithText(activity.getString(statusResource(model.kind)), useUnmergedTree = true)
            .fetchSemanticsNode()
        assertTextLayout(statusText, "status", allowEllipsis = false)
        if (model.kind == WearSurfaceKind.RETRYABLE_ERROR) {
            assertTarget("retry")
            rule.onNodeWithTag("retry").assertIsEnabled()
        }
    }
}

private fun WearAcceptanceSession.assertPrimaryValues(model: WearSurfaceModel) {
    assertCard(model, "reps_card")
    assertText("reps_value", requireNotNull(model.formattedValues.reps))
    assertRoundNode("reps_card_icon")
    if (model.weighted) {
        assertCard(model, "weight_card")
        assertText("weight_value", model.formattedValues.weight ?: "—")
        assertRoundNode("weight_card_icon")
    } else {
        rule.onNodeWithTag("weight_card").assertDoesNotExist()
    }
    assertTarget("complete_set")
    assertRoundNode("complete_glyph")
    assertPrimarySeparation(model)
    val button = rule.onNodeWithTag("complete_set")
    if (model.completeEnabled) button.assertIsEnabled() else button.assertIsNotEnabled()
    val actionDescription = if (model.completeEnabled) {
        R.string.complete_set_enabled_description
    } else {
        R.string.complete_set_disabled_description
    }
    assertEquals(
        listOf(activity.getString(actionDescription)),
        node("complete_set", merged = true).config.getOrNull(SemanticsProperties.ContentDescription),
    )
    val reason = model.completionUnavailableReason
    if (reason == null) {
        assertTrue("Enabled action has no blocking reason", model.completeEnabled)
        rule.onNodeWithTag("completion_reason").assertDoesNotExist()
        assertText("exercise_context", model.exerciseName ?: activity.getString(R.string.exercise_generic), true)
    } else {
        assertFalse("Blocked action", model.completeEnabled)
        assertText("completion_reason", activity.getString(reasonResource(reason)))
    }
}

private fun WearAcceptanceSession.assertCard(model: WearSurfaceModel, tag: String) {
    assertTarget(tag, whole = true)
    val card = node(tag, merged = true)
    val weight = tag == "weight_card"
    val descriptions = buildList {
        add(activity.getString(if (weight) R.string.weight_label else R.string.reps_label))
        if (weight) {
            add(model.formattedValues.weight?.let { activity.getString(R.string.weight_value, it) }
                ?: activity.getString(R.string.weight_unset))
        }
    }
    assertEquals(
        "$tag spoken field/value/unit", descriptions, card.config.getOrNull(SemanticsProperties.ContentDescription),
    )
    if (!weight) assertEquals("Reps in merged accessibility tree", model.formattedValues.reps, textOf(card))
    assertEquals("$tag role", Role.Button, card.config.getOrNull(SemanticsProperties.Role))
    assertEquals("$tag availability", !model.controlsEnabled, card.config.contains(SemanticsProperties.Disabled))
    assertEquals(
        "$tag spoken state",
        activity.getString(if (model.controlsEnabled) R.string.control_enabled else R.string.control_disabled),
        card.config.getOrNull(SemanticsProperties.StateDescription),
    )
    assertTrue("$tag click semantics", card.config.contains(SemanticsActions.OnClick))
}

internal fun WearAcceptanceSession.assertDetails(model: WearSurfaceModel) {
    if (!model.controlsVisible) return
    rule.onNodeWithTag("exercise_name").performScrollTo().assertIsDisplayed()
    val name = node("exercise_name")
    assertEquals(
        "Full exercise remains in details",
        model.exerciseName ?: activity.getString(R.string.exercise_generic),
        textOf(name),
    )
    val layouts = mutableListOf<TextLayoutResult>()
    name.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    assertEquals(1, layouts.size)
    assertFalse("Full exercise must wrap without layout overflow", layouts.single().hasVisualOverflow)
    capture("details-name")
    rule.onNodeWithTag("set_scale").performScrollTo().assertIsDisplayed()
    assertWhole(node("set_scale"), "set_scale")
    assertEquals(
        listOf(activity.getString(R.string.set_progress, model.setOrdinal, model.totalSets)),
        node("set_scale", merged = true).config.getOrNull(SemanticsProperties.ContentDescription),
    )
    capture("details-scale")
}

internal fun WearAcceptanceSession.assertTarget(tag: String, whole: Boolean = false) {
    val target = node(tag, merged = true)
    val visible = target.boundsInRoot.intersect(screen())
    val density = target.layoutInfo.density.density
    assertTrue("$tag visible width below 48dp: $visible", visible.width / density >= MIN_TARGET_DP - DP_TOLERANCE)
    assertTrue("$tag visible height below 48dp: $visible", visible.height / density >= MIN_TARGET_DP - DP_TOLERANCE)
    if (whole) assertWhole(target, tag)
}

internal fun WearAcceptanceSession.assertText(tag: String, expected: String, allowEllipsis: Boolean = false) {
    val text = node(tag)
    assertEquals("$tag text", expected, textOf(text))
    assertTextLayout(text, tag, allowEllipsis)
}

private fun WearAcceptanceSession.assertTextLayout(
    text: SemanticsNode,
    tag: String,
    allowEllipsis: Boolean,
) {
    assertWhole(text, tag)
    val layouts = mutableListOf<TextLayoutResult>()
    text.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    assertEquals("$tag actual rendered text layout", 1, layouts.size)
    val layout = layouts.single()
    if (allowEllipsis) assertTrue("$tag at most two context lines", layout.lineCount <= 2)
    if (!allowEllipsis) assertFalse("$tag visual overflow", layout.hasVisualOverflow)
    repeat(layout.lineCount) { line ->
        if (!allowEllipsis) assertFalse("$tag ellipsis", layout.isLineEllipsized(line))
        val origin = text.positionInRoot
        assertRound(
            Rect(origin.x + layout.getLineLeft(line), origin.y + layout.getLineTop(line),
                origin.x + layout.getLineRight(line), origin.y + layout.getLineBottom(line)),
            "$tag line $line",
        )
    }
}

private fun WearAcceptanceSession.assertPrimarySeparation(model: WearSurfaceModel) {
    val tags = buildList {
        add("reps_card")
        if (model.weighted) add("weight_card")
        add("complete_set")
        add(if (model.completionUnavailableReason == null) "exercise_context" else "completion_reason")
    }
    tags.forEachIndexed { index, first ->
        tags.drop(index + 1).forEach { second ->
            val overlap = node(first).boundsInRoot.intersect(node(second).boundsInRoot)
            assertTrue(
                "Primary siblings overlap: $first/$second $overlap",
                overlap.width <= PIXEL_TOLERANCE || overlap.height <= PIXEL_TOLERANCE,
            )
        }
    }
}

private fun WearAcceptanceSession.assertRoundNode(tag: String) {
    val value = node(tag)
    assertWhole(value, tag)
    assertRound(value.boundsInRoot, tag)
}

private fun WearAcceptanceSession.assertRound(rect: Rect, label: String) {
    val display = screen()
    val radius = minOf(display.width, display.height) / 2f
    listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight).forEach { corner ->
        assertTrue(
            "$label crosses real round-screen edge: $rect",
            (corner - display.center).getDistance() <= radius + PIXEL_TOLERANCE,
        )
    }
}

private fun WearAcceptanceSession.assertWhole(value: SemanticsNode, label: String) {
    assertTrue("$label placed", value.layoutInfo.isAttached && value.layoutInfo.isPlaced)
    val visible = value.boundsInRoot.intersect(screen())
    assertEquals(
        "$label horizontal ancestor/screen clipping", value.size.width.toFloat(), visible.width, PIXEL_TOLERANCE,
    )
    assertEquals(
        "$label vertical ancestor/screen clipping", value.size.height.toFloat(), visible.height, PIXEL_TOLERANCE,
    )
}

internal fun WearAcceptanceSession.node(tag: String, merged: Boolean = false): SemanticsNode =
    rule.onNodeWithTag(tag, useUnmergedTree = !merged).fetchSemanticsNode()

internal fun WearAcceptanceSession.scrollPosition(): Float =
    node("controller_scroll").config[SemanticsProperties.VerticalScrollAxisRange].value()

private fun WearAcceptanceSession.screen(): Rect = rule.onRoot().fetchSemanticsNode().boundsInRoot

private fun textOf(node: SemanticsNode): String =
    node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }

private fun reasonResource(reason: CompletionUnavailableReason): Int = when (reason) {
    CompletionUnavailableReason.DISCONNECTED -> R.string.complete_reason_disconnected
    CompletionUnavailableReason.REFRESH_REQUIRED -> R.string.complete_reason_refresh
    CompletionUnavailableReason.COMMAND_IN_FLIGHT -> R.string.complete_reason_sending
    CompletionUnavailableReason.INVALID_REPS -> R.string.complete_reason_reps
    CompletionUnavailableReason.INVALID_WEIGHT -> R.string.complete_reason_weight
}

private fun statusResource(kind: WearSurfaceKind): Int = when (kind) {
    WearSurfaceKind.LOADING -> R.string.loading
    WearSurfaceKind.NO_SESSION -> R.string.start_workout_on_phone
    WearSurfaceKind.ACTIVE -> R.string.ready
    WearSurfaceKind.PHONE_ACTION_NO_SETS -> R.string.add_set_on_phone
    WearSurfaceKind.PHONE_ACTION_UNSUPPORTED -> R.string.edit_set_on_phone
    WearSurfaceKind.PAYLOAD_TOO_LARGE -> R.string.open_workout_on_phone
    WearSurfaceKind.WORKOUT_COMPLETE -> R.string.workout_complete
    WearSurfaceKind.REFRESH_REQUIRED -> R.string.refresh_required
    WearSurfaceKind.DISCONNECTED -> R.string.phone_unavailable
    WearSurfaceKind.RETRYABLE_ERROR -> R.string.transport_error
    WearSurfaceKind.PROTOCOL_MISMATCH -> R.string.update_required
}

private const val MIN_TARGET_DP = 48f
private const val DP_TOLERANCE = 0.01f
private const val PIXEL_TOLERANCE = 0.5f
