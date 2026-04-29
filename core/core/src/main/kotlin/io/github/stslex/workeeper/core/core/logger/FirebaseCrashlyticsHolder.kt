package io.github.stslex.workeeper.core.core.logger

import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.crashlytics.recordException

object FirebaseCrashlyticsHolder {

    private const val SCREEN_NAME_KEY = "SCREEN_NAME"
    private const val UNRESOLVE_SCREEN_NAME = "UNRESOLVED"
    private const val LAST_EVENT_TIME_DIFF = 2_000L
    private var lastEvent: Pair<String, Long>? = null

    private val crashlytics by lazy { Firebase.crashlytics }

    @Synchronized
    fun log(message: String) {
        runIfNameChanged(message) { crashlytics.log(message) }
    }

    @Synchronized
    fun recordException(
        throwable: Throwable,
        tag: String,
    ) {
        crashlytics.recordException(throwable) { key("TAG", tag) }
        lastEvent = null
    }

    fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }

    fun setScreenName(name: String) {
        setCustomKey(SCREEN_NAME_KEY, name)
    }

    fun clearScreenName() {
        setCustomKey(SCREEN_NAME_KEY, UNRESOLVE_SCREEN_NAME)
    }

    fun initialize() {
        if (crashlytics.didCrashOnPreviousExecution()) {
            crashlytics.sendUnsentReports()
        }
        clearScreenName()
    }

    private fun runIfNameChanged(name: String, block: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        val lastEvent = lastEvent
        if (
            lastEvent == null ||
            lastEvent.first != name ||
            (currentTime - lastEvent.second) > LAST_EVENT_TIME_DIFF
        ) {
            block()
        }
        this.lastEvent = Pair(name, currentTime)
    }
}
