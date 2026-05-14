package io.github.stslex.workeeper.core.data.backup.google_drive.auth

import com.google.android.gms.common.api.Scope

/**
 * Scopes requested by every `AuthorizationRequest` / `RevokeAccessRequest` in this
 * module. Keeping them in one place is load-bearing: `setRequestedScopes` on
 * sign-in, silent refresh, and revoke MUST match exactly — a mismatch breaks
 * silent token reuse (GMS treats the request as new and re-prompts) and leaves
 * `userinfo`-derived identity behind on revoke.
 *
 * `drive.appdata` is the data scope used for the backup file; the two `userinfo`
 * scopes are needed to call `oauth2/v3/userinfo` for email + display name after a
 * successful authorize — `AuthorizationResult` itself does not surface them.
 */
internal object DriveAuthScopes {

    const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    const val USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email"
    const val USERINFO_PROFILE = "https://www.googleapis.com/auth/userinfo.profile"

    val ALL: List<Scope> = listOf(
        Scope(DRIVE_APPDATA),
        Scope(USERINFO_EMAIL),
        Scope(USERINFO_PROFILE),
    )

    /**
     * Scopes the backup feature cannot operate without. Userinfo scopes are NOT
     * here — declining them only degrades account display (handled in
     * `DriveBackupAuth.toAccount` via the placeholder email fallback), but the
     * feature still works. Only `drive.appdata` blocks all storage operations,
     * so partial-grant detection in `DriveBackupAuth` checks for its absence.
     *
     * Kept as an explicit constant rather than derived (e.g. `ALL.first()`) so
     * the required set is obvious at the call site and future scopes can be
     * promoted to required without touching ordering.
     */
    val REQUIRED: List<String> = listOf(DRIVE_APPDATA)
}
