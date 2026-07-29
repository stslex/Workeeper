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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

/**
 * The mockup's `sh-pick` (extraction §4.9): the window — scrim, grab, dismissal — wrapped at
 * this level, the content split out so it stays goldenable (the window is outside the gate,
 * §10.4).
 */
@Composable
internal fun ExercisePickerSheet(
    items: ImmutableList<ExercisePickerItemUiModel>,
    selectedUuid: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onItemSelect: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    // expandedOnly: the user opened this to search, not to peek.
    // onSettled is THE SEQUENCING POINT — focus is taken only once the sheet has ARRIVED at
    // expanded. Requesting it at composition instead would race the enter animation: the IME
    // rises into a sheet that is still translating, which jitters the layout it is supposed
    // to sit above. Waiting costs the animation's duration and buys a keyboard that appears
    // once, in place.
    AppBottomSheet(
        onDismiss = onDismiss,
        expandedOnly = true,
        onSettled = { focusRequester.requestFocus() },
    ) {
        ExercisePickerSheetContent(
            items = items,
            selectedUuid = selectedUuid,
            query = query,
            onQueryChange = onQueryChange,
            onItemSelect = onItemSelect,
            searchFocusRequester = focusRequester,
        )
    }
}

/**
 * `sh-pick`'s drawing: the h3 title, then `.mitem` rows — `--body` at the body rung, the
 * selected one `.mitem.on`: `--max` at 500 with the trailing `.chev`-weight check
 * (`M4 12.5l5 5L20 7`, `AppIcons.Check`). The old build put the exercise-type label under
 * the selected row instead; the mockup's selection mark is the check, and the check ships.
 */
@Composable
internal fun ExercisePickerSheetContent(
    items: ImmutableList<ExercisePickerItemUiModel>,
    selectedUuid: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    onItemSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    searchFocusRequester: FocusRequester? = null,
) {
    // The filter is derived, not stored: `items` is the whole picker set already in memory
    // (the recents query carries no LIMIT), so a client-side match is the complete answer
    // here — nothing can be typed that exists in the picker but not in this list. A
    // library-wide search would be a different, larger set, and selecting from it would
    // dead-tap: the handler resolves the chosen uuid against exactly this list.
    val visible = remember(items, query) {
        if (query.isBlank()) {
            items
        } else {
            items.filter { item -> item.name.contains(query, ignoreCase = true) }
                .toImmutableList()
        }
    }
    // THE HEIGHT BUDGET. The IME inset is delivered and it animates — measured on API 35,
    // portrait: `ime` climbs 0 → 883px over the keyboard's rise, and this budget follows it
    // 863dp → 473dp, frame by frame. What the inset CANNOT do is make oversized content fit:
    // Material sets this window to SOFT_INPUT_ADJUST_NOTHING on API 30+, so the window is
    // never resized, and content taller than the space left above the keyboard is not
    // scrolled or panned — it is simply covered. Nothing bounded this content to that space,
    // which is the bug. So it bounds itself, and the list below takes the remainder.
    //
    // Read in composition rather than taken from a padding modifier: this is the raw window
    // inset, and reading it here is what makes the reflow track the keyboard's animation
    // instead of jumping at the end of it.
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
                .then(
                    searchFocusRequester
                        ?.let { requester -> Modifier.focusRequester(requester) }
                        ?: Modifier,
                )
                .testTag("ExerciseChartPickerSearch"),
            value = query,
            onValueChange = onQueryChange,
            // No leading magnifier: the mockup draws no search affordance on this sheet at
            // all (§4.9), so inventing a glyph for it would be inventing design. The
            // placeholder carries the meaning.
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
            // `weight(fill = false)` is what makes the list the elastic element: it takes the
            // space the title and the field leave inside the budget above, and no more, so a
            // rising keyboard shrinks the list rather than pushing it under itself. The 360dp
            // cap still applies when there is room to spare — the sheet is a picker, not a
            // full-screen list.
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

/**
 * A floor for the budget, so the cap can never go to zero or negative and collapse the layout.
 *
 * It is also the point where this sheet stops being solvable. Measured on API 35, landscape,
 * with the keyboard up: window 1080px, IME 662px, status bar 137px — 281px, i.e. **94dp for
 * everything**, against 60dp of chrome before this layout gets a pixel. A 56dp text field with
 * a title above it does not fit in what is left, at any cap, and no arithmetic here changes
 * that: the sheet would have to stop being a sheet. Reported rather than worked around.
 */
private val MIN_SHEET_CONTENT_HEIGHT = 160.dp

/**
 * What the window spends before this layout gets a pixel, and therefore what the budget above
 * has to hand back: the grab handle block (8 + 4 + 16) and `AppBottomSheet`'s own bottom
 * padding (`xxl`, 32) — 60dp, all of it OUTSIDE the height cap applied here. `AppSheetLayout`'s
 * own 8/24 padding is inside the cap and is already accounted for by the cap itself.
 *
 * It is a constant rather than a measurement because the alternative — measuring the chrome and
 * feeding it back into the constraint that produced it — is a layout feedback loop. The error it
 * can carry is absorbed by the list's `weight`, which takes whatever is actually left rather
 * than what this arithmetic predicted.
 */
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
