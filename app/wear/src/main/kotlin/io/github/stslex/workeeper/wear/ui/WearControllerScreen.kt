// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.BasicSwipeToDismissBox
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.wear.R
import java.text.NumberFormat

/**
 * The Wear controller, laid out per the controller redesign spec §4: time at the top arc,
 * a connection dot beside the status word, the exercise name, the set scale with the same
 * information repeated as words, two tappable value cards, and `Complete set` anchored to the
 * bottom edge (D-E). The training name is dropped from this screen by decision D-F.
 *
 * Tapping a value card opens the full-screen numeric editor of §5, which emits the existing
 * [ControllerAction.SetReps] / [ControllerAction.SetWeight] — no new action type, no new state.
 */
@Composable
internal fun WearControllerScreen(
    state: WearSurfaceState,
    onAction: (ControllerAction) -> Unit,
) {
    WearAppTheme {
        var editingField by rememberSaveable { mutableStateOf<NumericField?>(null) }
        val editing = editingField?.takeIf { field ->
            state.controlsEnabled && (field == NumericField.REPS || state.weighted)
        }
        if (editing == null && editingField != null) {
            SideEffect { editingField = null }
        }
        if (editing != null) {
            NumericEditor(
                field = editing,
                model = state,
                onAction = onAction,
                onClose = { editingField = null },
            )
        } else {
            Controller(
                model = state,
                onAction = onAction,
                onEdit = { editingField = it },
            )
        }
    }
}

