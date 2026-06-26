// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import com.google.android.gms.common.api.Scope

/**
 * OAuth scopes for the backup feature. Two request sets exist deliberately:
 *
 * - [ALL] — the base set (`drive.appdata` + the two `userinfo` scopes). Used by regular
 *   sign-in, by silent refresh for accounts that have NOT granted `drive.file`, and as the
 *   binary backup's only requirement. This set is unchanged from v1.
 * - [ALL_WITH_DRIVE_FILE] — [ALL] plus the visible-Drive `drive.file` scope. Used ONLY by the
 *   explicit AI-export grant flow, by silent refresh for accounts that already granted
 *   `drive.file`, and by revoke (revoke everything).
 *
 * Why two sets (not one static set): `AuthorizationClient.authorize()` raises a resolution
 * (no silent token) whenever a *requested* scope is ungranted. Requesting `drive.file` on the
 * silent/binary path for an appdata-only user would therefore break silent token refresh. By
 * requesting only the already-granted set on silent refresh, the appdata-only path requests
 * exactly [ALL] — identical to v1 — so it can never trip a resolution.
 *
 * `drive.appdata` is the data scope for the binary backup; the two `userinfo` scopes back the
 * `oauth2/v3/userinfo` identity lookup (`AuthorizationResult` does not surface them).
 */
internal object DriveAuthScopes {

    const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    const val USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email"
    const val USERINFO_PROFILE = "https://www.googleapis.com/auth/userinfo.profile"

    /**
     * Visible-Drive scope for the AI snapshot. OPTIONAL: requested only via
     * [ALL_WITH_DRIVE_FILE] (explicit grant / already-granted silent refresh / revoke), never
     * on regular sign-in — so appdata-only users never see it and their silent refresh stays
     * resolution-free.
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
     * Scopes the backup feature cannot operate without. Userinfo + `drive.file` are NOT here —
     * declining userinfo only degrades account display, and `drive.file` only gates the
     * optional AI snapshot. Only `drive.appdata` blocks all storage operations, so
     * partial-grant detection in `DriveBackupAuth` checks for its absence.
     */
    val REQUIRED: List<String> = listOf(DRIVE_APPDATA)
}
