package io.github.stslex.workeeper.core.ui.mvi.performance

import com.google.firebase.perf.metrics.Trace

internal interface PerfTrace {

    fun putAttribute(key: String, value: String)

    fun start()

    fun stop()
}

internal fun interface PerfTraceFactory {

    fun create(name: String): PerfTrace
}

/** GUARD: production creates real Firebase traces; only host tests substitute this factory. */
internal object FirebaseTraceFactory : PerfTraceFactory {

    override fun create(name: String): PerfTrace = FirebasePerfTrace(Trace.create(name))
}

private class FirebasePerfTrace(
    private val trace: Trace,
) : PerfTrace {

    override fun putAttribute(key: String, value: String) {
        trace.putAttribute(key, value)
    }

    override fun start() {
        trace.start()
    }

    override fun stop() {
        trace.stop()
    }
}
