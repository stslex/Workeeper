package io.github.stslex.workeeper.core.ui.mvi.performance

import io.github.stslex.workeeper.core.core.logger.Log

object PerformanceMetricsRecorder {

    private val log = Log.tag("PerformanceMetricsRecorder")

    fun process(action: RecordAction) {
        log.i { "process action: $action" }
        platformPerformanceBackend.process(action)
    }
}
