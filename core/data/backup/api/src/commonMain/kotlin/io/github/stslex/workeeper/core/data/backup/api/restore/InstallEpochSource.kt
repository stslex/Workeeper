// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.restore

/**
 * The installation's stable epoch token: the one member of the Android `RestoreRecoveryFiles`
 * that common code reads. Android binds it to that same singleton; an iOS composition root binds
 * its own. See documentation/feature-specs/kmp-phase-7-17-app-dialogs-feature.md (D4).
 */
interface InstallEpochSource {

    /** Atomically creates or reads the stable installation token. */
    suspend fun installEpoch(): InstallEpoch
}
