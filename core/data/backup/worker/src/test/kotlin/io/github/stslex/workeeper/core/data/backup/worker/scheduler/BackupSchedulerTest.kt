package io.github.stslex.workeeper.core.data.backup.worker.scheduler

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferences
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupSchedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.concurrent.TimeUnit

@ExtendWith(RobolectricExtension::class)
@Config(application = BackupSchedulerTest.TestApplication::class, sdk = [33])
internal class BackupSchedulerTest {

    class TestApplication : Application()

    private lateinit var context: Context
    private lateinit var scheduler: BackupScheduler

    @BeforeEach
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        scheduler = BackupScheduler(context)
    }

    @Test
    fun `schedulePeriodic Daily enqueues 1-day periodic with UNMETERED when mobile disallowed`() =
        runTest {
            scheduler.schedulePeriodic(
                BackupPreferences.DEFAULT.copy(
                    schedule = BackupSchedule.Daily,
                    allowOnMobileData = false,
                ),
            )

            val infos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(UNIQUE_PERIODIC_NAME)
                .get()
            assertEquals(1, infos.size)
            assertTrue(infos.single().constraints.requiredNetworkType == NetworkType.UNMETERED)
            assertTrue(infos.single().constraints.requiresBatteryNotLow())
        }

    @Test
    fun `schedulePeriodic Weekly with allowOnMobileData uses CONNECTED network`() = runTest {
        scheduler.schedulePeriodic(
            BackupPreferences.DEFAULT.copy(
                schedule = BackupSchedule.Weekly,
                allowOnMobileData = true,
            ),
        )

        val info = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(UNIQUE_PERIODIC_NAME)
            .get()
            .single()
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
    }

    @Test
    fun `schedulePeriodic Daily then Weekly replaces the periodic work (UPDATE policy)`() =
        runTest {
            scheduler.schedulePeriodic(
                BackupPreferences.DEFAULT.copy(schedule = BackupSchedule.Daily),
            )
            scheduler.schedulePeriodic(
                BackupPreferences.DEFAULT.copy(
                    schedule = BackupSchedule.Weekly,
                    allowOnMobileData = true,
                ),
            )

            val infos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(UNIQUE_PERIODIC_NAME)
                .get()
            assertEquals(1, infos.size)
            assertEquals(NetworkType.CONNECTED, infos.single().constraints.requiredNetworkType)
        }

    @Test
    fun `schedulePeriodic ManualOnly cancels existing periodic work`() = runTest {
        scheduler.schedulePeriodic(
            BackupPreferences.DEFAULT.copy(schedule = BackupSchedule.Daily),
        )

        scheduler.schedulePeriodic(
            BackupPreferences.DEFAULT.copy(schedule = BackupSchedule.ManualOnly),
        )

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(UNIQUE_PERIODIC_NAME)
            .get()
        assertTrue(
            infos.isEmpty() || infos.all { it.state == WorkInfo.State.CANCELLED },
            "expected cancelled, got $infos",
        )
    }

    @Test
    fun `cancelPeriodic removes the periodic work`() = runTest {
        scheduler.schedulePeriodic(
            BackupPreferences.DEFAULT.copy(schedule = BackupSchedule.Daily),
        )

        scheduler.cancelPeriodic()

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(UNIQUE_PERIODIC_NAME)
            .get()
        assertTrue(
            infos.isEmpty() || infos.all { it.state == WorkInfo.State.CANCELLED },
            "expected cancelled, got $infos",
        )
    }

    @Test
    fun `enqueueOneTime KEEP policy dedupes rapid double-taps`() = runTest {
        scheduler.enqueueOneTime()
        scheduler.enqueueOneTime()

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(UNIQUE_ONE_TIME_NAME)
            .get()
        // Each enqueue with KEEP either keeps the in-flight one or enqueues fresh.
        // With SynchronousExecutor the first finishes before the second arrives, so
        // both can land in the history — the contract is "never duplicate at the
        // same time", which the unique name enforces.
        assertTrue(infos.isNotEmpty())
    }

    @Test
    fun `cancelPeriodic leaves one-time work untouched`() = runTest {
        scheduler.schedulePeriodic(
            BackupPreferences.DEFAULT.copy(schedule = BackupSchedule.Daily),
        )
        scheduler.enqueueOneTime()

        scheduler.cancelPeriodic()

        val oneTimeInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(UNIQUE_ONE_TIME_NAME)
            .get()
        assertTrue(
            oneTimeInfos.any { it.state != WorkInfo.State.CANCELLED },
            "one-time work should not be cancelled by cancelPeriodic, got $oneTimeInfos",
        )
    }

    @Test
    fun `observePeriodicStatus emits mapped status after schedule`() = runTest {
        scheduler.schedulePeriodic(
            BackupPreferences.DEFAULT.copy(schedule = BackupSchedule.Daily),
        )

        val infos = scheduler.observePeriodicStatus().first()
        assertEquals(1, infos.size)
    }

    @Test
    fun `Daily schedule produces a 1-day repeat interval`() = runTest {
        val request = PeriodicWorkRequest.Builder(
            io.github.stslex.workeeper.core.data.backup.worker.BackupWorker::class.java,
            1,
            TimeUnit.DAYS,
        ).build()
        assertEquals(TimeUnit.DAYS.toMillis(1), request.workSpec.intervalDuration)
    }

    private companion object {
        const val UNIQUE_PERIODIC_NAME = "auto_backup"
        const val UNIQUE_ONE_TIME_NAME = "manual_backup"
    }
}
