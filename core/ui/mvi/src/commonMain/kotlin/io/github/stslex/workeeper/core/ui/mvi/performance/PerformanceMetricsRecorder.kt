package io.github.stslex.workeeper.core.ui.mvi.performance

import io.github.stslex.workeeper.core.core.logger.Log

object PerformanceMetricsRecorder {

    private val log = Log.tag("PerformanceMetricsRecorder")

    fun process(action: RecordAction) {
        process(action, platformPerformanceBackend)
    }

    // GUARD: this overload lets the Android host oracle enter this façade with deterministic
    // sinks without replacing the production provider used by the public overload.
    internal fun process(action: RecordAction, backend: PerformanceBackend) {
        log.i { "process action: $action" }
        backend.process(action)
    }

    // GUARD: read by the Android host oracle to prove the platform actual is Firebase-backed.
    internal val backend: PerformanceBackend get() = platformPerformanceBackend
}
