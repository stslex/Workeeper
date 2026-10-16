// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.wear.ongoing.OngoingStatus
import io.github.stslex.workeeper.wear.ui.CompletionUnavailableReason
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
import io.github.stslex.workeeper.wear.ui.WearSurfaceModel
import io.github.stslex.workeeper.wear.ui.ongoingStatus
import io.github.stslex.workeeper.wear.ui.surface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DebugSnapshotDriverTest {
    @Test
    fun fixtureUsesCorrelatedAdmissionAndKeepsCanonicalCache() {
        val env = environment()
        val driver = driver(env)
        assertTrue(driver.handle(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY))
        assertTrue(env.owner.surface.value.completeEnabled)
        assertEquals(WearProtocol.MAX_WEAR_REPS, env.owner.surface.value.reps)
        assertNotNull(env.storage.bytes)
        assertTrue(env.owner.ongoingStatus.value is OngoingStatus.Scheduled)
    }

    @Test
    fun completeSetHasNoFakeSuccessAndTerminalIsAnExplicitScenario() {
        val env = environment()
        val driver = driver(env)
        driver.handle(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)
        env.owner.onAction(ControllerAction.SetReps(DRAFT_REPS))
        assertTrue(env.owner.onAction(ControllerAction.CompleteSet) is WatchActionResult.CommandIssued)
        assertEquals(CompletionUnavailableReason.COMMAND_IN_FLIGHT, env.owner.surface.value.completionUnavailableReason)
        assertTrue(driver.handle("terminal"))
        assertEquals(WearSurfaceKind.WORKOUT_COMPLETE, env.owner.surface.value.kind)
        assertFalse(env.owner.surface.value.hasUnsubmittedDraft)
        assertEquals(OngoingStatus.Inactive, env.owner.ongoingStatus.value)
        assertFalse(driver.handle("refresh"))
    }

    @Test
    fun commandFixtureRemainsInFlightUntilExplicitTerminal() {
        val env = environment()
        val driver = driver(env)
        assertTrue(driver.handle(SyntheticSurfaceFixtures.COMMAND_IN_FLIGHT))
        assertEquals(WearSurfaceKind.ACTIVE, env.owner.surface.value.kind)
        assertEquals(CompletionUnavailableReason.COMMAND_IN_FLIGHT, env.owner.surface.value.completionUnavailableReason)
        assertFalse(env.owner.surface.value.completeEnabled)
    }

    @Test
    fun explicitExpiryAndRefreshUseOneInjectedClock() {
        val env = environment()
        val driver = driver(env)
        driver.handle(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)
        assertTrue(driver.handle("expire"))
        assertFalse(env.owner.surface.value.completeEnabled)
        assertEquals(WearSurfaceKind.REFRESH_REQUIRED, env.owner.surface.value.kind)
        assertTrue(driver.handle("refresh"))
        assertTrue(env.owner.surface.value.completeEnabled)
        assertTrue(driver.handle("stop_ongoing"))
        assertEquals(OngoingStatus.Inactive, env.owner.ongoingStatus.value)
    }

    @Test
    fun outOfProtocolRangeFixtureIsNotForgedIntoCanonicalSnapshot() {
        val env = environment()
        val driver = driver(env)
        assertFalse(driver.handle(SyntheticSurfaceFixtures.WEIGHT_ERROR))
        assertNull(env.storage.bytes)
        assertEquals(WearSurfaceKind.LOADING, env.owner.surface.value.kind)
    }

    @Test
    fun readOnlyProtocolFixturesUseTheSameOwner() {
        val env = environment()
        val driver = driver(env)
        assertTrue(driver.handle(SyntheticSurfaceFixtures.NO_SETS))
        assertEquals(WearSurfaceKind.PHONE_ACTION_NO_SETS, env.owner.surface.value.kind)
        assertTrue(driver.handle(SyntheticSurfaceFixtures.COMPLETE))
        assertEquals(WearSurfaceKind.WORKOUT_COMPLETE, env.owner.surface.value.kind)
        assertTrue(driver.handle("no_session"))
        assertEquals(WearSurfaceKind.NO_SESSION, env.owner.surface.value.kind)
        assertFalse(env.owner.surface.value.completeEnabled)
    }

    /**
     * wear-phase-2-exercise-list.md §12.1 step 2: every fixture the driver publishes maps to the
     * surface the fixture describes, so the harness's fixture identities render as they did on v1.
     */
    @Test
    fun everyRuntimeFixtureMapsToTheSurfaceItDescribes() {
        assertEquals(RUNTIME_FIXTURE_COUNT, RUNTIME_FIXTURES.size)
        RUNTIME_FIXTURES.forEach { id ->
            val env = environment()
            assertTrue(driver(env).handle(id), id)
            val fixture = requireNotNull(SyntheticSurfaceFixtures.find(id))
            val surface = env.owner.surface.value.copy(selectedLocale = fixture.selectedLocale)
            assertEquals(fixture.pinned(), surface.pinned(), id)
        }
    }

    /** The harness shows fixtures one after another on one runtime: none keeps the last one's set. */
    @Test
    fun fixturesShownInSequenceEachMapToTheirOwnSurface() {
        val env = environment()
        val driver = driver(env)
        (RUNTIME_FIXTURES + RUNTIME_FIXTURES.reversed()).forEach { id ->
            assertTrue(driver.handle(id), id)
            val fixture = requireNotNull(SyntheticSurfaceFixtures.find(id))
            val surface = env.owner.surface.value.copy(selectedLocale = fixture.selectedLocale)
            assertEquals(fixture.pinned(), surface.pinned(), id)
        }
    }

    @Test
    fun fixturesWithoutARuntimeSourceAreRefused() {
        listOf(
            SyntheticSurfaceFixtures.WEIGHT_ERROR,
            SyntheticSurfaceFixtures.RETRYABLE,
            SyntheticSurfaceFixtures.PROTOCOL_MISMATCH,
            SyntheticSurfaceFixtures.LOADING,
        ).forEach { id ->
            val env = environment()
            assertFalse(driver(env).handle(id), id)
            assertEquals(WearSurfaceKind.LOADING, env.owner.surface.value.kind, id)
        }
    }

    /** The values a snapshot decides; `fieldError` of an active surface comes from a reducer event. */
    private fun WearSurfaceModel.pinned() = listOf(
        kind,
        trainingName,
        exerciseName,
        completedExercises,
        totalExercises,
        setOrdinal,
        totalSets,
        reps,
        weightHundredthsKg,
        weighted,
        controlsVisible,
        completeEnabled,
        completionUnavailableReason,
        fieldError.takeUnless { controlsVisible },
    )

    private fun environment() = RuntimeTestEnvironment(transformClock = { DebugElapsedClock(it) })

    private fun driver(env: RuntimeTestEnvironment) = DebugSnapshotDriver(
        env.owner,
        env.ids,
        env.clock as DebugElapsedClock,
    )
}

private const val DRAFT_REPS = 12
private const val RUNTIME_FIXTURE_COUNT = 14

private val RUNTIME_FIXTURES = listOf(
    SyntheticSurfaceFixtures.ACTIVE_BOUNDARY,
    SyntheticSurfaceFixtures.WEIGHTLESS,
    SyntheticSurfaceFixtures.FIELD_ERROR,
    SyntheticSurfaceFixtures.COMMAND_IN_FLIGHT,
    SyntheticSurfaceFixtures.UNSET_WEIGHT,
    SyntheticSurfaceFixtures.ANONYMOUS_EXERCISE,
    SyntheticSurfaceFixtures.ANONYMOUS_COMPLETE,
    SyntheticSurfaceFixtures.REFRESH_REQUIRED,
    SyntheticSurfaceFixtures.DISCONNECTED,
    SyntheticSurfaceFixtures.NO_SETS,
    SyntheticSurfaceFixtures.UNSUPPORTED,
    SyntheticSurfaceFixtures.PAYLOAD_TOO_LARGE,
    SyntheticSurfaceFixtures.COMPLETE,
    SyntheticSurfaceFixtures.NO_SESSION,
)
