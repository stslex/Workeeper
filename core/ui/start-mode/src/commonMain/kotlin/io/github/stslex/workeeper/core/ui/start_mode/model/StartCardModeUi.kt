// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.start_mode.model

/**
 * The four readouts `HomeStartCard` can show (home-start-card.md HS2). Shared because two
 * features consume it; each maps its own `StartCardModeDomain` onto this catalog.
 */
enum class StartCardModeUi {
    WEEK,
    DAYS_SINCE_LAST,
    LAGGING_GROUPS,
    FORGOTTEN_TRAINING,
}
