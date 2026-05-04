package io.github.stslex.workeeper.core.ui.mvi.performance

import com.google.firebase.perf.metrics.Trace
import io.github.stslex.workeeper.core.core.logger.Log

internal class PerformanceRecorder(
    val type: RecordType,
) {
    val tracePrefix: String = type.tag

    private val log = Log.tag("${tracePrefix}_$TAG")
    private var trace: Trace? = null
    private var traceName: String? = null
    private var processed = false

    fun start(
        name: String,
        vararg attrs: Pair<String, String>,
    ) {
        if (processed && type.singleShot) return
        log.d { "Starting new trace for $name with $attrs" }

        trace?.let {
            log.d("Stopping previous trace: ${traceName ?: "unknown"}")
            it.putAttribute("aborted", "true")
            it.stop()
        }

        traceName = "${tracePrefix}_$name"
        trace = Trace.create("${tracePrefix}_$name").apply {
            attrs.forEach { (key, value) ->
                putAttribute(key, value)
            }
            start()
        }
        processed = true
    }

    fun stop(name: String) {
        if (trace == null || traceName != "${tracePrefix}_$name") {
            return
        }

        log.d("Trace stopped for $name")

        trace?.stop()
        trace = null
        traceName = null
    }

    fun clear() {
        trace?.stop()
        trace = null
        traceName = null
        processed = false
    }

    enum class RecordType(
        val tag: String,
        val singleShot: Boolean,
    ) {
        TTID("TTID", false),
        AppCreate("AppCreate", true),
        ActivityCreate("ActivityCreate", false),
    }

    companion object {

        private const val TAG = "PerformanceRecorder"
    }
}
