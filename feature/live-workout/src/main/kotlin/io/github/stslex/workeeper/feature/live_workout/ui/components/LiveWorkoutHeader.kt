// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode

@Composable
internal fun LiveWorkoutHeader(
    trainingNameLabel: String,
    namePlaceholder: String,
    elapsedLabel: String,
    progressLabel: String,
    progress: Float,
    isEditingName: Boolean,
    nameDraft: String,
    onNameTap: () -> Unit,
    onNameChange: (String) -> Unit,
    onNameSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppUi.shapes.medium)
            .background(AppUi.colors.surfaceTier1)
            .padding(AppDimension.Space.lg),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (isEditingName) {
                    EditableTrainingNameField(
                        value = nameDraft,
                        placeholder = namePlaceholder,
                        onValueChange = onNameChange,
                        onSubmit = onNameSubmit,
                    )
                } else {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNameTap)
                            .testTag("LiveWorkoutTrainingNameLabel"),
                        text = trainingNameLabel,
                        style = AppUi.typography.titleMedium,
                        color = AppUi.colors.textPrimary,
                    )
                }
            }
            Text(
                text = "•$elapsedLabel",
                style = AppUi.typography.titleMedium,
                color = AppUi.colors.accent,
            )
        }
        if (progressLabel.isNotBlank()) {
            Text(
                text = progressLabel,
                style = AppUi.typography.bodySmall,
                color = AppUi.colors.textSecondary,
            )
        }
        Spacer(Modifier.height(AppDimension.Space.xs))
        Box(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppDimension.Space.xs),
                progress = { progress },
                color = AppUi.colors.accent,
                trackColor = AppUi.colors.surfaceTier3,
            )
        }
    }
}

/**
 * Inline-edit text field that mimics the header title typography. Focus is requested as
 * soon as the field appears (when the user taps the label); a focus-loss event submits the
 * current value, matching the "save on blur via tap-out, IME Done, or back-dismissed
 * keyboard" rule from spec A1.
 */
@Composable
private fun EditableTrainingNameField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var wasFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (wasFocused && !focusState.isFocused) onSubmit(value)
                    wasFocused = focusState.isFocused
                }
                .testTag("LiveWorkoutTrainingNameField"),
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = AppUi.typography.titleMedium.copy(
                color = AppUi.colors.textPrimary,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { onSubmit(value) },
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(AppUi.colors.accent),
        )
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = AppUi.typography.titleMedium,
                color = AppUi.colors.textTertiary,
            )
        }
    }
}

@Preview
@Composable
private fun LiveWorkoutHeaderLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        LiveWorkoutHeader(
            trainingNameLabel = "Push Day",
            namePlaceholder = "Untitled",
            elapsedLabel = "23:14",
            progressLabel = "2 of 5 done · 16 sets logged",
            progress = 0.4f,
            isEditingName = false,
            nameDraft = "Push Day",
            onNameTap = {},
            onNameChange = {},
            onNameSubmit = {},
        )
    }
}

@Preview
@Composable
private fun LiveWorkoutHeaderDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveWorkoutHeader(
            trainingNameLabel = "Push Day",
            namePlaceholder = "Untitled",
            elapsedLabel = "47:08",
            progressLabel = "4 of 5 done · 22 sets logged",
            progress = 0.8f,
            isEditingName = false,
            nameDraft = "Push Day",
            onNameTap = {},
            onNameChange = {},
            onNameSubmit = {},
        )
    }
}

@Preview
@Composable
private fun LiveWorkoutHeaderEmptyPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveWorkoutHeader(
            trainingNameLabel = "Untitled",
            namePlaceholder = "Untitled",
            elapsedLabel = "00:12",
            progressLabel = "",
            progress = 0f,
            isEditingName = false,
            nameDraft = "",
            onNameTap = {},
            onNameChange = {},
            onNameSubmit = {},
        )
    }
}

@Preview
@Composable
private fun LiveWorkoutHeaderEditingPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveWorkoutHeader(
            trainingNameLabel = "Untitled",
            namePlaceholder = "Untitled",
            elapsedLabel = "00:12",
            progressLabel = "",
            progress = 0f,
            isEditingName = true,
            nameDraft = "Push d",
            onNameTap = {},
            onNameChange = {},
            onNameSubmit = {},
        )
    }
}
