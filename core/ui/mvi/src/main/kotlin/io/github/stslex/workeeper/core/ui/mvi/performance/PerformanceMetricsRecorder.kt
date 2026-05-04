package io.github.stslex.workeeper.core.ui.mvi.performance

import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceRecorder.RecordType

object PerformanceMetricsRecorder {

    private val log = Log.tag("PerformanceMetricsRecorder")
    private val ttidRecorder = PerformanceRecorder(RecordType.TTID)
    private val appCreateRecorder = PerformanceRecorder(RecordType.AppCreate)
    private val activityCreateRecorder = PerformanceRecorder(RecordType.ActivityCreate)

    @Synchronized
    fun process(action: RecordAction) {
        log.i { "process action: $action" }
        when (action) {
            is RecordAction.ActivityCreated -> activityCreateRecorder.start(
                "MainActivity",
                "coldStart" to action.coldStart.toString(),
            )

            is RecordAction.AppCreated -> appCreateRecorder.start("App")

            is RecordAction.Navigation<*> -> ttidRecorder.start(
                action.screen.simpleName ?: "Unknown",
                "navType" to action.navType,
            )

            is RecordAction.OnScreenPlaced<*> -> {
                ttidRecorder.stop(action.screen.simpleName ?: "Unknown")
                appCreateRecorder.stop("App")
                activityCreateRecorder.stop("MainActivity")
            }

            RecordAction.ClearTraces -> {
                ttidRecorder.clear()

                appCreateRecorder.clear()
                activityCreateRecorder.clear()
            }
        }
    }
}
