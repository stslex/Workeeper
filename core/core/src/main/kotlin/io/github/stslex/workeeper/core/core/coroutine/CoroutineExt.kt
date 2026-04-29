package io.github.stslex.workeeper.core.core.coroutine

import io.github.stslex.workeeper.core.core.logger.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Log.e(throwable)
}

suspend fun <T, R> Collection<T>.asyncMap(
    transform: suspend (T) -> R,
): List<R> = coroutineScope {
    map { item -> async { transform(item) } }
}.awaitAll()

suspend fun <T, R> Collection<T>.asyncMapIndexed(
    transform: suspend (Int, T) -> R,
): List<R> = coroutineScope {
    mapIndexed { index, item -> async { transform(index, item) } }
}.awaitAll()

suspend fun <T> asyncScope(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T,
): Deferred<T> = coroutineScope {
    async(
        context = context,
        start = start,
        block = block,
    )
}

suspend fun <K, V, R> Map<K, V>.asyncMap(
    transform: suspend (Map.Entry<K, V>) -> R,
): List<R> = coroutineScope {
    this@asyncMap.map { entry -> async { transform(entry) } }
}.awaitAll()

suspend fun <T> Collection<T>.asyncForEach(
    action: suspend (T) -> Unit,
) {
    coroutineScope {
        map { item -> async { action(item) } }
    }.awaitAll()
}
