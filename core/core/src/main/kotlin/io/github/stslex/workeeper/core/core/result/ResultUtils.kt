package io.github.stslex.workeeper.core.core.result

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.model.AppError
import io.github.stslex.workeeper.core.core.model.UnresolveError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object ResultUtils {

    private const val TAG = "RESULT_CATCHER"
    private val logger = Log.tag(TAG)

    fun <T : Any> Flow<AppResult<T>>.onError(
        block: (AppError) -> Unit
    ): ResultFlow<T> = ResultFlow(this).onError(block)

    fun <T : Any> Flow<AppResult<T>>.onLoading(
        block: () -> Unit
    ): ResultFlow<T> = ResultFlow(this).onLoading(block)

    fun <T : Any> Flow<AppResult<T>>.onSuccess(
        block: (T) -> Unit
    ): ResultFlow<T> = ResultFlow(this).onSuccess(block)

    fun <T : Any> Flow<AppResult<T>>.fold(
        scope: AppCoroutineScope,
        onError: suspend (AppError) -> Unit = { logger.e(it, it.message) },
        onLoading: suspend () -> Unit = {},
        onSuccess: suspend (T) -> Unit,
    ): Job = scope
        .launch(
            flow = this,
            onError = { onError(UnresolveError(it, it.message)) }) { result ->
            when (result) {
                is AppResult.Success -> onSuccess(result.data)
                is AppResult.Error -> onError(result.error)
                AppResult.Loading -> onLoading()
            }
        }

    fun <T : Any, R : Any> flowRunCatching(
        mapper: Mapping<T, R>,
        block: suspend () -> T
    ): Flow<AppResult<R>> = flowRunCatching { mapper(block()) }

    fun <T : Any> flowRunCatching(
        block: suspend () -> T
    ): Flow<AppResult<T>> = flow {
        val result = runCatching {
            block()
        }.fold(
            onSuccess = { data ->
                AppResult.Success(data)
            },
            onFailure = { error ->
                logger.e(error, error.message)
                if (error is AppError) {
                    AppResult.Error(error)
                } else {
                    AppResult.Error(UnresolveError(error, error.message))
                }
            }
        )
        emit(result)
    }

    fun <T : Any, R : Any> appRunCatching(
        mapper: Mapping<T, R>,
        block: () -> T
    ): AppResult<R> = appRunCatching { mapper(block()) }

    fun <T : Any> appRunCatching(
        block: () -> T
    ): AppResult<T> = runCatching {
        block()
    }.fold(
        onSuccess = { data ->
            AppResult.Success(data)
        },
        onFailure = { error ->
            logger.e(error, error.message)
            if (error is AppError) {
                AppResult.Error(error)
            } else {
                AppResult.Error(
                    UnresolveError(
                        message = error.message,
                        cause = error
                    )
                )
            }
        }
    )

    suspend fun <T : Any, R : Any> suspendRunCatching(
        mapper: Mapping<T, R>,
        block: suspend () -> T
    ): AppResult<R> = suspendRunCatching { mapper(block()) }

    suspend fun <T : Any> suspendRunCatching(
        block: suspend () -> T
    ): AppResult<T> = runCatching {
        block()
    }.fold(
        onSuccess = { data ->
            AppResult.Success(data)
        },
        onFailure = { error ->
            logger.e(error, error.message)
            if (error is AppError) {
                AppResult.Error(error)
            } else {
                AppResult.Error(
                    UnresolveError(
                        message = error.message,
                        cause = error
                    )
                )
            }
        }
    )

}