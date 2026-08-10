// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.start_mode.model

/**
 * The four readouts `HomeStartCard` can show (home-start-card.md HS2). This module owns the
 * catalog because two features consume it — the card's head names the current mode and the
 * Settings entry (HS5) reaches the same picker sheet — and feature modules cannot depend on
 * each other.
 *
 * Persistence stores a per-feature domain enum's name, not this type: the UI enum stays out
 * of the domain layer (`DomainLayerNoUiRule`), so each consuming feature maps its own
 * `StartCardModeDomain` onto this catalog in `mvi/mapper/`.
 */
enum class StartCardModeUi {
    WEEK,
    DAYS_SINCE_LAST,
    LAGGING_GROUPS,
    FORGOTTEN_TRAINING,
}
