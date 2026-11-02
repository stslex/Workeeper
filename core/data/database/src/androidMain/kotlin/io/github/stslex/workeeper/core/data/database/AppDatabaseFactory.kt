// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Android's database entry point; `BaseApplication` passes the result to its generation's graph.
 * Migration registration and post-commit reporting are shared with the Apple factory.
 */
fun buildAppDatabase(context: Context): AppDatabase = buildAppDatabase(
    Room.databaseBuilder<AppDatabase>(
        context = context,
        name = AppDatabase.NAME,
    ).setDriver(BundledSQLiteDriver()),
)
