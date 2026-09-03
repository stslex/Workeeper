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
 * HS6 as a behavioural gate: the start-card mode must survive process death, so this runs
 * [CommonDataStoreImpl] over two DataStore generations sharing one file. See the Phase-6 spec.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = Application::class, sdk = [33])
internal class CommonDataStorePersistenceTest {

    /** Base constructor still memoizes under a throwaway name; only [dataStore] is read. */
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

    /** A legal, writable path for the base constructor's throwaway store; never opened. */
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
     * Process death: the generation's scope dies; only the file remains. [cancelAndJoin], never a
     * bare `cancel` — DataStore frees the file from `activeFiles` only on job completion.
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
        // Join before the delete: no writer may still be unwinding against the file.
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
