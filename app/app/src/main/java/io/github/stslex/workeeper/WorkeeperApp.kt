package io.github.stslex.workeeper

import android.app.Application
import io.github.stslex.workeeper.app.app.BuildConfig
import io.github.stslex.workeeper.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

open class WorkeeperApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            if (BuildConfig.DEBUG) {
                androidLogger(Level.DEBUG)
            }
            androidContext(this@WorkeeperApp)
            modules(appModules)
        }
    }
}