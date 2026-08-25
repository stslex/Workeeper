// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppBottomSheet
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetLayout
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.R
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** The mockup's `sh-pick` (§4.9): the sheet window; content is split out to stay goldenable. */
@Composable
internal fun ExercisePickerSheet(
    items: ImmutableList<ExercisePickerItemUiModel>,
    selectedUuid: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onItemSelect: (String) -> Unit,
) {
    // expandedOnly: the search field is on screen the moment the sheet is.
    // NO AUTO-FOCUS, deliberately — every trigger cheap enough races the enter animation.
    AppBottomSheet(
        onDismiss = onDismiss,
        expandedOnly = true,
    ) {
        ExercisePickerSheetContent(
            items = items,
            selectedUuid = selectedUuid,
            query = query,
            onQueryChange = onQueryChange,
            onItemSelect = onItemSelect,
        )
    }
}

/** `sh-pick`'s drawing: h3 title then `.mitem` rows, the selected one with a check (§4.9). */
@Composable
internal fun ExercisePickerSheetContent(
    items: ImmutableList<ExercisePickerItemUiModel>,
    selectedUuid: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onItemSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Filter derived, not stored: `items` is the whole picker set and the handler resolves in it.
    val visible = remember(items, query) {
        if (query.isBlank()) {
            items
        } else {
            items.filter { item -> item.name.contains(query, ignoreCase = true) }
                .toImmutableList()
        }
    }
    // Height budget: the sheet window is never resized for the IME, so the content bounds
    // itself to the space above the keyboard; reading the inset in composition tracks its rise.
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val available = with(density) {
        val ime = WindowInsets.ime.getBottom(density)
        val top = WindowInsets.systemBars.getTop(density)
        (windowHeight - ime - top).toDp() - SHEET_CHROME
    }.coerceAtLeast(MIN_SHEET_CONTENT_HEIGHT)

    AppSheetLayout(
        modifier = modifier.heightIn(max = available),
        title = stringResource(R.string.feature_exercise_chart_picker_title),
    ) {
        AppTextField(
            modifier = Modifier
                .padding(horizontal = AppDimension.Space.md)
                .testTag("ExerciseChartPickerSearch"),
            value = query,
            onValueChange = onQueryChange,
            // No leading magnifier: the mockup draws no search affordance on this sheet (§4.9).
            placeholder = stringResource(R.string.feature_exercise_chart_picker_search),
        )
        if (visible.isEmpty()) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = AppDimension.Space.md,
                        vertical = AppDimension.Space.lg,
                    )
                    .testTag("ExerciseChartPickerNoMatches"),
                text = stringResource(R.string.feature_exercise_chart_picker_no_matches),
                style = AppUi.typography.text.body,
                color = AppUi.colors.textTertiary,
            )
        }
        LazyColumn(
            // `weight(fill = false)` makes the list elastic, so a rising keyboard shrinks it.
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(horizontal = AppDimension.Space.md)
                .heightIn(max = PICKER_LIST_MAX_HEIGHT)
                .testTag("ExerciseChartPickerList"),
        ) {
            items(visible, key = { it.uuid }) { item ->
                PickerRow(
                    item = item,
                    selected = item.uuid == selectedUuid,
                    onClick = { onItemSelect(item.uuid) },
                )
            }
        }
    }
}

/** `.mitem` — padding `15px 4px` → 16/4, body rung, radius 12px → 8 (no 12 rung). */
@Composable
private fun PickerRow(
    item: ExercisePickerItemUiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimension.Radius.small))
            .clickable(onClick = onClick)
            .padding(
                horizontal = AppDimension.Space.xs,
                vertical = AppDimension.Space.lg,
            )
            .testTag("ExerciseChartPickerItem_${item.uuid}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(end = AppDimension.Space.md),
            text = item.name,
            style = if (selected) {
                AppUi.typography.text.body.copy(fontWeight = FontWeight.Medium)
            } else {
                AppUi.typography.text.body
            },
            color = if (selected) AppUi.colors.textPrimary else AppUi.colors.textSecondary,
            maxLines = 1,
        )
        if (selected) {
            Icon(
                modifier = Modifier
                    .size(AppDimension.iconSm)
                    .testTag("ExerciseChartPickerCheck"),
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = AppUi.colors.textPrimary,
            )
        }
    }
}

/** The old build's cap, kept: the sheet lists recents, not the whole library. */
private val PICKER_LIST_MAX_HEIGHT = 360.dp

/** Floor for the height budget; landscape with the keyboard up does not fit at any cap. */
private val MIN_SHEET_CONTENT_HEIGHT = 160.dp

/** Chrome the sheet window spends outside this height cap; a constant, not a measurement. */
private val SHEET_CHROME = 60.dp

@Preview
@Composable
private fun ExercisePickerSheetContentDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Row(modifier = Modifier.background(AppUi.colors.surfaceTier3)) {
            ExercisePickerSheetContent(
                items = persistentListOf(
                    ExercisePickerItemUiModel("a", "разведение ног", ExerciseTypeUiModel.WEIGHTED),
                    ExercisePickerItemUiModel("b", "жим платформы (узко)", ExerciseTypeUiModel.WEIGHTED),
                    ExercisePickerItemUiModel("c", "румынская тяга", ExerciseTypeUiModel.WEIGHTED),
                    ExercisePickerItemUiModel("d", "подтягивания", ExerciseTypeUiModel.WEIGHTLESS),
                ),
                selectedUuid = "a",
                query = "",
                onQueryChange = {},
                onItemSelect = {},
            )
        }
    }
}

@Preview
@Composable
private fun ExercisePickerSheetContentLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Row(modifier = Modifier.background(AppUi.colors.surfaceTier3)) {
            ExercisePickerSheetContent(
                items = persistentListOf(
                    ExercisePickerItemUiModel("a", "разведение ног", ExerciseTypeUiModel.WEIGHTED),
                    ExercisePickerItemUiModel("b", "подтягивания", ExerciseTypeUiModel.WEIGHTLESS),
                ),
                selectedUuid = "a",
                query = "",
                onQueryChange = {},
                onItemSelect = {},
            )
        }
    }
}
