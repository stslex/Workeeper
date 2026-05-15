// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.lint_rules

enum class ScopeClassType(
    val annotation: String?,
) {
    SINGLETON(
        annotation = "Singleton",
    ),
    VIEW_MODEL_SCOPED(
        annotation = "ViewModelScoped",
    ),
    HiltViewModelScoped(
        annotation = "HiltViewModel",
    );

    companion object {

        private val singletonClasses = listOf(
            "Repository",
            "DataStore",
            "Database",
            "Storage",
            "StoreDispatchers",
            // app-scoped, DataStore-backed cross-feature dialog catalog.
            // The only Store in the codebase that lives at SingletonComponent
            // scope — see documentation/lint-rules.md → HiltScopeRule and
            // documentation/feature-specs/app-dialogs.md → DI table.
            "AppDialogStore",
        )
        private val viewModelScopedClasses = listOf(
            "Handler",
            "Interactor",
            "Mapper",
        )

        private val storeScopeClasses = listOf(
            "Store",
        )

        fun getByName(
            name: String,
        ): ScopeClassType? = when {
            singletonClasses.any { name.contains(it) } -> SINGLETON
            viewModelScopedClasses.any { name.contains(it) } -> VIEW_MODEL_SCOPED
            storeScopeClasses.any { name.contains(it) } -> HiltViewModelScoped
            else -> null
        }
    }
}