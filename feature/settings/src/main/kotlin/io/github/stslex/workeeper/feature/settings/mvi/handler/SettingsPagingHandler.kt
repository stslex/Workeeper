// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStore
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.mvi.mapper.ThemeModeMapper.toUi
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ViewModelScoped
internal class SettingsPagingHandler @Inject constructor(
    private val interactor: SettingsInteractor,
    store: SettingsHandlerStore,
) : Handler<Action.Paging>, SettingsHandlerStore by store {

    override fun invoke(action: Action.Paging) {
        when (action) {
            Action.Paging.Init -> observeTheme()
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
