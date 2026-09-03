package io.github.stslex.workeeper.core.ui.mvi.performance

import io.github.stslex.workeeper.core.ui.navigation.Screen
import kotlin.reflect.KClass

sealed interface RecordAction {

    data class ActivityCreated(
        val coldStart: Boolean,
    ) : RecordAction

    data object AppCreated : RecordAction

    sealed class Navigation<S : Screen>(
        val screen: KClass<S>,
        val navType: String,
    ) : RecordAction {

        class NavTo<S : Screen>(
            screen: KClass<S>,
        ) : Navigation<S>(screen, "nav_to")

        class ReplaceTo<S : Screen>(
            screen: KClass<S>,
        ) : Navigation<S>(screen, "replace")
    }

    data class OnScreenPlaced<S : Screen>(
        val screen: KClass<S>,
    ) : RecordAction

    data object ClearTraces : RecordAction
}
