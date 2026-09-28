// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.wear.R
import io.github.stslex.workeeper.wear.ongoing.OngoingStatus
import io.github.stslex.workeeper.wear.runtime.ControllerAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "w240dp-h240dp-round")
internal class WearOngoingNoticeTest : WearOngoingNoticeContract()

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "ru-w240dp-h240dp-round")
internal class WearOngoingNoticeRuTest : WearOngoingNoticeContract()

@OptIn(ExperimentalTestApi::class)
internal abstract class WearOngoingNoticeContract {

    @Test
    fun deniedNotificationKeepsControllerUsableAndRequiresExplicitEnableAction() = runComposeUiTest {
        val active = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)).copy(
            reps = 8,
            weightHundredthsKg = 10_000,
        )
        val resources = RuntimeEnvironment.getApplication().resources
        val actions = mutableListOf<ControllerAction>()
        var enableRequests = 0
        var notice by mutableStateOf<WearOngoingNotice?>(null)
        lateinit var back: OnBackPressedDispatcher
        setContent {
            val owner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
            SideEffect { back = owner.onBackPressedDispatcher }
            WearGateHost(WearScreen.SMALL_ROUND, LARGEST_WEAR_FONT_SCALE) {
                WearControllerScreen(
                    state = active,
                    ongoingNotice = notice,
                    onEnableNotifications = { enableRequests++ },
                    onAction = { actions += it },
                )
            }
        }
        onNodeWithTag("ongoing_notice").assertDoesNotExist()
        notice = ongoingNotice(active, OngoingStatus.PermissionDenied, notificationsEnabled = false)
        waitForIdle()
        onNodeWithTag("complete_set").assertIsEnabled()
        onNodeWithTag("ongoing_notice_text").performScrollTo()
            .assertTextEquals(resources.getString(R.string.ongoing_notifications_disabled))
        assertEquals(0, enableRequests, "Rendering permission denial must not request access")
        onNodeWithTag("enable_notifications").performScrollTo().performClick()
        assertEquals(1, enableRequests)
        assertTrue(actions.isEmpty(), "Notification settings must not dispatch a workout action")
        onNodeWithTag("reps_card").performScrollTo().performClick()
        onNodeWithTag("editor_increase").performClick()
        assertTrue(actions.single() is ControllerAction.AdjustDraft, "Ordinary editing must remain usable")
        runOnIdle { back.onBackPressed() }
        waitForIdle()
        actions.clear()

        notice = ongoingNotice(active, OngoingStatus.PermissionDenied, notificationsEnabled = true)
        waitForIdle()
        onNodeWithTag("ongoing_notice_text").performScrollTo()
            .assertTextEquals(resources.getString(R.string.ongoing_shortcut_inactive))
        onNodeWithTag("enable_notifications").assertDoesNotExist()
        assertEquals(1, enableRequests, "Permission restoration must not ask again")
        assertTrue(actions.isEmpty(), "Permission restoration must not dispatch a refresh or completion")

        assertNull(ongoingNotice(active, OngoingStatus.Scheduled(10_000L), notificationsEnabled = true))
        assertNull(ongoingNotice(WearSurfaceModel(WearSurfaceKind.NO_SESSION), OngoingStatus.Inactive, false))
        notice = null
        waitForIdle()
        onNodeWithTag("ongoing_notice").assertDoesNotExist()
    }
}
