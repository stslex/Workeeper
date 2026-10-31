// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain.model

/**
 * The Home start card's readout mode as Settings sees it (home-start-card.md HS5: two entry
 * points, one sheet, one preference). Feature-local twin of `feature/home`'s enum of the
 * same name — features cannot depend on each other, and the domain layer cannot reach the
 * shared UI catalog (`DomainLayerNoUiRule`) — so the [value] strings are the contract that
 * keeps the two in step, pinned by test on both sides.
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
