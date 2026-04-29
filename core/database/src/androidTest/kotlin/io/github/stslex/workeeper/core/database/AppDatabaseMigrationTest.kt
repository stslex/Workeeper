// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule

/**
 * Migration test fixture for [AppDatabase].
 *
 * When a schema bump is added (e.g. version 5 → 6), add a test method below that:
 *   1. Opens the source version via `helper.createDatabase(TEST_DB, fromVersion)`.
 *   2. Seeds rows representative of the migration's concern (NULL values, edge cases).
 *   3. Closes that DB, then opens it via `helper.runMigrationsAndValidate(...)` with
 *      the matching `Migration(from, to)` object.
 *   4. Queries the migrated DB and asserts data was preserved / transformed correctly.
 *
 * See androidx.room.testing docs for full pattern.
 */
internal class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
