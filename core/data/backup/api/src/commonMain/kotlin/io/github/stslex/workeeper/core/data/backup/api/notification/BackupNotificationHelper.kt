// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.notification

/**
 * Contract for the persistent "Auto-backup paused" notification shown on `AuthRevoked`.
 * Lives in the api module so the app graph can name it without depending on the worker module.
 */
interface BackupNotificationHelper {

    fun showAuthPaused()

    fun cancelAuthPaused()
}
