// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.dataStore.store

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.data.dataStore.core.DataStorePathResolver
import io.github.stslex.workeeper.core.data.dataStore.core.DataStoreProvider
import io.github.stslex.workeeper.core.data.dataStore.core.DataStoreProviderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File

/**
 * HS6 as a behavioural gate: the start-card mode must survive process death, so this test
 * runs [CommonDataStoreImpl] across two "process generations" — two DataStore instances
 * over the SAME preferences file, the first one's scope cancelled before the second opens.
 * Only the bytes on disk connect them; a value that comes back was persisted, not cached.
 *
 * The production [DataStoreProvider] cannot express this — its companion memoizes one
 * process-lifetime store per name — which is exactly why the class carries its `open` seam.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = Application::class, sdk = [33])
internal class CommonDataStorePersistenceTest {

    /**
     * The base constructor still runs and still memoizes a store under its own name, which is why
     * that name is a throwaway: only the overridden [dataStore] is ever read. The base store is
     * never opened, so it costs nothing — DataStore opens its file lazily on first access.
     */
    private class GenerationProvider(
        file: File,
        scope: CoroutineScope,
    ) : DataStoreProvider(
        name = "hs6_super_ignored",
        pathResolver = ThrowawayPathResolver,
    ) {
        override val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    /**
     * Resolves under the Robolectric app's cacheDir so the base constructor's throwaway store names
     * a writable location. It is never opened; this only has to be a legal path.
     */
    private object ThrowawayPathResolver : DataStorePathResolver {

        override fun resolve(name: String): Path {
            val cacheDir = ApplicationProvider.getApplicationContext<Application>().cacheDir
            return File(cacheDir, "$name.preferences_pb").toOkioPath()
        }
    }

    private class GenerationFactory(
        private val file: File,
        private val scope: CoroutineScope,
    ) : DataStoreProviderFactory {
        override fun create(name: String): DataStoreProvider = GenerationProvider(file, scope)
    }

    private lateinit var file: File
    private val scopes = mutableListOf<CoroutineScope>()

    private fun newGeneration(): CommonDataStoreImpl {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += scope
        return CommonDataStoreImpl(GenerationFactory(file, scope))
    }

    /**
     * Process death: the generation's scope dies; only the file remains.
     *
     * [cancelAndJoin], never a bare `cancel`: DataStore drops the file from its process-global
     * `activeFiles` set inside an `invokeOnCompletion` handler on this scope's `Job`
     * (`SimpleActor.init` -> `DataStoreImpl.onComplete` -> `StorageConnection.close`), which runs when
     * the job *completes*, not when `cancel` returns. Its `Dispatchers.IO` children keep the job in
     * Cancelling for as long as they take to unwind, so opening the next generation without joining
     * races that removal and trips `check(!activeFiles.contains(path))` — "There are multiple
     * DataStores active for the same file" (FileStorage.kt:52). That race is what made this test flake
     * red on a loaded CI runner while staying green locally.
     */
    private suspend fun killGeneration() {
        scopes.removeLast().coroutineContext.job.cancelAndJoin()
    }

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        file = File(context.cacheDir, "hs6_${System.nanoTime()}.preferences_pb")
    }

    @AfterEach
    fun teardown() {
        // Joined for the same reason as [killGeneration]: the file must be released, and no writer may
        // still be unwinding against it, before the delete below.
        runBlocking { scopes.forEach { it.coroutineContext.job.cancelAndJoin() } }
        scopes.clear()
        file.delete()
    }

    @Test
    fun `the start card mode survives process death`() = runTest {
        val firstLife = newGeneration()
        firstLife.setHomeStartCardMode("FORGOTTEN_TRAINING")
        killGeneration()

        val secondLife = newGeneration()

        assertEquals("FORGOTTEN_TRAINING", secondLife.homeStartCardMode.first())
    }

    @Test
    fun `an absent key reads as the WEEK default on first launch`() = runTest {
        val firstLife = newGeneration()

        assertEquals("WEEK", firstLife.homeStartCardMode.first())
    }

    @Test
    fun `the latest write wins across generations`() = runTest {
        val firstLife = newGeneration()
        firstLife.setHomeStartCardMode("LAGGING_GROUPS")
        firstLife.setHomeStartCardMode("DAYS_SINCE_LAST")
        killGeneration()

        val secondLife = newGeneration()

        assertEquals("DAYS_SINCE_LAST", secondLife.homeStartCardMode.first())
    }
}
