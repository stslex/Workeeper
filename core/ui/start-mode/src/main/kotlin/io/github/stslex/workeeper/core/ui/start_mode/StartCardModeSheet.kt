// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.start_mode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppBottomSheet
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi

/**
 * The start card's mode picker (home-start-card.md HS4/HS5): four rows, each the mode's
 * name over one line saying what is counted, the current one checked. The row is its own
 * explanation — there is deliberately no info button and no second sheet behind one.
 *
 * One sheet, two entry points: the card's head on Home and the Settings entry both open
 * exactly this window, which is why it lives in this shared module rather than in either
 * feature.
 */
@Composable
fun StartCardModeSheet(
    selected: StartCardModeUi,
    onSelect: (StartCardModeUi) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(
        onDismiss = onDismiss,
        modifier = modifier.testTag("StartCardModeSheet"),
    ) {
        StartCardModeSheetContent(selected = selected, onSelect = onSelect)
    }
}

/**
 * The sheet's body, separate from the window so Paparazzi can photograph it —
 * `ModalBottomSheet` renders in its own window, outside the golden harness's model.
 */
@Composable
fun StartCardModeSheetContent(
    selected: StartCardModeUi,
    onSelect: (StartCardModeUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.core_ui_start_mode_sheet_title),
            style = AppUi.typography.titleLarge,
            color = AppUi.colors.textPrimary,
        )
        Spacer(Modifier.height(AppDimension.Space.sm))
        StartCardModeUi.entries.forEach { mode ->
            ModeRow(
                mode = mode,
                isSelected = mode == selected,
                onClick = { onSelect(mode) },
            )
        }
    }
}

@Composable
private fun ModeRow(
    mode: StartCardModeUi,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AppDimension.Space.sm)
            .testTag("StartCardModeRow_${mode.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
        ) {
            Text(
                text = startCardModeName(mode),
                style = AppUi.typography.titleMedium,
                color = AppUi.colors.textPrimary,
            )
            Text(
                text = startCardModeDescription(mode),
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textSecondary,
            )
        }
        if (isSelected) {
            Icon(
                modifier = Modifier
                    .padding(start = AppDimension.Space.sm)
                    .size(AppDimension.Icon.small),
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = AppUi.colors.textPrimary,
            )
        }
    }
}

/** The one line under each name — what the mode counts, which is the row's explanation. */
@Composable
private fun startCardModeDescription(mode: StartCardModeUi): String = stringResource(
    when (mode) {
        StartCardModeUi.WEEK -> R.string.core_ui_start_mode_description_week
        StartCardModeUi.DAYS_SINCE_LAST -> R.string.core_ui_start_mode_description_days_since_last
        StartCardModeUi.LAGGING_GROUPS -> R.string.core_ui_start_mode_description_lagging_groups
        StartCardModeUi.FORGOTTEN_TRAINING ->
            R.string.core_ui_start_mode_description_forgotten_training
    },
)

@Preview(name = "Light")
@Composable
private fun StartCardModeSheetContentLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        StartCardModeSheetContent(selected = StartCardModeUi.WEEK, onSelect = {})
    }
}

@Preview(name = "Dark")
@Composable
private fun StartCardModeSheetContentDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        StartCardModeSheetContent(selected = StartCardModeUi.LAGGING_GROUPS, onSelect = {})
    }
}
