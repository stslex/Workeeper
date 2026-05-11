package io.github.stslex.workeeper.core.data.backup.google_drive.error

/**
 * Internal exception types thrown by `DriveAuthInterceptor` / `DriveApiImpl` to
 * carry HTTP-level failure context up to `DriveErrorMapper`. Not exposed outside
 * the impl module — the public surface uses
 * [io.github.stslex.workeeper.core.data.backup.api.error.BackupError].
 */
internal sealed class DriveException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {

    /** Drive returned 401 — token revoked or never accepted. */
    class AuthRevoked(message: String, cause: Throwable? = null) : DriveException(message, cause)

    /** Drive returned 403 with a `userRateLimitExceeded` / `quotaExceeded` reason. */
    class QuotaExceeded(message: String, cause: Throwable? = null) : DriveException(message, cause)

    /** Drive returned 403 with any other reason — treated as scope/permission loss. */
    class Forbidden(message: String, cause: Throwable? = null) : DriveException(message, cause)

    /** Caller has no signed-in account — `AuthTokenProvider.currentToken()` was null. */
    class NotAuthenticated(message: String = "no signed-in account") : DriveException(message)
}
