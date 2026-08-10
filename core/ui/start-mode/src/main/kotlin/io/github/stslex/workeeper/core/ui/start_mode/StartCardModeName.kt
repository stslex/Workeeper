// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.start_mode

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi

/**
 * The mode's name — one string per mode, used both as the card head's label and as the
 * picker sheet's row name so the two surfaces cannot drift. The single exception is
 * [StartCardModeUi.FORGOTTEN_TRAINING], whose card head reads «Дольше всего не делали»
 * (home-start-card RU copy) — that label is the card's own string in `feature/home`, while
 * this name stays the mode's name everywhere a mode is *named* rather than *shown*.
 */
@Composable
fun startCardModeName(mode: StartCardModeUi): String = stringResource(
    when (mode) {
        StartCardModeUi.WEEK -> R.string.core_ui_start_mode_name_week
        StartCardModeUi.DAYS_SINCE_LAST -> R.string.core_ui_start_mode_name_days_since_last
        StartCardModeUi.LAGGING_GROUPS -> R.string.core_ui_start_mode_name_lagging_groups
        StartCardModeUi.FORGOTTEN_TRAINING -> R.string.core_ui_start_mode_name_forgotten_training
    },
)
