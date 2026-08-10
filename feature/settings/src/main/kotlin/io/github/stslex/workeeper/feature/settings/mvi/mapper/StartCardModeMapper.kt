// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.mapper

import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.feature.settings.domain.model.StartCardModeDomain

/**
 * Bridges the shared UI mode catalog (`core/ui/start-mode`) and this feature's domain twin —
 * the same seam `feature/home` keeps, on its own side of the feature boundary.
 */
internal object StartCardModeMapper {

    fun StartCardModeUi.toDomain(): StartCardModeDomain = when (this) {
        StartCardModeUi.WEEK -> StartCardModeDomain.WEEK
        StartCardModeUi.DAYS_SINCE_LAST -> StartCardModeDomain.DAYS_SINCE_LAST
        StartCardModeUi.LAGGING_GROUPS -> StartCardModeDomain.LAGGING_GROUPS
        StartCardModeUi.FORGOTTEN_TRAINING -> StartCardModeDomain.FORGOTTEN_TRAINING
    }

    fun StartCardModeDomain.toUi(): StartCardModeUi = when (this) {
        StartCardModeDomain.WEEK -> StartCardModeUi.WEEK
        StartCardModeDomain.DAYS_SINCE_LAST -> StartCardModeUi.DAYS_SINCE_LAST
        StartCardModeDomain.LAGGING_GROUPS -> StartCardModeUi.LAGGING_GROUPS
        StartCardModeDomain.FORGOTTEN_TRAINING -> StartCardModeUi.FORGOTTEN_TRAINING
    }
}
