// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import android.content.Context
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
import androidx.test.core.app.ApplicationProvider
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
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
import io.github.stslex.workeeper.core.ui.start_mode.R as StartModeR

/**
 * HS6 on the card: the head names no mode until the persisted one arrives.
 *
 * `State.init` used to seed `StartCardModeUi.WEEK`, so every cold start drew «НЕДЕЛЯ» in the
 * screen's most prominent element before anything had been read — and the label is
 * indistinguishable from a real reading, which is what makes a seed worse than an empty head.
 * Reinstate the seed and this suite reds where it says nothing is named: `State.init` hands
 * back WEEK, the head draws «НЕДЕЛЯ», and `assertDoesNotExist` on `HomeStartModeLabel` fails.
 *
 * ## Subject
 *
 * The mode comes from `State.init` and goes into `HomeStartCard`, which is the whole path the
 * seed travelled. `HomeScreen` is deliberately NOT composed: it brings a `LazyColumn`, a
 * paging collector and `rememberDeferredSurface`'s 140/260ms hold, none of which this claim
 * needs and all of which are ways for a Compose test to settle late or not at all. The screen
 * hands the card `state.startCardMode` unaltered — one call site, no expression — and
 * `HomeStartCardSeedTest` pins the other end of the wire without a composition at all.
 *
 * ## Shape
 *
 * Robolectric plus `runComposeUiTest`, so it runs under `testDebugUnitTest` and gates every
 * PR (`ui_tests.yml` is `workflow_dispatch`-only; an assertion there is not a gate). **One
 * `@Test`, one composition**, per `core:ui:kit`'s `AccessibilitySemanticsTest`: a second
 * Compose environment in the same Robolectric sandbox hangs rather than fails. Both halves of
 * the claim therefore share one composition, separated by a state change — which is also the
 * truer subject, since that change is precisely what DataStore's first emission does.
 *
 * `useUnmergedTree`: the head is `clickable`, which merges its descendants, so the label's tag
 * is not reachable in the merged tree.
 *
 * ## Three phases, and why the middle one exists
 *
 * 1. Nothing known — no label; the head still exists, is still clickable and is still 48dp
 *    wide, because withholding the claim must not cost the control.
 * 2. A readout arrives while the mode is still unknown. `CommonHandler` writes mode and body
 *    in one `copy`, so production cannot reach this state — which is precisely why the card's
 *    `if (mode == null) null else body` guard has no other witness: delete it and every suite
 *    in the repository stays green.
 * 3. Both resolved. The label is that mode's name, **and the readout draws** — which is what
 *    gives phase 2's absence its meaning: same card, same harness, a body that provably
 *    renders, so "not there" was the null mode suppressing it and not a dead fixture.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
@OptIn(ExperimentalTestApi::class)
internal class HomeStartCardModeLabelTest {

    @Test
    fun theHeadNamesNoModeUntilOneIsKnown() = runComposeUiTest {
        // Straight out of the production factory — not a hand-built null. If the seed comes
        // back, it comes back through here.
        val mode = mutableStateOf(
            State.init(
                pagingUiState = PagingUiState { flowOf(PagingData.empty<RecentSessionItem>()) },
            ).startCardMode,
        )
        val body = mutableStateOf<StartCardBodyUi?>(null)
        setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
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

        // The card is there and the head is still a target — the sheet must open, and with
        // nothing known it checks nothing. What is missing is only the claim.
        //
        // CARD and HEAD are asserted BEFORE the label's absence on purpose: an
        // `assertDoesNotExist` is also what a subtree that never composed would report, so
        // without these two the first half of this suite would be green on an empty screen.
        onNodeWithTag(CARD).assertExists()
        onNodeWithTag(HEAD, useUnmergedTree = true)
            .assertExists()
            // The head is a target, not merely a rectangle. Both KDocs justify keeping the
            // caret and the minimum width on the promise that the sheet still opens; without
            // this, gating `.clickable(...)` on `label != null` would leave every other
            // assertion here green — `clickable` adds no measurement, so even the width holds.
            .assertHasClickAction()
        onNodeWithTag(LABEL, useUnmergedTree = true).assertDoesNotExist()

        // A label-less head is a lone 16dp caret, so the head takes a minimum width while it
        // has none. Without it this commit would trade a false claim for an unpressable
        // control, which is the same defect wearing the other hat.
        val head = onNodeWithTag(HEAD, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val width = head.right - head.left
        assertTrue(width >= MIN_TOUCH_TARGET, "label-less head is $width wide, under $MIN_TOUCH_TARGET")

        // A readout arrives while the mode is still unknown. `CommonHandler` writes the pair
        // in one `copy` so this cannot happen in production — which is exactly why the card's
        // guard needs a witness of its own: delete `if (mode == null) null else body` and
        // nothing else in the repository notices.
        runOnIdle { body.value = WEEK_BODY }
        waitForIdle()

        onNodeWithTag(LABEL, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText(WEEK_BODY.sessionsCountLabel).assertDoesNotExist()

        // Resolved, and to a mode that is NOT the old seed: a resolved WEEK renders exactly
        // what the seed rendered, so it could not tell the two apart.
        runOnIdle {
            mode.value = StartCardModeUi.DAYS_SINCE_LAST
            body.value = DAYS_SINCE_BODY
        }
        waitForIdle()

        // `AppLabel` uppercases, locale-invariantly, so the expectation does the same.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = context
            .getString(StartModeR.string.core_ui_start_mode_name_days_since_last)
            .uppercase()
        onNodeWithTag(LABEL, useUnmergedTree = true).assertTextEquals(expected)
        // And the readout is drawn now. This is what makes the absence above mean something:
        // the same card, the same harness, a body that provably renders — so the earlier
        // "not there" was the null mode suppressing it, not a fixture that never draws.
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
