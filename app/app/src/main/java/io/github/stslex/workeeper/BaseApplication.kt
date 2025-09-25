package io.github.stslex.workeeper

import android.app.Application
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.KoinLogger
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.di.ApplicationModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.ksp.generated.module

abstract class BaseApplication : Application() {

    abstract val isDebugLoggingAllow: Boolean

    override fun onCreate() {
        super.onCreate()
        FirebaseCrashlyticsHolder.initialize()
        Log.isLogging = isDebugLoggingAllow
        startKoin {
            logger(KoinLogger(isDebug = isDebugLoggingAllow))
            androidContext(this@BaseApplication)
            modules(ApplicationModule().module)
        }
    }
}
