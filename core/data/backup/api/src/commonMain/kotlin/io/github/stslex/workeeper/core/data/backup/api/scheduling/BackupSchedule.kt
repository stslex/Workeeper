// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.scheduling

/**
 * Cadence at which the auto-backup periodic work runs. [ManualOnly] disables the
 * periodic work entirely — backups happen only when the user taps "Backup now".
 *
 * Impl detail: persisted to DataStore via [Enum.name], so the order of declaration
 * and the spelling of each variant are part of the persistence contract — renaming
 * or reordering requires a migration.
 */
enum class BackupSchedule {
    Daily,
    Weekly,
    ManualOnly,
}
