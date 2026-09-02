// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.testfixtures

import org.jetbrains.annotations.TestOnly

/** Shared persisted-state vectors for the phone Live rule and the Wear bridge target rule. */
@TestOnly
object WorkoutTargetParityFixture {

    data class Scenario(
        val name: String,
        val planSize: Int,
        val performedPositions: List<Int>,
        val skipped: Boolean,
        val expectedPhoneDone: Boolean,
        val expectedBridgeState: BridgeState,
        val expectedTargetPosition: Int? = null,
    )

    enum class BridgeState {
        ACTIVE_TARGET,
        PHONE_ACTION_REQUIRED,
        WORKOUT_COMPLETE,
    }

    val SCENARIOS: List<Scenario> = listOf(
        Scenario(
            name = "sparse position 4 only on a 5-set plan",
            planSize = 5,
            performedPositions = listOf(SET_POSITION_FOUR),
            skipped = false,
            expectedPhoneDone = false,
            expectedBridgeState = BridgeState.ACTIVE_TARGET,
            expectedTargetPosition = 0,
        ),
        Scenario(
            name = "sparse positions 1 and 3 on a 5-set plan",
            planSize = 5,
            performedPositions = listOf(1, SET_POSITION_THREE),
            skipped = false,
            expectedPhoneDone = false,
            expectedBridgeState = BridgeState.ACTIVE_TARGET,
            expectedTargetPosition = 0,
        ),
        Scenario(
            name = "all-done dense on a 5-set plan",
            planSize = 5,
            performedPositions = (0 until 5).toList(),
            skipped = false,
            expectedPhoneDone = true,
            expectedBridgeState = BridgeState.WORKOUT_COMPLETE,
        ),
        Scenario(
            name = "ad-hoc position 0 is the complete persisted union",
            planSize = 0,
            performedPositions = listOf(0),
            skipped = false,
            expectedPhoneDone = true,
            expectedBridgeState = BridgeState.WORKOUT_COMPLETE,
        ),
        Scenario(
            name = "ad-hoc sparse positions 2 and 4 are the complete persisted union",
            planSize = 0,
            performedPositions = listOf(2, SET_POSITION_FOUR),
            skipped = false,
            expectedPhoneDone = true,
            expectedBridgeState = BridgeState.WORKOUT_COMPLETE,
        ),
        Scenario(
            name = "empty no-plan no-row exercise requires phone action",
            planSize = 0,
            performedPositions = emptyList(),
            skipped = false,
            expectedPhoneDone = false,
            expectedBridgeState = BridgeState.PHONE_ACTION_REQUIRED,
        ),
        Scenario(
            name = "skipped planned exercise is excluded from the bridge traversal",
            planSize = 2,
            performedPositions = emptyList(),
            skipped = true,
            expectedPhoneDone = false,
            expectedBridgeState = BridgeState.WORKOUT_COMPLETE,
        ),
    )

    private const val SET_POSITION_THREE: Int = 3
    private const val SET_POSITION_FOUR: Int = 4
}
