package io.github.stslex.workeeper.lint_rules

enum class ScopeClassType(
    val annotation: String?,
) {
    SINGLETON(
        annotation = "Singleton",
    ),
    VIEW_MODEL_SCOPED(
        annotation = "ViewModelScoped",
    );

    companion object {

        private val singletonClasses = listOf(
            "Repository",
            "DataStore",
            "Database",
            "StoreDispatchers",
        )
        private val viewModelScopedClasses = listOf(
            "Handler",
            "Store",
            "Interactor",
            "Mapper",
        )

        fun getByName(
            name: String,
        ): ScopeClassType? = when {
            singletonClasses.any { name.contains(it) } -> SINGLETON
            viewModelScopedClasses.any { name.contains(it) } -> VIEW_MODEL_SCOPED
            else -> null
        }
    }
}