// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database_test

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.stslex.workeeper.core.data.database.AppDatabase

/**
 * androidTest-side counterpart to `RepositoryTestEnv`.
 *
 * Builds an in-memory `AppDatabase` for instrumentation tests that need a real Room
 * stack. `RepositoryTestEnv` is its sibling in this module's `src/main` and serves
 * Robolectric-based repository unit tests; this provider serves on-device androidTest
 * consumers. App-Scope Collapse Step 6 (Phase 3.2): the Metro test harness passes the
 * instance this returns as the `appDatabase` `create()` bound-instance root via
 * `buildAppGraph(...)` (previously the deleted Hilt `TestDatabaseModule`).
 */
object InMemoryDatabaseProvider {

    fun create(context: Context): AppDatabase = Room
        .inMemoryDatabaseBuilder<AppDatabase>(
            context,
        )
        .setDriver(BundledSQLiteDriver())
        .allowMainThreadQueries()
        .build()
}
