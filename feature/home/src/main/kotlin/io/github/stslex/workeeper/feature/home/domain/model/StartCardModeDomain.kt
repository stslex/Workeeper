// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.model

/**
 * The start card's readout mode (home-start-card.md HS2). Domain-side twin of the shared
 * `StartCardModeUi` catalog — the UI enum lives in `core/ui/start-mode` and cannot cross
 * into this layer (`DomainLayerNoUiRule`); the feature's `mvi/mapper` bridges the two.
 *
 * [value] is the DataStore encoding (HS6). The strings are a persistence contract, pinned
 * by test: renaming an entry without keeping its [value] would silently reset every
 * affected user to the default.
 */
enum class StartCardModeDomain(val value: String) {
    WEEK("WEEK"),
    DAYS_SINCE_LAST("DAYS_SINCE_LAST"),
    LAGGING_GROUPS("LAGGING_GROUPS"),
    FORGOTTEN_TRAINING("FORGOTTEN_TRAINING"),
    ;

    companion object {

        /** HS3: «Неделя» is the default — including for an unknown stored value. */
        fun fromValue(raw: String): StartCardModeDomain =
            entries.firstOrNull { it.value == raw } ?: WEEK
    }
}
