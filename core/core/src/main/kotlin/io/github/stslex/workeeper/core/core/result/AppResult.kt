package io.github.stslex.workeeper.core.core.result

import io.github.stslex.workeeper.core.core.model.AppError
import io.github.stslex.workeeper.core.core.model.UnresolveError

sealed interface AppResult<out T : Any> : AppResultMapper<T> {

    data class Success<T : Any>(val data: T) : AppResult<T> {

        override fun <R : Any> map(mapper: Mapper<T, R>): AppResult<R> = Success(mapper(data))
    }

    data class Error(val error: AppError) : AppResult<Nothing> {

        override fun <R : Any> map(mapper: Mapper<Nothing, R>): AppResult<R> = Error(error)
    }

    data object Loading : AppResult<Nothing> {

        override fun <R : Any> map(mapper: Mapper<Nothing, R>): AppResult<R> = Loading
    }

    companion object {

        fun <T : Any> success(value: T): AppResult<T> = Success(value)

        fun <T : Any> error(error: AppError): AppResult<T> = Error(error)

        fun <T : Any> error(
            error: Throwable
        ): AppResult<T> = if (error is AppError) {
            error(error)
        } else {
            error(UnresolveError(error))
        }

        fun <T : Any> loading(): AppResult<T> = Loading

        inline fun <T : Any> AppResult<T>.onError(
            action: (AppError) -> Unit
        ): AppResult<T> = apply { (this as? Error)?.let { action(it.error) } }

        inline fun <T : Any> AppResult<T>.onLoading(action: () -> Unit): AppResult<T> = apply {
            if (this is Loading) action()
        }

        inline fun <T : Any> AppResult<T>.onSuccess(
            action: (T) -> Unit
        ): AppResult<T> = this.apply {
            (this as? Success<T>)?.let { action(it.data) }
        }

    }
}
