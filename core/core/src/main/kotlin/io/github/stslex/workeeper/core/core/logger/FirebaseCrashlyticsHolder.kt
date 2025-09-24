package io.github.stslex.workeeper.core.core.logger

import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.crashlytics.recordException

object FirebaseCrashlyticsHolder {

    private const val SCREEN_NAME_KEY = "SCREEN_NAME"
    private const val UNRESOLVE_SCREEN_NAME = "UNRESOLVED"

    private val crashlytics by lazy { Firebase.crashlytics }

    fun log(message: String) {
        crashlytics.log(message)
    }

    fun recordException(
        throwable: Throwable,
        tag: String,
    ) {
        crashlytics.recordException(throwable) { key("TAG", tag) }
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
}
