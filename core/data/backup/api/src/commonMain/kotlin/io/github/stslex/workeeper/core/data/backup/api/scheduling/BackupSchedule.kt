// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.scheduling

/**
 * Cadence of the auto-backup periodic work; [ManualOnly] disables it entirely. Persisted via
 * [Enum.name] — renaming or reordering variants requires a migration.
 */
enum class BackupSchedule {
    Daily,
    Weekly,
    ManualOnly,
}
