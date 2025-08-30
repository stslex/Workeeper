package io.github.stslex.workeeper.core.core.result

import io.github.stslex.workeeper.core.core.result.ResultUtils.fold
import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.model.AppError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

class ResultFlow<T : Any>(
    private val flow: Flow<AppResult<T>>
) {

    private var onError: ((AppError) -> Unit)? = null
    private var onLoading: (() -> Unit)? = null
    private var onSuccess: ((T) -> Unit)? = null

    fun onError(block: (AppError) -> Unit): ResultFlow<T> {
        onError = block
        return this
    }

    fun onLoading(block: () -> Unit): ResultFlow<T> {
        onLoading = block
        return this
    }

    fun onSuccess(block: (T) -> Unit): ResultFlow<T> {
        onSuccess = block
        return this
    }

    fun collect(scope: AppCoroutineScope): Job = flow.fold(
        scope = scope,
        onError = { onError?.invoke(it) ?: Log.tag(TAG).e(it, it.message) },
        onLoading = { onLoading?.invoke() ?: Log.tag(TAG).i("Loading...") },
        onSuccess = { onSuccess?.invoke(it) ?: Log.tag(TAG).i("Success: $it") }
    )

    companion object {

        private const val TAG = "ResultFlow"
    }
}