package io.github.stslex.workeeper.core.core.utils

import io.github.stslex.workeeper.core.core.BuildConfig
import io.github.stslex.workeeper.core.core.logger.Log
import kotlin.uuid.Uuid

object CommonExt {

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

    inline fun <T> traceExecutionTime(
        name: String,
        msg: String? = null,
        block: () -> T,
    ): T {
        if (BuildConfig.DEBUG.not()) return block()
        val startTime = System.currentTimeMillis()
        val result = block()
        val endTime = System.currentTimeMillis()
        Log.tag("ExecutionTime_$name").i { "$name:$msg executed in ${endTime - startTime} ms" }
        return result
    }
}
