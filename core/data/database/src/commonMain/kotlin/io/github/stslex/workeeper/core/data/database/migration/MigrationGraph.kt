// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.migration

import androidx.room3.migration.Migration

/** [hasMigrationPath] over the live [MIGRATIONS] registry, for callers outside this module. */
fun hasMigrationPath(from: Int, to: Int): Boolean =
    hasMigrationPath(MIGRATIONS, from, to)

/**
 * BFS over the edges `startVersion → endVersion`: is [to] reachable from [from]?
 * `from == to` is a trivial path; downgrades (`from > to`) always return `false`.
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
