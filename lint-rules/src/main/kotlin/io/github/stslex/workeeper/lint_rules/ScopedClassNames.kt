// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

/**
 * Class-name predicates shared by the Metro DI rules: the scope-checked dependency buckets, and the
 * `*StoreImpl` exemption that `*HandlerStoreImpl` deliberately does not inherit.
 */
internal object ScopedClassNames {

    /**
     * Name fragments of the injected dependency buckets that must declare a Metro scope, matched by
     * `contains`; a name outside every bucket is intentionally unconstrained.
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
     * True when [name] is an MVI Store implementation; the `*HandlerStoreImpl` adapters share the
     * suffix but are ordinary feature-scoped graph nodes and are excluded.
     */
    fun isStoreImpl(name: String): Boolean =
        name.endsWith(STORE_IMPL_SUFFIX) && name.endsWith(HANDLER_STORE_IMPL_SUFFIX).not()
}
