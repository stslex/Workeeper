package io.github.stslex.workeeper.wear

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.wear.di.WearApplicationGraph

class WearApplication : Application() {
    internal val appScopeLifetime = AppScopeLifetime()
    internal val graph: WearApplicationGraph by lazy {
        createGraphFactory<WearApplicationGraph.Factory>().create(appScopeLifetime)
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseCrashlyticsHolder.setCustomKey("platform", "watch")
        FirebaseCrashlyticsHolder.initialize()
        Log.isLogging = BuildConfig.DEBUG || BuildConfig.FLAVOR == "dev"
    }
}
