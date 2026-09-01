// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.wear.R
import java.text.NumberFormat

@Composable
internal fun WearControllerScreen(
    state: WearSurfaceState,
    onAction: (ControllerAction) -> Unit,
) {
    val model = state
    WearAppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .testTag("controller_scroll"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Status(model)
            when (model.kind) {
                WearSurfaceKind.ACTIVE,
                WearSurfaceKind.REFRESH_REQUIRED,
                WearSurfaceKind.DISCONNECTED,
                -> ActiveContent(model, onAction)
                WearSurfaceKind.PHONE_ACTION_NO_SETS,
                WearSurfaceKind.PHONE_ACTION_UNSUPPORTED,
                -> PhoneActionContent(model)
                WearSurfaceKind.PAYLOAD_TOO_LARGE -> GenericWorkoutInstruction()
                WearSurfaceKind.WORKOUT_COMPLETE -> WorkoutCompleteContent(model)
                WearSurfaceKind.RETRYABLE_ERROR -> RetryContent(model, onAction)
                WearSurfaceKind.LOADING,
                WearSurfaceKind.NO_SESSION,
                WearSurfaceKind.PROTOCOL_MISMATCH,
                -> Unit
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Status(model: WearSurfaceModel) {
    Text(
        text = stringResource(model.statusCopy().resource),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() }
            .testTag("status"),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun ActiveContent(model: WearSurfaceModel, onAction: (ControllerAction) -> Unit) {
    Text(
        text = model.trainingName ?: stringResource(R.string.workout_generic),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("training_name"),
    )
    Progress(model)
    Text(
        text = model.exerciseName ?: stringResource(R.string.exercise_generic),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("exercise_name"),
    )
    Text(
        text = stringResource(
            R.string.set_progress,
            requireNotNull(model.setOrdinal),
            requireNotNull(model.totalSets),
        ),
        modifier = Modifier.testTag("set_progress"),
    )
    if (model.weighted) WeightStepper(model, onAction)
    RepsStepper(model, onAction)
    model.fieldError?.let { field ->
        val message = stringResource(
            if (field == NumericField.REPS) R.string.reps_invalid else R.string.weight_invalid,
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .semantics { error(message) }
                .testTag("field_error"),
        )
    }
    val completeEnabledDescription = stringResource(R.string.complete_set_enabled_description)
    val completeDisabledDescription = stringResource(R.string.complete_set_disabled_description)
    Button(
        onClick = { onAction(ControllerAction.CompleteSet) },
        enabled = model.completeEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = if (model.completeEnabled) {
                    completeEnabledDescription
                } else {
                    completeDisabledDescription
                }
            }
            .testTag("complete_set"),
    ) {
        Text(stringResource(R.string.complete_set), textAlign = TextAlign.Center)
    }
}

@Composable
private fun Progress(model: WearSurfaceModel) {
    Text(
        text = pluralStringResource(
            R.plurals.exercise_progress,
            requireNotNull(model.totalExercises),
            requireNotNull(model.completedExercises),
            model.totalExercises,
        ),
        modifier = Modifier.testTag("exercise_progress"),
    )
}

@Composable
private fun RepsStepper(model: WearSurfaceModel, onAction: (ControllerAction) -> Unit) {
    val reps = requireNotNull(model.reps)
    val down = WearDraftPolicy.decrementReps(reps)
    val up = WearDraftPolicy.incrementReps(reps)
    Stepper(
        label = stringResource(R.string.reps_label),
        value = reps.toString(),
        decrementLabel = stringResource(R.string.decrease_reps, reps),
        incrementLabel = stringResource(R.string.increase_reps, reps),
        decrementEnabled = model.controlsEnabled && down != null,
        incrementEnabled = model.controlsEnabled && up != null,
        onDecrement = { down?.let { onAction(ControllerAction.SetReps(it)) } },
        onIncrement = { up?.let { onAction(ControllerAction.SetReps(it)) } },
        tag = "reps",
    )
}

@Composable
private fun WeightStepper(model: WearSurfaceModel, onAction: (ControllerAction) -> Unit) {
    val weight = model.weightHundredthsKg
    val down = WearDraftPolicy.decrementWeight(weight)
    val up = WearDraftPolicy.incrementWeight(weight)
    val formatted = weight?.let(::formatWeight)
    val displayedWeight = formatted?.let { stringResource(R.string.weight_value, it) }
        ?: stringResource(R.string.weight_unset)
    Stepper(
        label = stringResource(R.string.weight_label),
        value = displayedWeight,
        decrementLabel = stringResource(R.string.decrease_weight, displayedWeight),
        incrementLabel = stringResource(R.string.increase_weight, displayedWeight),
        decrementEnabled = model.controlsEnabled && down != null,
        incrementEnabled = model.controlsEnabled && up != null,
        onDecrement = { down?.let { onAction(ControllerAction.SetWeight(it.value)) } },
        onIncrement = { if (up != null) onAction(ControllerAction.SetWeight(up)) },
        tag = "weight",
    )
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    decrementLabel: String,
    incrementLabel: String,
    decrementEnabled: Boolean,
    incrementEnabled: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    tag: String,
) {
    val enabledDescription = stringResource(R.string.control_enabled)
    val disabledDescription = stringResource(R.string.control_disabled)
    Text(label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onDecrement,
            enabled = decrementEnabled,
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = decrementLabel
                    stateDescription = if (decrementEnabled) enabledDescription else disabledDescription
                }
                .testTag("${tag}_decrease"),
        ) { Text("−") }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.testTag("${tag}_value"),
        )
        Button(
            onClick = onIncrement,
            enabled = incrementEnabled,
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = incrementLabel
                    stateDescription = if (incrementEnabled) enabledDescription else disabledDescription
                }
                .testTag("${tag}_increase"),
        ) { Text("+") }
    }
}

@Composable
private fun PhoneActionContent(model: WearSurfaceModel) {
    Text(
        text = model.exerciseName ?: stringResource(R.string.exercise_generic),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("exercise_name"),
    )
}

@Composable
private fun GenericWorkoutInstruction() {
    Text(
        text = stringResource(R.string.workout_generic),
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
        modifier = Modifier.testTag("training_name"),
    )
    Progress(model)
    Text(stringResource(R.string.finish_on_phone), modifier = Modifier.testTag("finish_on_phone"))
}

@Composable
private fun RetryContent(model: WearSurfaceModel, onAction: (ControllerAction) -> Unit) {
    Button(
        onClick = { onAction(ControllerAction.Retry) },
        enabled = model.retryEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("retry"),
    ) {
        Text(stringResource(R.string.retry))
    }
}

private fun formatWeight(hundredths: Int): String = NumberFormat.getNumberInstance().run {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
    isGroupingUsed = false
    format(hundredths / HUNDREDTHS_PER_KG)
}

private const val HUNDREDTHS_PER_KG = 100.0
