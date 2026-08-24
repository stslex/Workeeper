// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.result

import io.github.stslex.workeeper.core.data.backup.api.error.BackupError

/** Result wrapper for backup operations. GUARD: impls return [Failure], they never throw. */
sealed interface BackupResult<out T> {

    data class Success<T>(val data: T) : BackupResult<T>

    data class Failure(val error: BackupError) : BackupResult<Nothing>
}
