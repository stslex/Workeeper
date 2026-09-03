// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.core.ui.start_mode.startCardModeName
import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem
import io.github.stslex.workeeper.feature.home.mvi.model.StartCardBodyUi
import io.github.stslex.workeeper.feature.home.mvi.model.WeekDayUi
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * HS6 on the card: the head names no mode until the persisted one arrives, and a body under a
 * null mode does not draw — the only witness of the card's `if (mode == null) null else body`.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
@OptIn(ExperimentalTestApi::class)
internal class HomeStartCardModeLabelTest {

    @Test
    fun theHeadNamesNoModeUntilOneIsKnown() = runComposeUiTest {
        // Straight out of the production factory, not a hand-built null.
        val mode = mutableStateOf(
            State.init(
                pagingUiState = PagingUiState { flowOf(PagingData.empty<RecentSessionItem>()) },
            ).startCardMode,
        )
        val body = mutableStateOf<StartCardBodyUi?>(null)
        var expectedDaysSinceLastLabel = ""
        setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                val daysSinceLastLabel = startCardModeName(StartCardModeUi.DAYS_SINCE_LAST)
                SideEffect {
                    expectedDaysSinceLastLabel = daysSinceLastLabel.uppercase()
                }
                HomeStartCard(
                    mode = mode.value,
                    body = body.value,
                    onStartClick = {},
                    onOtherTrainingClick = {},
                    onModeClick = {},
                )
            }
        }
        waitForIdle()

        // CARD and HEAD are asserted BEFORE the label's absence: `assertDoesNotExist` is also
        // what a subtree that never composed would report.
        onNodeWithTag(CARD).assertExists()
        onNodeWithTag(HEAD, useUnmergedTree = true)
            .assertExists()
            // The head is a target, not merely a rectangle: `clickable` adds no measurement,
            // so the width assertion below would hold without it.
            .assertHasClickAction()
        onNodeWithTag(LABEL, useUnmergedTree = true).assertDoesNotExist()

        // A label-less head is a lone 16dp caret, so it takes a minimum width instead.
        val head = onNodeWithTag(HEAD, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val width = head.right - head.left
        assertTrue(width >= MIN_TOUCH_TARGET, "label-less head is $width wide, under $MIN_TOUCH_TARGET")

        // A readout under a still-unknown mode: unreachable in production, which is exactly
        // why the card's guard needs a witness of its own.
        runOnIdle { body.value = WEEK_BODY }
        waitForIdle()

        onNodeWithTag(LABEL, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText(WEEK_BODY.sessionsCountLabel).assertDoesNotExist()

        // Resolved, and deliberately not to WEEK: a resolved WEEK renders what a seed would.
        runOnIdle {
            mode.value = StartCardModeUi.DAYS_SINCE_LAST
            body.value = DAYS_SINCE_BODY
        }
        waitForIdle()

        // `AppLabel` uppercases, locale-invariantly, so the expectation does the same.
        onNodeWithTag(LABEL, useUnmergedTree = true).assertTextEquals(expectedDaysSinceLastLabel)
        // The readout draws now, which is what gives the absence above its meaning.
        onNodeWithText(DAYS_SINCE_BODY.daysCountLabel).assertExists()
    }

    private companion object {

        const val CARD = "HomeStartCard"
        const val HEAD = "HomeStartModeHead"
        const val LABEL = "HomeStartModeLabel"

        /** Android's minimum interactive target — Material asks 48, WCAG 2.5.5 asks 44. */
        val MIN_TOUCH_TARGET = 48.dp

        /** Held under a null mode, where it must not draw; never paired with a WEEK head. */
        val WEEK_BODY = StartCardBodyUi.Week(
            sessionsCountLabel = "3",
            sessionsUnitLabel = "тренировки",
            days = List(WEEK_DAYS) { WeekDayUi(label = "Пн", isFilled = false) }
                .toImmutableList(),
        )

        /** The resolved pair: DAYS_SINCE_LAST's head over its own readout. */
        val DAYS_SINCE_BODY = StartCardBodyUi.DaysSince(
            daysCountLabel = "4",
            daysUnitLabel = "дня",
            anchorLabel = "Ноги и плечи · 03/08/26",
        )

        const val WEEK_DAYS = 7
    }
}
