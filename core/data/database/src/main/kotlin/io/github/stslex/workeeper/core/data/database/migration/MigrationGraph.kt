// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.migration

import androidx.room3.migration.Migration

/**
 * Public convenience wrapper over [hasMigrationPath] that reads the live [MIGRATIONS]
 * registry. Callers outside the database module (e.g. `BackupInteractor` pre-restore
 * checks) use this form so the registry stays internal.
 */
fun hasMigrationPath(from: Int, to: Int): Boolean =
    hasMigrationPath(MIGRATIONS, from, to)

/**
 * Pure BFS over the directed graph induced by [migrations] (every entry contributes
 * an edge `startVersion → endVersion`). Returns `true` iff [to] is reachable from
 * [from] following those edges.
 *
 * Used at pre-restore time to reject backups we cannot migrate (see
 * `documentation/feature-specs/backup-recovery.md` → "Pre-restore compatibility
 * checks") and by `MigrationsRegistryTest` to enforce the consecutive-pair
 * completeness invariant.
 *
 * Treats `from == to` as a trivial path (no migration required). Downgrades
 * (`from > to`) are unsupported and always return `false` — Room does not support
 * schema downgrades, and silently allowing them would mask a real bug.
 */
internal fun hasMigrationPath(
    migrations: Array<Migration>,
    from: Int,
    to: Int,
): Boolean {
    if (from == to) return true
    if (from > to) return false

    val edges: Map<Int, List<Int>> = migrations
        .groupBy { it.startVersion }
        .mapValues { (_, list) -> list.map { it.endVersion } }

    val visited = mutableSetOf<Int>()
    val queue = ArrayDeque<Int>().apply { add(from) }
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (current == to) return true
        if (!visited.add(current)) continue
        edges[current]?.forEach { neighbor ->
            if (neighbor <= to) queue.add(neighbor)
        }
    }
    return false
}
