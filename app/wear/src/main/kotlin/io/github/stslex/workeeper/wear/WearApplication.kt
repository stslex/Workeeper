package io.github.stslex.workeeper.wear

import android.app.Application
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.Log

class WearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseCrashlyticsHolder.setCustomKey("platform", "watch")
        FirebaseCrashlyticsHolder.initialize()
        Log.isLogging = BuildConfig.DEBUG || BuildConfig.FLAVOR == "dev"
    }
}
