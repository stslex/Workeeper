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

// App-Scope Collapse Step 6 (cut): plain ViewModel (was the last @HiltViewModel). Constructed in App.kt
// via viewModel {} with deps read from the app graph — commonDataStore via context.appDeps<T>(),
// navigatorEventBus from the internal AppGraph (concrete, app-internal accessor).
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
