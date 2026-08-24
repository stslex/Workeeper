package io.github.stslex.workeeper.core.core.utils

import io.github.stslex.workeeper.core.core.logger.Log
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

object CommonExt {

    /**
     * The gate [traceExecutionTime] reads; Android's `BaseApplication.onCreate` assigns it.
     * Defaults to `false`, so targets that never run that bootstrap do not trace.
     */
    var isTraceExecutionEnabled: Boolean = false

    fun Uuid.Companion.parseOrRandom(
        uuidString: String?,
    ): Uuid = uuidString
        ?.let(Uuid::parse)
        ?: Uuid.random()

    inline fun <T> runIf(condition: Boolean, block: () -> T): T? = if (condition) block() else null

    inline fun <T, R> runIfNotNull(
        value: T?,
        block: (T) -> R,
    ): R? = if (value != null) block(value) else null

    fun <T> traceExecutionTime(
        name: String,
        msg: String? = null,
        block: () -> T,
    ): T {
        if (isTraceExecutionEnabled.not()) return block()
        val mark = TimeSource.Monotonic.markNow()
        val result = block()
        val elapsed = mark.elapsedNow()
        Log.tag("ExecutionTime_$name").i { "$name:$msg executed in ${elapsed.inWholeMilliseconds} ms" }
        return result
    }
}
