// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain

import io.github.stslex.workeeper.feature.settings.domain.model.ArchivedCountsDomain
import io.github.stslex.workeeper.feature.settings.domain.model.StartCardModeDomain
import io.github.stslex.workeeper.feature.settings.domain.model.ThemeModeDomain
import kotlinx.coroutines.flow.Flow

interface SettingsInteractor {

    fun appVersionName(): String

    fun appVersionCode(): Long

    fun observeThemeMode(): Flow<ThemeModeDomain>

    suspend fun setThemeMode(mode: ThemeModeDomain)

    /** The Home start card's readout mode (HS5/HS6) — same preference Home observes. */
    fun observeStartCardMode(): Flow<StartCardModeDomain>

    suspend fun setStartCardMode(mode: StartCardModeDomain)

    /**
     * How many exercises and trainings are in the archive.
     *
     * §26 draws the Archive row with a sub-line («4 упражнения · 1 тренировка»), and **B15 said the
     * data source did not exist**. It does, and has since the archive screen was built: both are
     * `Flow<Int>` from `observeArchivedCount()` on their repositories, and `ArchivePagingHandler`
     * already consumes them to label its own segmented control. Nothing was missing but this wiring.
     */
    fun observeArchivedCounts(): Flow<ArchivedCountsDomain>
}
