package io.github.stslex.workeeper.core.core.coroutine

import io.github.stslex.workeeper.core.core.logger.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    Log.e(throwable)
}

suspend fun <T, R> Iterable<T>.asyncMap(
    transform: suspend (T) -> R,
): List<R> = coroutineScope {
    map { item -> async { transform(item) } }
}.awaitAll()

suspend fun <T, R> Iterable<T>.asyncMapNotNull(
    transform: suspend (T) -> R?,
): List<R> = coroutineScope {
    map { item -> async { transform(item) } }
}
    .awaitAll()
    .filterNotNull()

/**
 * Runs [transform] in parallel for each element and assembles the resulting pairs into a
 * single [Map]. Each transform launches as an `async` child of `coroutineScope`, so all
 * calls run concurrently and the function suspends until every child completes.
 *
 * Duplicate keys: last-write-wins. Each transform stores its result via
 * `mutableMap[key] = value` on a shared [MutableMap]; with concurrent execution, completion
 * order is not deterministic, so the surviving value for any duplicated key is whichever
 * transform happened to write last. If callers need a stable resolution, they must
 * de-duplicate before calling.
 */
suspend inline fun <T, K : Any, V : Any> Iterable<T>.asyncAssociate(
    crossinline transform: suspend (T) -> Pair<K, V>,
): Map<K, V> {
    val resultMap = ConcurrentHashMap<K, V>()
    coroutineScope {
        map { element ->
            async {
                transform(element).let { (key, value) ->
                    resultMap[key] = value
                }
            }
        }.awaitAll()
    }
    return resultMap.toMap()
}

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

/**
 * Evaluates [predicate] in parallel for each element and returns `true` only when every
 * element satisfies it. All predicates are awaited (no short-circuit) — if early termination
 * matters, prefer the sequential `Iterable.all { ... }` instead.
 */
suspend inline fun <T> Iterable<T>.asyncAll(
    crossinline predicate: suspend (T) -> Boolean,
): Boolean = asyncMap { predicate(it) }.all { it }

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
