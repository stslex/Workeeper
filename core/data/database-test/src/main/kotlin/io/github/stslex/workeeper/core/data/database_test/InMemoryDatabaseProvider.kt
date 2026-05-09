// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database_test

import android.content.Context
import androidx.room.Room
import io.github.stslex.workeeper.core.data.database.AppDatabase

/**
 * androidTest-side counterpart to `RepositoryTestEnv`.
 *
 * Builds an in-memory `AppDatabase` for instrumentation tests that need a real Room
 * stack injected through Hilt. `RepositoryTestEnv` lives in
 * `core/data/database/src/testFixtures/` and serves Robolectric-based repository unit
 * tests; this provider serves on-device androidTest consumers via
 * [io.github.stslex.workeeper.core.data.database_test.di.TestDatabaseModule].
 */
object InMemoryDatabaseProvider {

    fun create(context: Context): AppDatabase = Room
        .inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        )
        .allowMainThreadQueries()
        .build()
}