@Composable
private fun Controller(
    model: WearSurfaceModel,
    onAction: (ControllerAction) -> Unit,
    onEdit: (NumericField) -> Unit,
) {
    when (model.kind) {
        WearSurfaceKind.ACTIVE,
        WearSurfaceKind.REFRESH_REQUIRED,
        WearSurfaceKind.DISCONNECTED,
        -> ActiveScaffold(model, onAction, onEdit)
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
    onAction: (ControllerAction) -> Unit,
    onEdit: (NumericField) -> Unit,
) {
    val scrollState = rememberScrollState()
    val clearance = MEDIUM_EDGE_CLEARANCE.dp
    ScreenScaffold(
        scrollState = scrollState,
        contentPadding = activeContentPadding(),
        timeText = { TimeText() },
    ) { contentPadding ->
        // The scaffold's own edge-button slot scales the button away until the user scrolls to
        // the end — the primary action below the fold is the exact defect D-E removes. The
        // button is anchored statically instead, and the scroll VIEWPORT is inset above it
        // (padding before verticalScroll), so content can never sit under the button at any
        // font scale: what does not fit is clipped un-tappable until scrolled into view.
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = clearance)
                    .verticalScroll(scrollState)
                    .padding(contentPadding)
                    .testTag("controller_scroll"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StatusRow(model, showDot = true)
                ExerciseName(model)
                SetScale(model)
                Text(
                    text = stringResource(
                        R.string.set_progress,
                        requireNotNull(model.setOrdinal),
                        requireNotNull(model.totalSets),
                    ),
                    color = WearPalette.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("set_progress"),
                )
                ValueCards(model, onEdit)
                FieldError(model)
            }
            CompleteSetButton(model, onAction, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** An explicit, deterministic inset: the §4 stack is budgeted against it, not a default. */
@Composable
private fun activeContentPadding() = PaddingValues(
    start = CONTENT_SIDE_INSET.dp,
    end = CONTENT_SIDE_INSET.dp,
    top = CONTENT_TOP_INSET.dp,
)

@Composable
private fun RetryScaffold(model: WearSurfaceModel, onAction: (ControllerAction) -> Unit) {
    val scrollState = rememberScrollState()
    ScreenScaffold(
        scrollState = scrollState,
        timeText = { TimeText() },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = SMALL_EDGE_CLEARANCE.dp)
                    .verticalScroll(scrollState)
                    .padding(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(space = 6.dp, alignment = Alignment.CenterVertically),
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
    ScreenScaffold(
        scrollState = scrollState,
        timeText = { TimeText() },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space = 6.dp, alignment = Alignment.CenterVertically),
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

@Composable
private fun StatusRow(model: WearSurfaceModel, showDot: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDot) {
            ConnectionDot(fresh = model.kind == WearSurfaceKind.ACTIVE)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = stringResource(model.statusCopy().resource),
            modifier = Modifier
                .semantics { heading() }
                .testTag("status"),
            textAlign = TextAlign.Center,
            color = WearPalette.textPrimary,
            maxLines = STATUS_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
        )
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
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        color = WearPalette.textPrimary,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.testTag("exercise_name"),
    )
}

/** One pill per set: completed filled, the current one outlined, pending [WearPalette.pillPending]. */
@Composable
private fun SetScale(model: WearSurfaceModel) {
    val total = requireNotNull(model.totalSets)
    val current = requireNotNull(model.setOrdinal)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .testTag("set_scale"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(total) { index ->
            SetPill(
                completed = index < current - 1,
                current = index == current - 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SetPill(completed: Boolean, current: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(percent = 50)
    val base = modifier.height(PILL_HEIGHT.dp)
    Box(
        modifier = when {
            completed -> base.background(color = WearPalette.textPrimary, shape = shape)
            current -> base.border(width = DOT_RING_WIDTH.dp, color = WearPalette.textPrimary, shape = shape)
            else -> base.background(color = WearPalette.pillPending, shape = shape)
        },
    )
}

@Composable
private fun ValueCards(model: WearSurfaceModel, onEdit: (NumericField) -> Unit) {
    val reps = requireNotNull(model.reps)
    if (model.weighted) {
        // Deliberately UNEQUAL. Reps are at most three digits; a weight carries up to six
        // characters, so equal halves starve one and waste the other. Measured at font scale
        // 1.24, the binding case: «999.99» needs 65dp of content and «999» needs 36dp, and
        // this split gives them 76dp and 44dp on a 192dp screen. See WEIGHT_CARD_SHARE.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WeightCard(model, onEdit, modifier = Modifier.weight(WEIGHT_CARD_SHARE))
            RepsCard(model, reps, onEdit, modifier = Modifier.weight(1f))
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            RepsCard(model, reps, onEdit, modifier = Modifier.fillMaxWidth(fraction = LONE_CARD_WIDTH))
        }
    }
}

@Composable
private fun WeightCard(model: WearSurfaceModel, onEdit: (NumericField) -> Unit, modifier: Modifier = Modifier) {
    val weight = model.weightHundredthsKg
    val formatted = weight?.let(::formatWeight)
    ValueCard(
        icon = R.drawable.ic_weight,
        iconDescription = stringResource(R.string.weight_label),
        // The card shows a bare numeral. The app is kilograms only — no module offers a unit
        // choice — so «kg» on the card is a constant, and a constant does not earn the width
        // it costs: with it, no split of a 192dp row fits «999.99 kg» (108 + 52 + 8 > 160).
        // The unit still reaches the user in the full-screen editor, and reaches TalkBack here
        // through the value's content description, so no channel loses it.
        value = formatted ?: stringResource(R.string.weight_unset),
        valueDescription = formatted?.let { stringResource(R.string.weight_value, it) },
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
    reps: Int,
    onEdit: (NumericField) -> Unit,
    modifier: Modifier = Modifier,
) {
    ValueCard(
        icon = R.drawable.ic_reps,
        iconDescription = stringResource(R.string.reps_label),
        // Reps have no unit; the numeral stands alone and needs no spoken embellishment.
        value = reps.toString(),
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
    val surface = if (enabled) {
        Modifier.background(color = WearPalette.card, shape = shape)
    } else {
        Modifier
            .background(color = WearPalette.cardInactive, shape = shape)
            .border(width = 1.dp, color = WearPalette.stroke, shape = shape)
    }
    Column(
        modifier = modifier
            .clip(shape)
            .then(surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                stateDescription = if (enabled) enabledDescription else disabledDescription
            }
            .padding(vertical = 6.dp, horizontal = 8.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = iconDescription,
            tint = if (enabled) WearPalette.textSecondary else WearPalette.textMuted,
            modifier = Modifier.size(CARD_ICON.dp),
        )
        Text(
            text = value,
            color = if (enabled) WearPalette.textPrimary else WearPalette.textMuted,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
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

/**
 * `Complete set`, anchored to the bottom edge (D-E). Disabled it inverts from filled to
 * outlined and gains the word `control_disabled` beneath its label — shape and text, not colour
 * alone (§4); the taller size makes room for that extra line.
 */
@Composable
private fun CompleteSetButton(
    model: WearSurfaceModel,
    onAction: (ControllerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabledDescription = stringResource(R.string.complete_set_enabled_description)
    val disabledDescription = stringResource(R.string.complete_set_disabled_description)
    EdgeButton(
        onClick = { onAction(ControllerAction.CompleteSet) },
        enabled = model.completeEnabled,
        // Medium in BOTH states: the disabled form needs the extra line for the disabled word,
        // and the enabled primary action may never be smaller than the disabled one (G7).
        buttonSize = EdgeButtonSize.Medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = WearPalette.textPrimary,
            contentColor = WearPalette.onAccent,
            disabledContainerColor = WearPalette.screen,
            disabledContentColor = WearPalette.textMuted,
        ),
        border = if (model.completeEnabled) null else BorderStroke(1.dp, WearPalette.stroke),
        modifier = modifier
            .semantics {
                contentDescription = if (model.completeEnabled) {
                    enabledDescription
                } else {
                    disabledDescription
                }
            }
            .testTag("complete_set"),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.complete_set),
                style = if (model.completeEnabled) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.labelSmall
                },
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
            if (!model.completeEnabled) {
                Text(
                    text = stringResource(R.string.control_disabled),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.testTag("complete_unavailable"),
                )
            }
        }
    }
}

@Composable
private fun RetryButton(
    model: WearSurfaceModel,
    onAction: (ControllerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    EdgeButton(
        onClick = { onAction(ControllerAction.Retry) },
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
        modifier = Modifier.testTag("exercise_progress"),
    )
    Text(
        text = stringResource(R.string.finish_on_phone),
        color = WearPalette.textSecondary,
        modifier = Modifier.testTag("finish_on_phone"),
    )
}

/**
 * The full-screen numeric editor of §5: one value, large, increment at the top arc, decrement
 * at the bottom arc. Every step emits the existing draft action immediately, so leaving the
 * editor — swipe to dismiss or hardware back — loses nothing; there is no unconfirmed state.
 * This is the only screen where rotary input is bound, so no mode indicator is required.
 */
@Composable
private fun NumericEditor(
    field: NumericField,
    model: WearSurfaceModel,
    onAction: (ControllerAction) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val increment: () -> Unit
    val decrement: () -> Unit
    val incrementEnabled: Boolean
    val decrementEnabled: Boolean
    if (field == NumericField.REPS) {
        val reps = requireNotNull(model.reps)
        val up = WearDraftPolicy.incrementReps(reps)
        val down = WearDraftPolicy.decrementReps(reps)
        increment = { up?.let { onAction(ControllerAction.SetReps(it)) } }
        decrement = { down?.let { onAction(ControllerAction.SetReps(it)) } }
        incrementEnabled = up != null
        decrementEnabled = down != null
    } else {
        val weight = model.weightHundredthsKg
        val up = WearDraftPolicy.incrementWeight(weight)
        val down = WearDraftPolicy.decrementWeight(weight)
        increment = { up?.let { onAction(ControllerAction.SetWeight(it)) } }
        decrement = { down?.let { onAction(ControllerAction.SetWeight(it.value)) } }
        incrementEnabled = up != null
        decrementEnabled = down != null
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
                    increment = increment,
                    decrement = decrement,
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
    increment: () -> Unit,
    decrement: () -> Unit,
    incrementEnabled: Boolean,
    decrementEnabled: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    var rotaryAccumulator by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                rotaryAccumulator += event.verticalScrollPixels
                while (rotaryAccumulator >= ROTARY_STEP_PX) {
                    rotaryAccumulator -= ROTARY_STEP_PX
                    increment()
                }
                while (rotaryAccumulator <= -ROTARY_STEP_PX) {
                    rotaryAccumulator += ROTARY_STEP_PX
                    decrement()
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        val reps = model.reps
        val weight = model.weightHundredthsKg
        val displayedValue: String
        val valueStyle = if (field == NumericField.REPS) {
            displayedValue = requireNotNull(reps).toString()
            MaterialTheme.typography.numeralMedium
        } else {
            displayedValue = weight?.let { stringResource(R.string.weight_value, formatWeight(it)) }
                ?: stringResource(R.string.weight_unset)
            MaterialTheme.typography.numeralExtraSmall
        }
        EditorStepButton(
            glyph = "+",
            description = if (field == NumericField.REPS) {
                stringResource(R.string.increase_reps, requireNotNull(reps))
            } else {
                stringResource(R.string.increase_weight, displayedValue)
            },
            enabled = incrementEnabled,
            onClick = increment,
            tag = "editor_increase",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp),
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
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
                text = displayedValue,
                color = WearPalette.textPrimary,
                style = valueStyle.copy(fontFeatureSettings = "tnum"),
                maxLines = 1,
                modifier = Modifier.testTag("editor_value"),
            )
        }
        EditorStepButton(
            glyph = "−",
            description = if (field == NumericField.REPS) {
                stringResource(R.string.decrease_reps, requireNotNull(reps))
            } else {
                stringResource(R.string.decrease_weight, displayedValue)
            },
            enabled = decrementEnabled,
            onClick = decrement,
            tag = "editor_decrease",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
        )
    }
}

@Composable
private fun EditorStepButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val enabledDescription = stringResource(R.string.control_enabled)
    val disabledDescription = stringResource(R.string.control_disabled)
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
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
        Text(text = glyph, textAlign = TextAlign.Center)
    }
}

/**
 * Trailing zeros are dropped, so a whole weight reads «80 kg» rather than «80.00 kg» — the
 * rendering the round-4 review specified. Significant decimals survive: a 2.5 kg step gives
 * «82.5 kg», and a phone-sent 72.53 kg keeps both places.
 */
private fun formatWeight(hundredths: Int): String = NumberFormat.getNumberInstance().run {
    minimumFractionDigits = 0
    maximumFractionDigits = 2
    isGroupingUsed = false
    format(hundredths / HUNDREDTHS_PER_KG)
}

private const val HUNDREDTHS_PER_KG = 100.0
private const val STATUS_MAX_LINES = 4
private const val DOT_RING_WIDTH = 1.5
private const val PILL_HEIGHT = 6
private const val LONE_CARD_WIDTH = 0.55f

/** The value card's field icon, sized to sit under the value without competing with it. */
private const val CARD_ICON = 16

/**
 * The weight card's share of the two-card row, against the reps card's 1f. 92:60 on a 192dp
 * screen — 76dp and 44dp of content, against the 65dp and 36dp the widest values need at the
 * largest font scale. Equal halves gave both 60dp, which the weight overran.
 */
private const val WEIGHT_CARD_SHARE = 1.533f
private const val ROTARY_STEP_PX = 48f

/** Viewport inset above a Small (56dp) anchored edge button, its outer padding included. */
private const val SMALL_EDGE_CLEARANCE = 62

/**
 * Viewport inset above the Medium (70dp) complete-set button — one size for both states, so
 * the enabled primary action is never smaller than the disabled one and the geometry is stable.
 */
private const val MEDIUM_EDGE_CLEARANCE = 76

/** Sides of the content column; the §4 stack sits in the wide middle band of the circle. */
private const val CONTENT_SIDE_INSET = 16

/** Clears the top-arc time text. */
private const val CONTENT_TOP_INSET = 24
