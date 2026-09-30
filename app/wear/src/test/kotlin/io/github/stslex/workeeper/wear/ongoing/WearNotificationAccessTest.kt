// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ongoing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WearNotificationAccessTest {

    @Test
    fun firstPermissionRequestAndRationaleAreUserActionsButPermanentDenialUsesSettings() {
        val first = notificationAccess(false, false, true, requestedBefore = false, shouldShowRationale = false)
        assertFalse(first.enabled)
        assertEquals(NotificationEnableAction.REQUEST_PERMISSION, first.action)
        val retry = notificationAccess(false, false, true, requestedBefore = true, shouldShowRationale = true)
        assertEquals(NotificationEnableAction.REQUEST_PERMISSION, retry.action)
        val denied = notificationAccess(false, false, true, requestedBefore = true, shouldShowRationale = false)
        assertEquals(NotificationEnableAction.OPEN_SETTINGS, denied.action)
    }

    @Test
    fun blockedApplicationOrChannelUsesSettingsEvenWhenRuntimePermissionIsGranted() {
        val appBlocked = notificationAccess(true, false, true, requestedBefore = false, shouldShowRationale = false)
        val channelBlocked = notificationAccess(true, true, false, requestedBefore = false, shouldShowRationale = false)
        assertFalse(appBlocked.enabled)
        assertFalse(channelBlocked.enabled)
        assertEquals(NotificationEnableAction.OPEN_SETTINGS, appBlocked.action)
        assertEquals(NotificationEnableAction.OPEN_SETTINGS, channelBlocked.action)
        val enabled = notificationAccess(true, true, true, requestedBefore = true, shouldShowRationale = false)
        assertTrue(enabled.enabled)
        assertEquals(NotificationEnableAction.NONE, enabled.action)
    }
}
