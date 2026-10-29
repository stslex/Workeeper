// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode

/**
 * `.shead`: training name, meta line and elapsed timer, sitting directly on `surfaceTier0`.
 * GUARD: this is not a card — no background, border, radius or elevation. See extraction §1.3.
 */
@Composable
internal fun LiveWorkoutHeader(
    trainingNameLabel: String,
    namePlaceholder: String,
    elapsedLabel: String,
    metaLabel: String,
    isEditingName: Boolean,
    nameDraft: String,
    onNameTap: () -> Unit,
    onNameChange: (String) -> Unit,
    onNameSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    requestFocusWhenEditing: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (isEditingName) {
                EditableTrainingNameField(
                    value = nameDraft,
                    placeholder = namePlaceholder,
                    onValueChange = onNameChange,
                    onSubmit = onNameSubmit,
                    requestFocusOnAppear = requestFocusWhenEditing,
                )
            } else {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNameTap)
                        .testTag("LiveWorkoutTrainingNameLabel"),
                    text = trainingNameLabel,
                    style = AppUi.typography.text.title,
                    color = AppUi.colors.textPrimary,
                )
            }
            if (metaLabel.isNotBlank()) {
                Spacer(modifier = Modifier.height(AppDimension.Space.sm))
                Text(
                    modifier = Modifier.testTag("LiveWorkoutHeaderMeta"),
                    text = metaLabel,
                    style = AppUi.typography.mono.meta,
                    color = AppUi.colors.textTertiary,
                )
            }
        }
        Text(
            modifier = Modifier.testTag("LiveWorkoutTimer"),
            text = elapsedLabel,
            style = AppUi.typography.timer,
            color = AppUi.colors.textPrimary,
        )
    }
}

/**
 * Inline-edit field in header title typography; focus loss submits the value (spec A1).
 * `requestFocusOnAppear` exists only so the golden can skip the layoutlib-hostile IME path.
 */
@Composable
private fun EditableTrainingNameField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    requestFocusOnAppear: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    var wasFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (requestFocusOnAppear) focusRequester.requestFocus()
    }
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
            textStyle = AppUi.typography.text.title.copy(
                color = AppUi.colors.textPrimary,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { onSubmit(value) },
            ),
            cursorBrush = SolidColor(AppUi.colors.accent),
        )
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = AppUi.typography.text.title,
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
            trainingNameLabel = "верх (с подтягиваниями)",
            namePlaceholder = "Без названия",
            elapsedLabel = "12:04",
            metaLabel = "1 из 5 упражнений · 4 из 18 подходов",
            isEditingName = false,
            nameDraft = "верх (с подтягиваниями)",
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
            trainingNameLabel = "верх (с подтягиваниями)",
            namePlaceholder = "Без названия",
            elapsedLabel = "47:08",
            metaLabel = "2 из 5 упражнений · 9 из 18 подходов · пропущено 1",
            isEditingName = false,
            nameDraft = "верх (с подтягиваниями)",
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
            trainingNameLabel = "Без названия",
            namePlaceholder = "Без названия",
            elapsedLabel = "00:12",
            metaLabel = "",
            isEditingName = true,
            nameDraft = "Push d",
            onNameTap = {},
            onNameChange = {},
            onNameSubmit = {},
        )
    }
}
