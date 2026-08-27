package io.github.stslex.workeeper.core.ui.mvi.performance

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberPlatformScreenRenderRecorder(): ScreenRenderRecorder {
    val activity = LocalActivity.current
    val context = LocalContext.current
    // GUARD: the `as? Activity` fallback is load-bearing — LocalActivity is null under some test
    // and embedded hosts, and FirebaseScreenRenderRecorder keeps the null-Activity diagnostic.
    return remember(activity, context) {
        FirebaseScreenRenderAdapter(activity = activity ?: context as? Activity)
    }
}

internal class FirebaseScreenRenderAdapter(
    private val activity: Activity?,
    /** GUARD: production keeps the Firebase sink; host tests may inject only at construction. */
    internal val sink: ScreenTraceSink = FirebaseScreenTraceSink,
) : ScreenRenderRecorder {

    override fun start(screenName: String) {
        sink.start(screenName = screenName, activity = activity)
    }

    override fun stop(screenName: String) {
        sink.stop(screenName)
    }
}

internal interface ScreenTraceSink {

    fun start(screenName: String, activity: Activity?)

    fun stop(screenName: String)
}

internal object FirebaseScreenTraceSink : ScreenTraceSink {

    override fun start(screenName: String, activity: Activity?) {
        FirebaseScreenRenderRecorder.recordScreenTrace(screenName = screenName, activity = activity)
    }

    override fun stop(screenName: String) {
        FirebaseScreenRenderRecorder.stopScreenTrace(screenName)
    }
}
