// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database_test

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.stslex.workeeper.core.data.database.AppDatabase

/**
 * androidTest-side counterpart to `RepositoryTestEnv`: an in-memory `AppDatabase` passed to
 * `buildAppGraph(...)` as the `appDatabase` bound instance.
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
