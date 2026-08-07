// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.model

/**
 * The start card's readout mode (home-start-card.md HS2). Domain-side twin of the shared
 * `StartCardModeUi` catalog — the UI enum lives in `core/ui/start-mode` and cannot cross
 * into this layer (`DomainLayerNoUiRule`); the feature's `mvi/mapper` bridges the two.
 */
enum class StartCardModeDomain {
    WEEK,
    DAYS_SINCE_LAST,
    LAGGING_GROUPS,
    FORGOTTEN_TRAINING,
}
