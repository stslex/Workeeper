// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.di.SettingsScope
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.mvi.mapper.ThemeModeMapper.toUi
import io.github.stslex.workeeper.feature.settings.mvi.model.ArchivedCountsUi
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import kotlinx.coroutines.flow.map

@SingleIn(SettingsScope::class)
internal class SettingsPagingHandler @Inject constructor(
    private val interactor: SettingsInteractor,
    store: SettingsHandlerStore,
) : Handler<Action.Paging>, SettingsHandlerStore by store {

    override fun invoke(action: Action.Paging) {
        when (action) {
            Action.Paging.Init -> {
                observeTheme()
                observeArchivedCounts()
            }
        }
    }

    /** The Archive row's sub-line. Pass-through of two counts that already existed (B15). */
    private fun observeArchivedCounts() {
        interactor.observeArchivedCounts()
            .map { ArchivedCountsUi(exercises = it.exercises, trainings = it.trainings) }
            .launch { counts ->
                updateStateImmediate { it.copy(archivedCounts = counts) }
            }
    }

    private fun observeTheme() {
        interactor.observeThemeMode()
            .map { it.toUi() }
            .launch { mode ->
                updateStateImmediate { it.copy(themeMode = mode) }
            }
    }
}
