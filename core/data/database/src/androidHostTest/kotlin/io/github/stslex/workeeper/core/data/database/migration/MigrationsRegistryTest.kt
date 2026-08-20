// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.migration

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Registry-level integrity check for [MIGRATIONS]. Pairs with the per-migration
 * fixture in `core/data/database/src/androidTest/.../AppDatabaseMigrationTest.kt`,
 * which exercises each `Migration` end-to-end against `Y.json` / `Y+1.json` schema
 * snapshots via Room's `MigrationTestHelper`.
 *
 * The split: this class enforces the array-level invariant ("every consecutive
 * version pair from [MIN_SUPPORTED_SCHEMA_VERSION] forward has a registered
 * path"), so a schema bump that adds `Migration(N+1, N+2)` but forgets
 * `Migration(N, N+1)` fails this class before the deliberate absence of
 * `fallbackToDestructiveMigration*` in `buildAppDatabase` (`AppDatabaseFactory.kt`)
 * can hurt a user shipped on DB version N. `AppDatabaseMigrationTest` covers
 * SQL-level correctness per migration.
 *
 * Spec: `documentation/feature-specs/backup-recovery.md` →
 * "CI-enforced migration test".
 */
internal class MigrationsRegistryTest {

    @Test
    fun `every consecutive version pair from min supported to current is migratable`() {
        for (n in MIN_SUPPORTED_SCHEMA_VERSION until APP_DATABASE_VERSION) {
            assertTrue(
                hasMigrationPath(MIGRATIONS, from = n, to = n + 1),
                "Missing migration path from $n to ${n + 1}. " +
                    "Either add a Migration($n, ${n + 1}) and register it in " +
                    "MigrationsRegistry.kt, or raise MIN_SUPPORTED_SCHEMA_VERSION " +
                    "if that version range is intentionally unsupported. " +
                    "Spec: docs/feature-specs/backup-recovery.md#ci-enforced-migration-test",
            )
        }
    }

    @Test
    fun `every registered migration is reachable in the forward direction`() {
        // Each registered Migration(X, Y) must be self-consistent: a graph walk
        // from X to Y must succeed. This is a sanity check that the migration is
        // actually wired into the array (groupBy by startVersion sees it) and not
        // accidentally registered with endVersion < startVersion.
        MIGRATIONS.forEach { migration ->
            val start = migration.startVersion
            val end = migration.endVersion
            assertTrue(
                hasMigrationPath(MIGRATIONS, from = start, to = end),
                "Migration($start, $end) is registered but hasMigrationPath " +
                    "rejects it — check direction.",
            )
        }
    }
}
