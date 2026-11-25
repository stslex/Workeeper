// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.stslex.workeeper.app.ios

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.core.core.images.ImageRef
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSThread
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class IosRuntimeTest {
    @Test
    fun refreshDrainsExactUiAndDatabaseUsersBeforePublishing() = onMain {
        val fixture = IosGraphFixture()
        var opens = 0
        val runtime = IosRuntime(StartupImages, { false }, {
            assertFalse(NSThread.isMainThread)
            opens++
            fixture.database
        }, deadlineMillis = 500)
        try {
            runtime.start()
            val first = runtime.awaitGeneration()
            val token = checkNotNull(runtime.admitUiGeneration(first.id))
            val original = runtime.resolve() as IosAppGraph
            val oldHost = original.reinitializer
            val readerStarted = CompletableDeferred<Unit>()
            val releaseReader = CompletableDeferred<Unit>()
            coroutineScope {
                val read = async {
                    original.transactions {
                        val database = original.appDatabase
                        readerStarted.complete(Unit)
                        releaseReader.await()
                        assertNull(database.trainingDao.getById(Uuid.NIL))
                        database
                    }
                }
                readerStarted.await()
                oldHost.reinitialize()
                oldHost.reinitialize()
                runtime.problem.first { it != null }
                assertTrue(original.appScopeLifetime.isActive)
                assertNull(runtime.admitUiGeneration(first.id))
                assertFails { runtime.resolve() }
                runtime.releaseUiGeneration(token)
                runtime.retry().join()
                assertNotNull(runtime.problem.value, "Refresh completed with an admitted Room transaction")
                assertNull(runtime.currentId())
                assertTrue(original.appScopeLifetime.isActive)
                releaseReader.complete(Unit)
                assertSame(original.appDatabase, read.await())
            }
            runtime.retry().join()
            val next = runtime.awaitGeneration()
            assertEquals(first.id + 1, next.id)
            assertNotSame(first.viewModelStoreOwner, next.viewModelStoreOwner)
            assertFalse(original.appScopeLifetime.isActive)
            assertNull(runtime.admitUiGeneration(first.id))
            val grant = checkNotNull(runtime.admitUiGeneration(next.id))
            val graph = runtime.resolve() as IosAppGraph
            assertNotSame(original, graph)
            assertSame(original.appDatabase, graph.appDatabase)
            assertEquals(1, opens)
            runtime.releaseUiGeneration(grant)
            oldHost.reinitialize()
            assertEquals(next.id, runtime.currentId())
            runtime.openRecovery(next.id)
            assertEquals(IosHostProblem.RecoveryRequested, runtime.problem.value)
            assertFails { runtime.resolve() }
            runtime.retry().join()
            assertEquals(next.id + 1, runtime.awaitGeneration().id)
        } finally {
            withContext(NonCancellable) {
                runtime.dispose().join()
                assertTrue(runtime.isDisposed, "The fixture still owns its database")
                fixture.close(databaseAlreadyClosed = true)
            }
        }
    }

    @Test
    fun navigationOpensDatabaseIndependentRecovery() {
        val fixture = IosGraphFixture()
        val runtime = IosRuntime(StartupImages, { false }, { fixture.database })
        try {
            // Room opens on IO and publishes on the Darwin main run loop.
            onMain {
                runtime.start()
                runtime.awaitGeneration()
            }
            runComposeUiTest {
                setContent { WorkeeperRoot(runtime) }
                onNodeWithTag("HomeGraph").assertIsDisplayed()
                runOnIdle { (runtime.resolve() as IosAppGraph).navigator.openRecovery() }
                waitForIdle()
                onNodeWithText("Workeeper recovery").assertIsDisplayed()
                onNodeWithText("Retry").assertIsDisplayed()
                onNodeWithTag("HomeGraph").assertDoesNotExist()
                assertNull(runtime.currentId())
            }
        } finally {
            onMain {
                runtime.dispose().join()
                assertTrue(runtime.isDisposed)
                fixture.close(databaseAlreadyClosed = true)
            }
        }
    }

    private suspend fun IosRuntime.awaitGeneration(): AppUiPhase.Generation =
        appUiPhases.filterIsInstance<AppUiPhase.Generation>().first()

    private fun onMain(block: suspend () -> Unit) {
        check(NSThread.isMainThread)
        val done = CoroutineScope(Dispatchers.Main.immediate).async { withTimeout(20_000) { block() } }
        while (!done.isCompleted) {
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
        }
        runBlocking { done.await() }
    }

    private object StartupImages : ImageStorage {
        override suspend fun cleanupTempFiles() = Unit
        override suspend fun saveImage(sourceRef: ImageRef, exerciseUuid: String): ImageSaveResult =
            error("No media in this test")
        override suspend fun createTempCaptureRef(): ImageRef = error("No media in this test")
        override suspend fun deleteImage(path: String): Boolean = error("No media in this test")
        override suspend fun lastModified(path: String): Long = error("No media in this test")
    }
}
