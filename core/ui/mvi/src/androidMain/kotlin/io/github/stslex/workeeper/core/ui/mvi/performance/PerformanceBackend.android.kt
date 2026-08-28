package io.github.stslex.workeeper.core.ui.mvi.performance

internal actual val platformPerformanceBackend: PerformanceBackend = FirebasePerformanceBackend()

internal class FirebasePerformanceBackend(
    traces: PerfTraceFactory = FirebaseTraceFactory,
) : PerformanceBackend {

    private var recorders = Recorders(traces)

    // GUARD: this monitor serializes the three recorders' mutable trace state. It is not Store
    // event ordering.
    @Synchronized
    override fun process(action: RecordAction) {
        when (action) {
            is RecordAction.ActivityCreated -> recorders.activityCreate.start(
                "MainActivity",
                "coldStart" to action.coldStart.toString(),
            )

            is RecordAction.AppCreated -> recorders.appCreate.start("App")

            is RecordAction.Navigation<*> -> recorders.ttid.start(
                action.screen.simpleName ?: "Unknown",
                "navType" to action.navType,
            )

            is RecordAction.OnScreenPlaced<*> -> {
                recorders.ttid.stop(action.screen.simpleName ?: "Unknown")
                recorders.appCreate.stop("App")
                recorders.activityCreate.stop("MainActivity")
            }

            RecordAction.ClearTraces -> {
                recorders.ttid.clear()

                recorders.appCreate.clear()
                recorders.activityCreate.clear()
            }
        }
    }

    /** Runs the public façade against deterministic sinks while retaining this provider object. */
    internal fun <T> withTraceFactoryForTest(
        traces: PerfTraceFactory,
        block: () -> T,
    ): T = synchronized(this) {
        val productionRecorders = recorders
        recorders = Recorders(traces)
        try {
            block()
        } finally {
            recorders = productionRecorders
        }
    }

    private class Recorders(traces: PerfTraceFactory) {

        val ttid = PerformanceRecorder(PerformanceRecorder.RecordType.TTID, traces)
        val appCreate = PerformanceRecorder(PerformanceRecorder.RecordType.AppCreate, traces)
        val activityCreate =
            PerformanceRecorder(PerformanceRecorder.RecordType.ActivityCreate, traces)
    }
}
