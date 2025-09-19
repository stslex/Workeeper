package io.github.stslex.workeeper.core.core.result

interface AppResultMapper<out T : Any> {

    fun <R : Any> map(mapper: Mapper<T, R>): AppResult<R>
}