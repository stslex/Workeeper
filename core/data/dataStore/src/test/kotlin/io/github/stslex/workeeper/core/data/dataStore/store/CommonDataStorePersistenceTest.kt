// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.dataStore.store

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.data.dataStore.core.DataStoreProvider
import io.github.stslex.workeeper.core.data.dataStore.core.DataStoreProviderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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

    private class GenerationProvider(
        file: File,
        scope: CoroutineScope,
    ) : DataStoreProvider(
        name = "hs6_super_ignored",
        context = ApplicationProvider.getApplicationContext(),
    ) {
        override val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = scope) { file }
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

    /** Process death: the generation's scope dies; only the file remains. */
    private fun killGeneration() {
        scopes.removeLast().cancel()
    }

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        file = File(context.cacheDir, "hs6_${System.nanoTime()}.preferences_pb")
    }

    @AfterEach
    fun teardown() {
        scopes.forEach { it.cancel() }
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
