// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.data.database_test.InMemoryDatabaseProvider
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.core.ui.test.fakes.FakeImageStorage
import io.github.stslex.workeeper.di.AppGraph
import io.github.stslex.workeeper.di.buildAppGraph
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dialog exactly-once semantics across a generation replacement (Phase 5 R2, spec §12): the
 * `pending_*` flags are DataStore-persisted process state, so a dialog published by generation N
 * is VISIBLE to generation N+1 (survival), and one acknowledgement through EITHER generation's
 * observer clears it for both (exactly-once — the flag, not the repository instance, is the
 * truth). Two full Metro graphs over one process DataStore, the
 * `AppScopeDataStoreSingletonTest` shape applied to the dialog subsystem.
 */
@Regression
@RunWith(AndroidJUnit4::class)
internal class DialogStateAcrossGenerationsTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val resources = mutableListOf<Pair<AppScopeLifetime, () -> Unit>>()

    private fun newGeneration(): AppGraph {
        val database = InMemoryDatabaseProvider.create(context)
        val lifetime = AppScopeLifetime()
        resources += lifetime to { database.close() }
        return buildAppGraph(
            applicationContext = context,
            appDatabase = database,
            imageStorage = FakeImageStorage(),
            appScopeLifetime = lifetime,
        )
    }

    @After
    fun tearDown() = runBlocking {
        // Leave no pending flag behind for later tests in the shared process.
        val cleanupGraph = newGeneration()
        cleanupGraph.appDialogRepository.currentDialog.first()?.let { dialog ->
            cleanupGraph.appDialogObserver.acknowledgeReaction(dialog)
        }
        resources.forEach { (lifetime, closeDb) ->
            runBlocking { lifetime.cancelAndJoin() }
            closeDb()
        }
        resources.clear()
    }

    @Test
    fun pendingDialogSurvivesReplacement_andIsConsumedExactlyOnce() = runBlocking {
        val generationOne = newGeneration()
        val published = AppDialog.RestoreSuccess(
            restoredAtEpochMs = 1_724_300_000_000,
            previousVersionAvailable = true,
        )
        generationOne.appDialogRepository.publish(published)
        assertEquals(published, generationOne.appDialogRepository.currentDialog.first())

        // The replacement: a fresh graph generation over the SAME process DataStore.
        val generationTwo = newGeneration()

        // Survival: the pending flag is state, not an instance property.
        val visibleInTwo = generationTwo.appDialogRepository.currentDialog.first()
        assertTrue(
            "the pending dialog must survive the generation replacement; got $visibleInTwo",
            visibleInTwo is AppDialog.RestoreSuccess,
        )

        // Exactly-once: one acknowledgement through the NEW generation clears the flag…
        generationTwo.appDialogObserver.acknowledgeReaction(visibleInTwo!!)
        assertNull(generationTwo.appDialogRepository.currentDialog.first())
        // …for the old generation's reader too — no second pending copy exists anywhere.
        assertNull(generationOne.appDialogRepository.currentDialog.first())
    }
}
