// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

/**
 * The constructor-injected dependency buckets [MetroScopeRule] scope-checks, matched by class-name
 * substring. A name that matches a bucket must declare a Metro `@SingleIn(<Scope>::class)`.
 *
 * The `Store` bucket was dropped along with the Hilt-era `@HiltViewModel` branch: a Metro `Store` is
 * intentionally UNSCOPED (retained by the Android `ViewModelStore` via `rememberMetroStoreProcessor`) and
 * carries a class-level `@Inject`, so it never reaches this classifier — the rule short-circuits on its
 * empty primary-constructor annotations first.
 */
enum class ScopeClassType {
    SINGLETON,
    FEATURE_SCOPED,
    ;

    companion object {

        private val singletonClasses = listOf(
            "Repository",
            "DataStore",
            "Database",
            "Storage",
            "StoreDispatchers",
        )
        private val featureScopedClasses = listOf(
            "Handler",
            "Interactor",
            "Mapper",
        )

        fun getByName(
            name: String,
        ): ScopeClassType? = when {
            singletonClasses.any { name.contains(it) } -> SINGLETON
            featureScopedClasses.any { name.contains(it) } -> FEATURE_SCOPED
            else -> null
        }
    }
}
