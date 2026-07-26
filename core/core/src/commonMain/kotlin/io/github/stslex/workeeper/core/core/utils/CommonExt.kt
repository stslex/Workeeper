package io.github.stslex.workeeper.core.core.utils

import io.github.stslex.workeeper.core.core.logger.Log
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

object CommonExt {

    /**
     * The gate [traceExecutionTime] reads. On Android `BaseApplication.onCreate` assigns it
     * `isDebugLoggingAllow` — the same value it assigns to [Log.isLogging] — so tracing follows
     * the build flavour's debug-logging flag. Kept as a settable flag rather than an
     * `expect val`/`BuildConfig` read because the KMP `android` library target (AGP 9.x) does not
     * expose `buildConfig`, and this keeps the gate identical across android + ios with no
     * generated-source plumbing.
     *
     * Unlike [Log.isLogging] (which defaults to `true`) this defaults to `false`, so any target
     * that never runs that bootstrap — the iOS target, JVM unit tests — does not trace.
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
