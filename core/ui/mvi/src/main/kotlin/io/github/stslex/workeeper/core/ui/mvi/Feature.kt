package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Component
import org.koin.core.component.KoinScopeComponent
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.core.scope.Scope
import kotlin.reflect.KClass

/**
 * Feature is a Koin feature module that provides a StoreProcessor.
 * It is responsible for managing the state and actions related to the feature.
 *
 * @see [StoreProcessor]
 * */
@Immutable
abstract class Feature<TProcessor : StoreProcessor<*, *, *>, TComponent : Component>(
    scopeClass: KClass<*>
) : KoinScopeComponent {

    open val loadModule: Module? get() = null

    private val scopeName: String = requireNotNull(scopeClass.qualifiedName) {
        "Scope name is null. Please check the SettingsFeature class."
    }

    final override val scope: Scope by lazy {
        getKoin().getOrCreateScope(
            scopeId = scopeName,
            qualifier = qualifier(scopeName)
        )
    }

    @Composable
    abstract fun processor(component: TComponent): TProcessor
}