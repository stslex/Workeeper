// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain.model

/**
 * The start card's readout mode (home-start-card.md HS2). GUARD: [value] is the DataStore
 * encoding — changing one silently resets every affected user to the default.
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
