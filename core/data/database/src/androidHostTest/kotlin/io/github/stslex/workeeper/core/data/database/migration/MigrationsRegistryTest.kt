// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.migration

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Array-level integrity check for [MIGRATIONS]: every consecutive version pair from
 * [MIN_SUPPORTED_SCHEMA_VERSION] forward has a registered path. See backup-recovery.md.
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
        // A registered Migration(X, Y) must be self-consistent: X must reach Y.
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
