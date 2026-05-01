// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppBottomSheet
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Bottom sheet for adding an exercise to an active Live workout session. Stateless: each
 * input emits an [ExercisePickerAction] back to the parent, which holds the query +
 * results in its `Store.State` and re-renders.
 *
 * Search-or-create UX (Q3 lock):
 *  - Empty query → show all library entries from [results].
 *  - Non-empty query without matches → [noMatchHeadline] is non-null, [createCtaLabel] is
 *    non-null, both rendered as separate elements (explicit no-match indicator before
 *    Create per the spec).
 *  - Non-empty query with partial matches → results list rendered, [createCtaLabel] is
 *    non-null and shown below the list so the user can still create the distinct typed
 *    name. Only an exact case-insensitive name match suppresses [createCtaLabel].
 *
 * The headline + Create label come pre-formatted from the parent so this composable does
 * not derive display text — kit composables stay locale-agnostic and stateless.
 *
 * @param query current search input
 * @param results filtered library list (already excludes the active session's exercises)
 * @param noMatchHeadline pre-formatted "No exercises match '{query}'" string, null when
 * results is non-empty (partial matches do not warrant the no-match indicator)
 * @param createCtaLabel pre-formatted "Create '{query}'" string, null when an exact
 * case-insensitive match exists (DB-level dedupe would kick in)
 * @param isPrimaryActionEnabled drives the Create CTA enabled state — false while a fetch
 * is in flight (throttle for rapid double-taps)
 */
@Composable
fun ExercisePickerBottomSheet(
    query: String,
    results: ImmutableList<ExercisePickerUiModel>,
    noMatchHeadline: String?,
    createCtaLabel: String?,
    searchHint: String,
    isPrimaryActionEnabled: Boolean,
    onAction: (ExercisePickerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(
        modifier = modifier.testTag("ExercisePickerBottomSheet"),
        onDismiss = { onAction(ExercisePickerAction.OnDismiss) },
    ) {
        AppTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ExercisePickerQueryField"),
            value = query,
            onValueChange = { onAction(ExercisePickerAction.OnQueryChange(it)) },
            placeholder = searchHint,
            leadingIcon = Icons.Default.Search,
        )
        Spacer(Modifier.height(AppDimension.Space.md))
        if (noMatchHeadline != null) {
            NoMatchHeadline(headline = noMatchHeadline)
            Spacer(Modifier.height(AppDimension.Space.md))
        }
        if (results.isNotEmpty()) {
            ResultsList(
                results = results,
                onSelect = { onAction(ExercisePickerAction.OnExerciseSelect(it)) },
            )
        }
        if (createCtaLabel != null) {
            Spacer(Modifier.height(AppDimension.Space.md))
            CreateCtaButton(
                label = createCtaLabel,
                enabled = isPrimaryActionEnabled,
                onClick = { onAction(ExercisePickerAction.OnCreateNewExercise(query)) },
            )
        }
    }
}

@Composable
private fun ResultsList(
    results: ImmutableList<ExercisePickerUiModel>,
    onSelect: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = LIST_MAX_HEIGHT_DP),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        items(items = results, key = { it.uuid }) { entry ->
            ExerciseRow(entry = entry, onClick = { onSelect(entry.uuid) })
        }
    }
}

@Composable
private fun ExerciseRow(
    entry: ExercisePickerUiModel,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppUi.shapes.medium)
            .background(AppUi.colors.surfaceTier0)
            .clickable(onClick = onClick)
            .padding(
                horizontal = AppDimension.Space.md,
                vertical = AppDimension.Space.md,
            )
            .testTag("ExercisePickerRow_${entry.uuid}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = entry.name,
            style = AppUi.typography.bodyLarge,
            color = AppUi.colors.textPrimary,
        )
    }
}

@Composable
private fun NoMatchHeadline(headline: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimension.Space.md),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            modifier = Modifier.testTag("ExercisePickerNoMatchHeadline"),
            text = headline,
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CreateCtaButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        AppButton.Tertiary(
            modifier = Modifier.testTag("ExercisePickerCreateCta"),
            text = label,
            onClick = onClick,
            enabled = enabled,
            size = AppButtonSize.MEDIUM,
            leadingIcon = Icons.Default.Add,
        )
    }
}

private val LIST_MAX_HEIGHT_DP = 360.dp

@Preview
@Composable
private fun ExercisePickerEmptyQueryLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ExercisePickerBottomSheet(
            query = "",
            results = persistentListOf(
                ExercisePickerUiModel("u1", "Bench Press", ExerciseTypeUiModel.WEIGHTED),
                ExercisePickerUiModel("u2", "Pull Ups", ExerciseTypeUiModel.WEIGHTLESS),
                ExercisePickerUiModel("u3", "Squat", ExerciseTypeUiModel.WEIGHTED),
            ),
            noMatchHeadline = null,
            createCtaLabel = null,
            searchHint = "Search or create",
            isPrimaryActionEnabled = true,
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun ExercisePickerNoMatchDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ExercisePickerBottomSheet(
            query = "skull crushers",
            results = persistentListOf(),
            noMatchHeadline = "No exercises match “skull crushers”",
            createCtaLabel = "Create “skull crushers”",
            searchHint = "Search or create",
            isPrimaryActionEnabled = true,
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun ExercisePickerPartialMatchPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ExercisePickerBottomSheet(
            query = "bench",
            results = persistentListOf(
                ExercisePickerUiModel("u1", "Bench Press", ExerciseTypeUiModel.WEIGHTED),
                ExercisePickerUiModel("u2", "Bench Press (Incline)", ExerciseTypeUiModel.WEIGHTED),
            ),
            noMatchHeadline = null,
            createCtaLabel = "Create “bench”",
            searchHint = "Search or create",
            isPrimaryActionEnabled = true,
            onAction = {},
        )
    }
}
