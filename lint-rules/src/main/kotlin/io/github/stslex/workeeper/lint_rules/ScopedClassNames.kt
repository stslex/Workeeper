// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

/**
 * Class-name predicates shared by the Metro DI rules.
 *
 * [isScopeChecked] is the dependency-bucket test [MetroScopeRule] applies: a Metro-injected class whose
 * name matches one of the fragments below must declare a `@SingleIn(<Scope>::class)`. It used to be an
 * enum (`ScopeClassType`) with a `SINGLETON` / `FEATURE_SCOPED` split, but the split was write-only —
 * the single consumer only compared the result against `null`, and the Handler bucket is re-derived
 * inline where the app-scope guard needs it — so the classifier is a `Boolean`.
 *
 * [isStoreImpl] is the Store exemption that [MetroScopeRule] and [ScreenInjectionRule] both need. An MVI
 * `*StoreImpl` is intentionally UNSCOPED (retained by the Android `ViewModelStore` via
 * `rememberMetroStoreProcessor`) and its primary constructor is the one legitimate sink for the
 * navigation route arg. A `*HandlerStoreImpl` is NOT such a Store: it is the feature-scoped
 * `BaseHandlerStore` event-relay adapter, so it stays scope-checked by [MetroScopeRule] and does not
 * inherit the route-arg exemption in [ScreenInjectionRule].
 */
internal object ScopedClassNames {

    /**
     * Name fragments of the injected dependency buckets that must declare a Metro scope. Matched by
     * `contains`, so `ExerciseRepositoryImpl`, `ArchiveInteractorImpl` and `ClickHandler` all match while
     * a name outside every bucket (e.g. `NavigatorEventBus`) is intentionally unconstrained.
     */
    private val scopeCheckedFragments = listOf(
        "Repository",
        "DataStore",
        "Database",
        "Storage",
        "StoreDispatchers",
        "Handler",
        "Interactor",
        "Mapper",
    )

    private const val STORE_IMPL_SUFFIX = "StoreImpl"

    private const val HANDLER_STORE_IMPL_SUFFIX = "HandlerStoreImpl"

    /** True when [name] falls into a dependency bucket that must carry a Metro `@SingleIn`. */
    fun isScopeChecked(name: String): Boolean = scopeCheckedFragments.any { name.contains(it) }

    /**
     * True when [name] is an MVI Store implementation. The `*HandlerStoreImpl` adapters are deliberately
     * excluded — they end with the same suffix but are ordinary feature-scoped graph nodes.
     */
    fun isStoreImpl(name: String): Boolean =
        name.endsWith(STORE_IMPL_SUFFIX) && name.endsWith(HANDLER_STORE_IMPL_SUFFIX).not()
}
