package io.github.stslex.workeeper.core.data.backup.api.result

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError

/**
 * Result wrapper for fallible backup operations. Mirrors the data-layer convention
 * established by `ImageSaveResult` (sealed Success/Failure with a typed error) so
 * callers can exhaustively match without losing error context.
 *
 * Impls must never throw for the failure cases enumerated in [BackupError] — they
 * are converted into [Failure] instead. Uncaught exceptions in impls are a bug.
 */
sealed interface BackupResult<out T> {

    data class Success<T>(val data: T) : BackupResult<T>

    data class Failure(val error: BackupError) : BackupResult<Nothing>
}
