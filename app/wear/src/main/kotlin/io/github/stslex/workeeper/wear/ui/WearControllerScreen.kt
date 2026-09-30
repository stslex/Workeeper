// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.BasicSwipeToDismissBox
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import io.github.stslex.workeeper.core.ui.design.AppDesignDimensions.Space
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.wear.R
import io.github.stslex.workeeper.wear.mvi.store.WearStore

/** Primary values and the blocking reason precede scrollable details; see Wear UI completion §1. */
@Composable
internal fun WearControllerScreen(
    state: WearStore.State,
    consume: (WearStore.Action) -> Unit,
) {
    val current = state.presentation
    WearAppTheme {
        val interactiveState = rememberSaveableStateHolder()
        if (current.ambient.isAmbient) {
            WearAmbientSummary(current.model, current.ambient, hasUnsubmittedValues = current.model.hasUnsubmittedDraft)
        } else {
            interactiveState.SaveableStateProvider("interactive") {
                val editing = state.editor
                if (editing != null) {
                    NumericEditor(
                        field = editing,
                        model = current.model,
                        onAction = consume,
                        onClose = { consume(WearStore.Action.Navigation.CloseEditor) },
                    )
                } else {
                    Controller(
                        model = current.model,
                        ongoingNotice = current.notice,
                        onEnableNotifications = { consume(WearStore.Action.Click.EnableNotifications) },
                        onAction = consume,
                        onEdit = { consume(WearStore.Action.Click.Edit(it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Controller(
    model: WearSurfaceModel,
    ongoingNotice: WearOngoingNotice?,
    onEnableNotifications: () -> Unit,
    onAction: (WearStore.Action) -> Unit,
    onEdit: (NumericField) -> Unit,
) {
    when (model.kind) {
        WearSurfaceKind.ACTIVE,
        WearSurfaceKind.REFRESH_REQUIRED,
        WearSurfaceKind.DISCONNECTED,
        -> ActiveScaffold(model, ongoingNotice, onEnableNotifications, onAction, onEdit)
        WearSurfaceKind.RETRYABLE_ERROR -> RetryScaffold(model, onAction)
        WearSurfaceKind.LOADING,
        WearSurfaceKind.NO_SESSION,
        WearSurfaceKind.PHONE_ACTION_NO_SETS,
        WearSurfaceKind.PHONE_ACTION_UNSUPPORTED,
        WearSurfaceKind.PAYLOAD_TOO_LARGE,
        WearSurfaceKind.WORKOUT_COMPLETE,
        WearSurfaceKind.PROTOCOL_MISMATCH,
        -> InstructionScaffold(model)
    }
}

@Composable
private fun ActiveScaffold(
    model: WearSurfaceModel,
    ongoingNotice: WearOngoingNotice?,
    onEnableNotifications: () -> Unit,
    onAction: (WearStore.Action) -> Unit,
    onEdit: (NumericField) -> Unit,
) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    ScreenScaffold(
        scrollState = scrollState,
        contentPadding = PaddingValues(0.dp),
        timeText = { TimeText() },
    ) { _ ->
        // Keep the action outside the scroll viewport: the scaffold's edge slot hides it until the end.
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = PRIMARY_TOP_INSET.dp, bottom = SMALL_EDGE_CLEARANCE.dp)
                    .requestFocusOnHierarchyActive()
                    .rotaryScrollable(
                        behavior = RotaryScrollableDefaults.behavior(scrollState),
                        focusRequester = focusRequester,
                    )
                    .verticalScroll(scrollState)
                    .padding(horizontal = CONTENT_SIDE_INSET.dp)
                    .testTag("controller_scroll"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                PrimaryContext(model)
                ValueCards(model, onEdit)
                ControllerDetails(model, ongoingNotice, onEnableNotifications)
            }
            CompleteSetButton(model, onAction, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun PrimaryContext(model: WearSurfaceModel) {
    val reason = model.completionUnavailableReason
    val text = reason?.let { stringResource(it.copyResource()) }
        ?: model.exerciseName ?: stringResource(R.string.exercise_generic)
    Box(
        modifier = Modifier.width(PRIMARY_CONTEXT_WIDTH.dp).heightIn(min = PRIMARY_CONTEXT_HEIGHT.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            color = if (reason == null) WearPalette.textPrimary else WearPalette.textSecondary,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = if (reason == null) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = Modifier
                .semantics { heading() }
                .testTag(if (reason == null) "exercise_context" else "completion_reason"),
        )
    }
}

@StringRes
private fun CompletionUnavailableReason.copyResource(): Int = when (this) {
    CompletionUnavailableReason.DISCONNECTED -> R.string.complete_reason_disconnected
    CompletionUnavailableReason.REFRESH_REQUIRED -> R.string.complete_reason_refresh
    CompletionUnavailableReason.COMMAND_IN_FLIGHT -> R.string.complete_reason_sending
    CompletionUnavailableReason.INVALID_REPS -> R.string.complete_reason_reps
    CompletionUnavailableReason.INVALID_WEIGHT -> R.string.complete_reason_weight
}

@Composable
private fun ControllerDetails(
    model: WearSurfaceModel,
    ongoingNotice: WearOngoingNotice?,
    onEnableNotifications: () -> Unit,
) {
    Column(
        modifier = Modifier.width(PRIMARY_CONTEXT_WIDTH.dp).testTag("controller_details"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(COMPACT_CONTENT_SPACE),
    ) {
        StatusRow(model, showDot = true)
        ExerciseName(model)
        SetScale(model)
        FieldError(model)
        OngoingNotice(ongoingNotice, onEnableNotifications)
    }
}

@Composable
private fun RetryScaffold(model: WearSurfaceModel, onAction: (WearStore.Action) -> Unit) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    ScreenScaffold(
        scrollState = scrollState,
        timeText = { TimeText() },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = SMALL_EDGE_CLEARANCE.dp)
                    .requestFocusOnHierarchyActive()
                    .rotaryScrollable(
                        behavior = RotaryScrollableDefaults.behavior(scrollState),
                        focusRequester = focusRequester,
                    )
                    .verticalScroll(scrollState)
                    .padding(contentPadding)
                    .testTag("controller_scroll"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(
                    space = COMPACT_CONTENT_SPACE,
                    alignment = Alignment.CenterVertically,
                ),
            ) {
                StatusRow(model, showDot = false)
            }
            RetryButton(model, onAction, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun InstructionScaffold(model: WearSurfaceModel) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    ScreenScaffold(
        scrollState = scrollState,
        timeText = { TimeText() },
    ) { _ ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val side = minOf(maxWidth, maxHeight) * ROUND_SAFE_FRACTION - ROUND_SAFE_MARGIN.dp
            Column(
                modifier = Modifier
                    .size(side)
                    .align(Alignment.Center)
                    .requestFocusOnHierarchyActive()
                    .rotaryScrollable(
                        behavior = RotaryScrollableDefaults.behavior(scrollState),
                        focusRequester = focusRequester,
                    )
                    .verticalScroll(scrollState)
                    .testTag("controller_scroll"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(
                    space = COMPACT_CONTENT_SPACE,
                    alignment = Alignment.CenterVertically,
                ),
            ) {
                StatusRow(model, showDot = false)
                when (model.kind) {
                    WearSurfaceKind.PHONE_ACTION_NO_SETS,
                    WearSurfaceKind.PHONE_ACTION_UNSUPPORTED,
                    -> PhoneActionContent(model)
                    WearSurfaceKind.PAYLOAD_TOO_LARGE -> GenericWorkoutInstruction()
                    WearSurfaceKind.WORKOUT_COMPLETE -> WorkoutCompleteContent(model)
                    WearSurfaceKind.LOADING,
                    WearSurfaceKind.NO_SESSION,
                    WearSurfaceKind.PROTOCOL_MISMATCH,
                    WearSurfaceKind.ACTIVE,
                    WearSurfaceKind.REFRESH_REQUIRED,
                    WearSurfaceKind.DISCONNECTED,
                    WearSurfaceKind.RETRYABLE_ERROR,
                    -> Unit
                }
            }
        }
    }
}

@Composable
private fun StatusRow(model: WearSurfaceModel, showDot: Boolean) {
    // In ACTIVE the filled dot already says "connected", so the word beside it is nearly
    // redundant and costs a line of a 192dp screen. It is dropped from the DRAWING there and
    // kept on the row's description, so the accessible channel is unchanged. Every degraded
    // state keeps the word visible: there the word is the whole message.
    val word = stringResource(model.statusCopy().resource)
    val drawWord = model.kind != WearSurfaceKind.ACTIVE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Merged: the dot and the word are one status, and TalkBack should say it once.
            .semantics(mergeDescendants = true) {
                heading()
                if (!drawWord) contentDescription = word
            }
            .testTag("status"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDot) {
            ConnectionDot(fresh = model.kind == WearSurfaceKind.ACTIVE)
            if (drawWord) Spacer(Modifier.width(COMPACT_CONTENT_SPACE))
        }
        if (drawWord) {
            Text(
                text = word,
                textAlign = TextAlign.Center,
                color = WearPalette.textPrimary,
                maxLines = STATUS_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

/** Filled when fresh, hollow when not — a shape difference, not only a colour one (§4). */
@Composable
private fun ConnectionDot(fresh: Boolean) {
    val tag = if (fresh) "status_dot_filled" else "status_dot_hollow"
    Canvas(
        modifier = Modifier
            .size(8.dp)
            .testTag(tag),
    ) {
        if (fresh) {
            drawCircle(color = WearPalette.textPrimary)
        } else {
            drawCircle(
                color = WearPalette.textPrimary,
                style = Stroke(width = DOT_RING_WIDTH.dp.toPx()),
            )
        }
    }
}

@Composable
private fun ExerciseName(model: WearSurfaceModel) {
    Text(
        text = model.exerciseName ?: stringResource(R.string.exercise_generic),
        textAlign = TextAlign.Center,
        color = WearPalette.textPrimary,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.testTag("exercise_name"),
    )
}

/** Bounded progress buckets; the full set ordinal and total remain in the spoken description. */
@Composable
private fun SetScale(model: WearSurfaceModel) {
    val total = requireNotNull(model.totalSets)
    val current = requireNotNull(model.setOrdinal)
    // The wording rides the pills instead of occupying a line of its own. §10 forbids relying
    // on a visual channel ALONE, not stating it in the accessible channel — the same trade
    // already made for the unit, for the absent weight, and for the action label.
    val spoken = stringResource(R.string.set_progress, current, total)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md)
            .semantics { contentDescription = spoken }
            .testTag("set_scale"),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        model.setScaleSlots.forEach { slot ->
            SetPill(
                completed = slot.completed,
                current = slot.current,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SetPill(completed: Boolean, current: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(percent = 50)
    val fill = when {
        completed -> WearPalette.textPrimary
        current -> Color.Transparent
        else -> WearPalette.pillPending
    }
    Box(
        modifier = modifier
            .height(PILL_HEIGHT.dp)
            .background(color = fill, shape = shape)
            .border(
                width = DOT_RING_WIDTH.dp,
                color = if (current) WearPalette.textPrimary else Color.Transparent,
                shape = shape,
            ),
    )
}

@Composable
private fun ValueCards(model: WearSurfaceModel, onEdit: (NumericField) -> Unit) {
    if (model.weighted) {
        // Reserve more width for six-character weights than for three-digit reps.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            WeightCard(model, onEdit, modifier = Modifier.weight(WEIGHT_CARD_SHARE))
            RepsCard(model, onEdit, modifier = Modifier.weight(1f))
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            RepsCard(model, onEdit, modifier = Modifier.fillMaxWidth(fraction = LONE_CARD_WIDTH))
        }
    }
}

@Composable
private fun WeightCard(model: WearSurfaceModel, onEdit: (NumericField) -> Unit, modifier: Modifier = Modifier) {
    val formatted = model.formattedValues.weight
    ValueCard(
        icon = R.drawable.ic_weight,
        iconDescription = stringResource(R.string.weight_label),
        // Keep the unit and full unset wording in accessibility and the editor.
        value = formatted ?: UNSET_WEIGHT_MARK,
        valueDescription = formatted?.let { stringResource(R.string.weight_value, it) }
            ?: stringResource(R.string.weight_unset),
        enabled = model.controlsEnabled,
        onClick = { onEdit(NumericField.WEIGHT) },
        tag = "weight_card",
        valueTag = "weight_value",
        modifier = modifier,
    )
}

@Composable
private fun RepsCard(
    model: WearSurfaceModel,
    onEdit: (NumericField) -> Unit,
    modifier: Modifier = Modifier,
) {
    ValueCard(
        icon = R.drawable.ic_reps,
        iconDescription = stringResource(R.string.reps_label),
        // Reps have no unit; the numeral stands alone and needs no spoken embellishment.
        value = requireNotNull(model.formattedValues.reps),
        valueDescription = null,
        enabled = model.controlsEnabled,
        onClick = { onEdit(NumericField.REPS) },
        tag = "reps_card",
        valueTag = "reps_value",
        modifier = modifier,
    )
}

/**
 * A value card of §4: an icon identifying the field, and the value below it — the weight's unit
 * inside the value, reps bare. The icon replaces a textual header because it is
 * locale-independent and cannot regress on translation, and because one row does not amortise
 * a column header. Read-only cards lose their fill for [WearPalette.cardInactive], keep a
 * [WearPalette.stroke] outline, and move their content to [WearPalette.textMuted] — a shape
 * change on top of the status-row text change.
 */
@Composable
private fun ValueCard(
    @DrawableRes icon: Int,
    iconDescription: String,
    value: String,
    valueDescription: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    valueTag: String,
    modifier: Modifier = Modifier,
) {
    val enabledDescription = stringResource(R.string.control_enabled)
    val disabledDescription = stringResource(R.string.control_disabled)
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .heightIn(min = PRIMARY_CARD_HEIGHT.dp)
            .clip(shape)
            .background(color = if (enabled) WearPalette.card else WearPalette.cardInactive, shape = shape)
            .border(width = 1.dp, color = if (enabled) Color.Transparent else WearPalette.stroke, shape = shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                stateDescription = if (enabled) enabledDescription else disabledDescription
            }
            .padding(vertical = COMPACT_CONTENT_SPACE, horizontal = Space.sm)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xxs, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = iconDescription,
            tint = if (enabled) WearPalette.textSecondary else WearPalette.textMuted,
            modifier = Modifier.size(CARD_ICON.dp).testTag("${tag}_icon"),
        )
        Text(
            text = value,
            color = if (enabled) WearPalette.textPrimary else WearPalette.textMuted,
            style = MaterialTheme.typography.numeralExtraSmall,
            maxLines = 1,
            modifier = Modifier
                .semantics { valueDescription?.let { contentDescription = it } }
                .testTag(valueTag),
        )
    }
}

@Composable
private fun FieldError(model: WearSurfaceModel) {
    val field = model.fieldError ?: return
    val message = stringResource(
        if (field == NumericField.REPS) R.string.reps_invalid else R.string.weight_invalid,
    )
    Text(
        text = message,
        color = WearPalette.error,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .semantics { error(message) }
            .testTag("field_error"),
    )
}

/** The disabled reason occupies the primary header; both action states retain the same visible target. */
@Composable
private fun CompleteSetButton(
    model: WearSurfaceModel,
    onAction: (WearStore.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabledDescription = stringResource(R.string.complete_set_enabled_description)
    val disabledDescription = stringResource(R.string.complete_set_disabled_description)
    EdgeButton(
        onClick = { onAction(WearStore.Action.Click.Complete) },
        enabled = model.completeEnabled,
        buttonSize = EdgeButtonSize.Small,
        colors = ButtonDefaults.buttonColors(
            containerColor = WearPalette.textPrimary,
            contentColor = WearPalette.onAccent,
            disabledContainerColor = WearPalette.screen,
            disabledContentColor = WearPalette.textMuted,
        ),
        border = if (model.completeEnabled) null else BorderStroke(1.dp, WearPalette.stroke),
        modifier = modifier
            .semantics {
                contentDescription = if (model.completeEnabled) enabledDescription else disabledDescription
            }
            .testTag("complete_set"),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_complete),
            contentDescription = null,
            modifier = Modifier.size(COMPLETE_GLYPH.dp).testTag("complete_glyph"),
        )
    }
}

@Composable
private fun RetryButton(
    model: WearSurfaceModel,
    onAction: (WearStore.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    EdgeButton(
        onClick = { onAction(WearStore.Action.Click.Retry) },
        enabled = model.retryEnabled,
        buttonSize = EdgeButtonSize.Small,
        colors = ButtonDefaults.buttonColors(
            containerColor = WearPalette.textPrimary,
            contentColor = WearPalette.onAccent,
            disabledContainerColor = WearPalette.screen,
            disabledContentColor = WearPalette.textMuted,
        ),
        border = if (model.retryEnabled) null else BorderStroke(1.dp, WearPalette.stroke),
        modifier = modifier.testTag("retry"),
    ) {
        // labelSmall: «Повторить» is one unbreakable word and must fit the Small arc at the
        // largest font scale — G6 owns this bound.
        Text(
            text = stringResource(R.string.retry),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun PhoneActionContent(model: WearSurfaceModel) {
    Text(
        text = model.exerciseName ?: stringResource(R.string.exercise_generic),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        color = WearPalette.textSecondary,
        modifier = Modifier.testTag("exercise_name"),
    )
}

@Composable
private fun GenericWorkoutInstruction() {
    Text(
        text = stringResource(R.string.workout_generic),
        color = WearPalette.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("workout_generic"),
    )
}

@Composable
private fun WorkoutCompleteContent(model: WearSurfaceModel) {
    Text(
        text = model.trainingName ?: stringResource(R.string.workout_generic),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        color = WearPalette.textPrimary,
        modifier = Modifier.testTag("training_name"),
    )
    Text(
        text = pluralStringResource(
            R.plurals.exercise_progress,
            requireNotNull(model.totalExercises),
            requireNotNull(model.completedExercises),
            model.totalExercises,
        ),
        color = WearPalette.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("exercise_progress"),
    )
    Text(
        text = stringResource(R.string.finish_on_phone),
        color = WearPalette.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("finish_on_phone"),
    )
}

/**
 * The full-screen numeric editor of §5: one value, large, increment at the top arc, decrement
 * at the bottom arc. Every step emits the existing draft action immediately, so leaving the
 * editor — swipe to dismiss or hardware back — loses nothing; there is no unconfirmed state.
 * Rotary input edits the value here and scrolls content on the controller.
 */
@Composable
private fun NumericEditor(
    field: NumericField,
    model: WearSurfaceModel,
    onAction: (WearStore.Action) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val incrementEnabled = if (field == NumericField.REPS) {
        model.incrementRepsEnabled
    } else {
        model.incrementWeightEnabled
    }
    val decrementEnabled = if (field == NumericField.REPS) {
        model.decrementRepsEnabled
    } else {
        model.decrementWeightEnabled
    }
    BasicSwipeToDismissBox(
        onDismissed = onClose,
        modifier = Modifier.testTag("editor"),
    ) { isBackground ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WearPalette.screen),
        ) {
            if (!isBackground) {
                EditorContent(
                    field = field,
                    model = model,
                    onAdjust = { steps -> onAction(WearStore.Action.Input.Draft(field, steps)) },
                    incrementEnabled = incrementEnabled,
                    decrementEnabled = decrementEnabled,
                )
            }
        }
    }
}

@Composable
private fun EditorContent(
    field: NumericField,
    model: WearSurfaceModel,
    onAdjust: (Int) -> Unit,
    incrementEnabled: Boolean,
    decrementEnabled: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    var rotaryAccumulator by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .requestFocusOnHierarchyActive()
            .onRotaryScrollEvent { event ->
                rotaryAccumulator += event.verticalScrollPixels
                val steps = (rotaryAccumulator / ROTARY_STEP_PX).toInt()
                if (steps != 0) {
                    rotaryAccumulator -= steps * ROTARY_STEP_PX
                    onAdjust(steps)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .testTag("editor_rotary"),
    ) {
        val reps = model.reps
        val displayedValue: String
        val numericValue = if (field == NumericField.REPS) {
            model.formattedValues.reps
        } else {
            model.formattedValues.weight
        }
        displayedValue = if (field == NumericField.REPS) {
            requireNotNull(numericValue)
        } else {
            numericValue?.let { stringResource(R.string.weight_value, it) } ?: stringResource(R.string.weight_unset)
        }
        val numericStyle = if (field == NumericField.REPS) {
            MaterialTheme.typography.numeralMedium
        } else {
            MaterialTheme.typography.numeralSmall
        }
        val auxiliaryStyle = MaterialTheme.typography.bodyExtraSmall
        val valueStyle = if (numericValue == null) {
            MaterialTheme.typography.bodyLarge
        } else {
            numericStyle.copy(fontFamily = MaterialTheme.typography.bodyLarge.fontFamily)
        }
        val valueText = remember(displayedValue, numericValue, numericStyle, auxiliaryStyle) {
            val numberStart = numericValue?.let(displayedValue::indexOf) ?: -1
            if (numberStart < 0 || numericValue == null) {
                AnnotatedString(displayedValue)
            } else {
                buildAnnotatedString {
                    withStyle(auxiliaryStyle.toSpanStyle()) { append(displayedValue.substring(0, numberStart)) }
                    withStyle(numericStyle.toSpanStyle()) { append(numericValue) }
                    withStyle(auxiliaryStyle.toSpanStyle()) {
                        append(displayedValue.substring(numberStart + numericValue.length))
                    }
                }
            }
        }
        EditorStepButton(
            icon = R.drawable.ic_increment,
            description = if (field == NumericField.REPS) {
                stringResource(R.string.increase_reps, requireNotNull(reps))
            } else {
                stringResource(R.string.increase_weight, displayedValue)
            },
            enabled = incrementEnabled,
            onClick = { onAdjust(1) },
            tag = "editor_increase",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp),
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.xxs),
        ) {
            Text(
                text = if (field == NumericField.REPS) {
                    stringResource(R.string.reps_label)
                } else {
                    stringResource(R.string.weight_label)
                },
                color = WearPalette.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.testTag("editor_label"),
            )
            Text(
                text = valueText,
                color = WearPalette.textPrimary,
                style = valueStyle,
                maxLines = 1,
                modifier = Modifier.testTag("editor_value"),
            )
        }
        EditorStepButton(
            icon = R.drawable.ic_decrement,
            description = if (field == NumericField.REPS) {
                stringResource(R.string.decrease_reps, requireNotNull(reps))
            } else {
                stringResource(R.string.decrease_weight, displayedValue)
            },
            enabled = decrementEnabled,
            onClick = { onAdjust(-1) },
            tag = "editor_decrease",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
        )
    }
}

@Composable
private fun EditorStepButton(
    @DrawableRes icon: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val enabledDescription = stringResource(R.string.control_enabled)
    val disabledDescription = stringResource(R.string.control_disabled)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(shape = MaterialTheme.shapes.medium),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = WearPalette.textPrimary,
            contentColor = WearPalette.onAccent,
            disabledContainerColor = WearPalette.cardInactive,
            disabledContentColor = WearPalette.textMuted,
        ),
        modifier = modifier
            .size(48.dp)
            .semantics {
                contentDescription = description
                stateDescription = if (enabled) enabledDescription else disabledDescription
            }
            .testTag(tag),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp).testTag("${tag}_glyph"),
        )
    }
}

private val COMPACT_CONTENT_SPACE = Space.xs + Space.xxs

private const val STATUS_MAX_LINES = 4
private const val DOT_RING_WIDTH = 1.5
private const val PILL_HEIGHT = 6
private const val LONE_CARD_WIDTH = 0.55f

/** Em dash — the conventional marker for an absent value in a numeric field. */
private const val UNSET_WEIGHT_MARK = "\u2014"

/** The value card's field icon, sized to sit under the value without competing with it. */
private const val CARD_ICON = 16

/** The primary action's check glyph. Larger than a card icon: it is the action itself. */
private const val COMPLETE_GLYPH = 16

/**
 * Extra width for the weight; see wear-style-mvi.md#card-sizing-provenance.
 */
private const val WEIGHT_CARD_SHARE = 1.533f
private const val ROTARY_STEP_PX = 48f

/** Viewport inset above a Small (56dp) anchored edge button, its outer padding included. */
private const val SMALL_EDGE_CLEARANCE = 62
/** Sides of the content column; the §4 stack sits in the wide middle band of the circle. */
private const val CONTENT_SIDE_INSET = 16

/** Clears the top-arc time text. */
private const val PRIMARY_TOP_INSET = 26
private const val PRIMARY_CONTEXT_WIDTH = 128
private const val PRIMARY_CONTEXT_HEIGHT = 42
private const val PRIMARY_CARD_HEIGHT = 54

// The inset square stays inside the circle at every visible row; see wear-style-mvi.md#geometry.
private const val ROUND_SAFE_FRACTION = 0.70710677f
private const val ROUND_SAFE_MARGIN = 4
