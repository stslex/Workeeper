// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.navigation.NavigatorEventBus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// A plain ViewModel, constructed in App.kt via viewModel {}. BOTH deps arrive from
// `AppRootDeps` — the contract this module declares and `:app:app`'s AppGraph implements — never
// from the graph itself and never via `context.appDeps<T>()`: `AppGraph` is internal to `:app:app`,
// which depends on this module, so it is not nameable here at all.
internal class AppRootViewModel(
    commonDataStore: CommonDataStore,
    val navigatorEventBus: NavigatorEventBus,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = commonDataStore.themePreference
        .map { value -> runCatching { ThemeMode.valueOf(value) }.getOrDefault(ThemeMode.SYSTEM) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeMode.SYSTEM,
        )
}
