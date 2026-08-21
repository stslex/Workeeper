// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import androidx.room3.RoomDatabaseConstructor

/**
 * Room's KMP constructor seam: `@ConstructedBy` on [AppDatabase] points here, and the Room
 * compiler generates the `actual` object into every platform compilation — which is why this
 * `expect` has no hand-written `actual` anywhere. No `NO_ACTUAL_FOR_EXPECT` suppression
 * either (NoActualForExpectSuppressionRule forbids it): the metadata compilation does not
 * require an actual, and if KSP ever stops generating them the platform compile goes red
 * instead of green-over-nothing. The generic `Room.databaseBuilder<AppDatabase>(…)` sites
 * resolve through it instead of JVM reflection, and an iOS builder (phase 7) gets a
 * constructor for free.
 */
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
