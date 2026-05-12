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
}
