package io.github.stslex.workeeper

import android.app.Application
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.Log

abstract class BaseApplication : Application() {

    abstract val isDebugLoggingAllow: Boolean

    override fun onCreate() {
        super.onCreate()
        FirebaseCrashlyticsHolder.initialize()
        Log.isLogging = isDebugLoggingAllow
    }
}
