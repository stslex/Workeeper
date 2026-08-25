// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import androidx.room3.RoomDatabaseConstructor

/**
 * Room's KMP constructor seam; the Room compiler generates the `actual` per platform, which
 * is why none is hand-written here. GUARD: never add a `NO_ACTUAL_FOR_EXPECT` suppression.
 */
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
