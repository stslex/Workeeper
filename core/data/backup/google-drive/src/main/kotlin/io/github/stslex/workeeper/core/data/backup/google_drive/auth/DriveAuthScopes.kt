// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import com.google.android.gms.common.api.Scope

/**
 * OAuth scopes for the backup feature. Two request sets exist because `authorize()` raises a
 * resolution whenever a requested scope is ungranted. See documentation/feature-specs/backup.md.
 */
internal object DriveAuthScopes {

    const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    const val USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email"
    const val USERINFO_PROFILE = "https://www.googleapis.com/auth/userinfo.profile"

    /**
     * Visible-Drive scope for the AI snapshot. OPTIONAL: requested only via
     * [ALL_WITH_DRIVE_FILE], never on regular sign-in.
     */
    const val DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"

    val ALL: List<Scope> = listOf(
        Scope(DRIVE_APPDATA),
        Scope(USERINFO_EMAIL),
        Scope(USERINFO_PROFILE),
    )

    /** [ALL] plus [DRIVE_FILE]. */
    val ALL_WITH_DRIVE_FILE: List<Scope> = ALL + Scope(DRIVE_FILE)

    /**
     * Scopes the backup feature cannot operate without. Only `drive.appdata` blocks all storage
     * operations, so partial-grant detection checks for its absence.
     */
    val REQUIRED: List<String> = listOf(DRIVE_APPDATA)
}
