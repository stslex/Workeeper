// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SearchOff
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/** Zero, one and two actions. [twoActions] has no production caller; only this golden guards it. */
internal class EmptyStateGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun noActions(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            AppEmptyState(
                headline = "Nothing archived",
                supportingText = "Archived exercises appear here for restore or permanent delete.",
                icon = Icons.Default.Inventory2,
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun oneAction(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            AppEmptyState(
                headline = "Ready when you are",
                supportingText = "Start a training to log your first session.",
                icon = Icons.Default.FitnessCenter,
                actionLabel = "Start a workout",
                onAction = {},
            )
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun twoActions(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            AppEmptyState(
                headline = "No exercises yet",
                supportingText = "Create one now, or start from the built-in library.",
                icon = Icons.Default.SearchOff,
                actionLabel = "Create exercise",
                onAction = {},
                secondaryActionLabel = "Browse the library",
                onSecondaryAction = {},
            )
        }
    }
}
