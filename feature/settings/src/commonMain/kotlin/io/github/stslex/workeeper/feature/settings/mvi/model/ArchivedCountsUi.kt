// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.model

import androidx.compose.runtime.Stable

/** The Archive row's drawn sub-line, as two numbers. The string is built in the UI layer. */
@Stable
data class ArchivedCountsUi(
    val exercises: Int,
    val trainings: Int,
)
