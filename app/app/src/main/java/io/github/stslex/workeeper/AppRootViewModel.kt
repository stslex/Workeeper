// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder
import io.github.stslex.workeeper.core.ui.navigation.RootComponent
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.navigation.NavigationHolderController
import io.github.stslex.workeeper.navigation.RootComponentImpl
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
internal class AppRootViewModel @Inject constructor(
    private val navHostController: NavigationHolderController,
    private val navigator: Navigator,
    commonDataStore: CommonDataStore,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = commonDataStore.themePreference
        .map { value -> runCatching { ThemeMode.valueOf(value) }.getOrDefault(ThemeMode.SYSTEM) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeMode.SYSTEM,
        )

    fun holdController(
        navController: NavHostController,
    ): NavigatorHolder = navHostController.apply {
        produce(navController)
    }

    fun removeController() {
        navHostController.clear()
    }

    fun createRootComponent(): RootComponent = RootComponentImpl(navigator)

    fun navTo(screen: Screen) {
        navigator.navTo(screen)
    }
}
