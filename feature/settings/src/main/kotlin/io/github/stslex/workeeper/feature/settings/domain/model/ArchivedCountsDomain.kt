// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain.model

/** What the Archive row's drawn sub-line counts. */
data class ArchivedCountsDomain(
    val exercises: Int,
    val trainings: Int,
)
