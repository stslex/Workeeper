package io.github.stslex.workeeper.core.ui.mvi.performance

import android.app.Activity
import com.google.firebase.perf.application.FrameMetricsRecorder
import com.google.firebase.perf.metrics.Trace
import com.google.firebase.perf.util.Constants
import com.google.firebase.perf.util.ScreenTraceUtil
import io.github.stslex.workeeper.core.core.logger.Log
import java.util.concurrent.ConcurrentHashMap

object FirebaseScreenRenderRecorder {

    private const val TAG = "FirebaseRecorder"
    private val log = Log.tag(TAG)
    private val traces = ConcurrentHashMap<String, Trace>()
    private val recorders = ConcurrentHashMap<String, FrameMetricsRecorder>()

    fun recordScreenTrace(
        screenName: String,
        activity: Activity?,
    ) {
        if (activity == null) {
            val e = IllegalStateException(
                "Failure while recording performance metrics. Activity is null for screen $screenName",
            )
            log.e(e)
            return
        }

        val recorder = FrameMetricsRecorder(activity)
        val trace = Trace.create(Constants.SCREEN_TRACE_PREFIX + screenName)

        recorder.start()
        trace.start()

        traces[screenName] = trace
        recorders[screenName] = recorder
    }

    fun stopScreenTrace(screenName: String) {
        val trace = traces[screenName]
        val recorder = recorders[screenName]

        if (trace == null || recorder == null) {
            val e = IllegalStateException(
                """
                    Failure while stopping performance metrics recording for screen $screenName. 
                    Trace or recorder not found.
                """.trimIndent(),
            )
            log.e(e)
            return
        }

        val calculation = runCatching {
            recorder.stop()
        }
            .onFailure { e -> log.e(e) }
            .getOrNull()

        if (calculation?.isAvailable == true) {
            calculation.get()?.let { recorders ->
                log.i {
                    """
                        Performance metrics for screen $screenName:
                         - Frozen frames: ${recorders.frozenFrames}
                         - Slow frames: ${recorders.slowFrames}
                         - Total frames: ${recorders.totalFrames}
                    """.trimIndent()
                }
                ScreenTraceUtil.addFrameCounters(trace, recorders)
            }
        }

        trace.stop()

        traces.remove(screenName)
        recorders.remove(screenName)
    }

    fun clearAllTraces() {
        traces.keys.forEach { key -> stopScreenTrace(key) }
    }
}
