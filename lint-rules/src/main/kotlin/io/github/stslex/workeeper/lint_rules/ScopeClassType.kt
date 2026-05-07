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