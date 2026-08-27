package io.github.stslex.workeeper.core.ui.mvi.performance

internal actual val platformPerformanceBackend: PerformanceBackend = FirebasePerformanceBackend()

internal class FirebasePerformanceBackend(
    traces: PerfTraceFactory = FirebaseTraceFactory,
) : PerformanceBackend {

    private val ttidRecorder = PerformanceRecorder(PerformanceRecorder.RecordType.TTID, traces)
    private val appCreateRecorder =
        PerformanceRecorder(PerformanceRecorder.RecordType.AppCreate, traces)
    private val activityCreateRecorder =
        PerformanceRecorder(PerformanceRecorder.RecordType.ActivityCreate, traces)

    // GUARD: this monitor serializes the three recorders' mutable trace state. It is not Store
    // event ordering.
    @Synchronized
    override fun process(action: RecordAction) {
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
