package io.github.stslex.workeeper.core.ui.navigation

data class SaveHandlerAttr<T>(
    val key: String,
    val defaultValue: T? = null,
) {

    fun toPairValue(value: T?): Pair<String, T?> = key to value
}
