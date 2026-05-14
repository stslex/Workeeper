// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceMetricsRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.RecordAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

abstract class BaseApplication : Application(), Configuration.Provider {

    abstract val isDebugLoggingAllow: Boolean

    @Inject
    internal lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        FirebaseCrashlyticsHolder.initialize()
        Log.isLogging = isDebugLoggingAllow
        cleanupOrphanedImageTempFiles()
        PerformanceMetricsRecorder.process(RecordAction.AppCreated)
    }

    private fun cleanupOrphanedImageTempFiles() {
        val imageStorage = EntryPointAccessors.fromApplication(
            this,
            ImageStorageEntryPoint::class.java,
        ).imageStorage()
        // Fire-and-forget on a one-shot IO coroutine — clearing temp files left
        // behind by killed camera-capture flows is best-effort.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            imageStorage.cleanupTempFiles()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface ImageStorageEntryPoint {
        fun imageStorage(): ImageStorage
    }
}
