// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.wear.ongoing.OngoingStatus
import io.github.stslex.workeeper.wear.ui.CompletionUnavailableReason
import io.github.stslex.workeeper.wear.ui.ControllerAction
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
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

    private fun environment() = RuntimeTestEnvironment(transformClock = { DebugElapsedClock(it) })

    private fun driver(env: RuntimeTestEnvironment) = DebugSnapshotDriver(
        env.owner,
        env.ids,
        env.clock as DebugElapsedClock,
    )
}

private const val DRAFT_REPS = 12
