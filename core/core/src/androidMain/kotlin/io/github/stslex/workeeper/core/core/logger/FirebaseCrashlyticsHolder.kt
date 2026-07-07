package io.github.stslex.workeeper.core.core.logger

import com.google.firebase.Firebase
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.crashlytics.recordException

actual object FirebaseCrashlyticsHolder {

    private const val SCREEN_NAME_KEY = "SCREEN_NAME"
    private const val UNRESOLVE_SCREEN_NAME = "UNRESOLVED"

    // Tolerate an uninitialised Firebase context — production app modules always init it,
    // but instrumentation tests use HiltTestApplication which doesn't run BaseApplication's
    // setup. Without this guard the first log call from any production code path throws
    // `Default FirebaseApp is not initialized`. Returning null short-circuits every public
    // method into a no-op, which is the right behaviour for a crash-reporting sink that has
    // no transport configured.
    private val crashlytics: FirebaseCrashlytics? by lazy {
        runCatching { Firebase.crashlytics }.getOrNull()
    }
    private val filter = EventsFilter()

    @Synchronized
    actual fun log(message: String) {
        val sink = crashlytics ?: return
        filter(message) { sink.log(message) }
    }

    @Synchronized
    actual fun recordException(
        throwable: Throwable,
        tag: String,
    ) {
        val sink = crashlytics ?: return
        filter(throwable.message.orEmpty()) {
            sink.recordException(throwable) {
                key("TAG", tag)
            }
        }
        filter.clear()
    }

    actual fun setCustomKey(key: String, value: String) {
        val sink = crashlytics ?: return
        sink.setCustomKey(key, value)
    }

    actual fun setCustomKey(key: String, value: Int) {
        val sink = crashlytics ?: return
        sink.setCustomKey(key, value)
    }

    actual fun setCustomKey(key: String, value: Long) {
        val sink = crashlytics ?: return
        sink.setCustomKey(key, value)
    }

    actual fun setCustomKey(key: String, value: Boolean) {
        val sink = crashlytics ?: return
        sink.setCustomKey(key, value)
    }

    actual fun setScreenName(name: String) {
        setCustomKey(SCREEN_NAME_KEY, name)
    }

    actual fun clearScreenName() {
        setCustomKey(SCREEN_NAME_KEY, UNRESOLVE_SCREEN_NAME)
    }

    actual fun initialize() {
        val sink = crashlytics ?: return
        if (sink.didCrashOnPreviousExecution()) {
            sink.sendUnsentReports()
        }
        clearScreenName()
    }
}
