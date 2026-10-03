// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.wear

import androidx.room3.ColumnInfo
import kotlin.uuid.Uuid

/**
 * The phone's change key for the paired watch (wear-live-sync.md §6.1): the active session and its
 * Wear revision, nothing else.
 */
data class ActiveWearKeyRow(
    @ColumnInfo(name = "session_uuid")
    val sessionUuid: Uuid,
    @ColumnInfo(name = "wear_revision")
    val revision: Long,
)
