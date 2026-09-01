// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.wear

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** One phone-owned database identity. It is deliberately outside backup-export DTOs. */
@Entity(tableName = "wear_database_metadata")
data class WearDatabaseMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = SINGLETON_ID,
    @ColumnInfo(name = "database_epoch")
    val databaseEpoch: String,
) {
    companion object {
        const val SINGLETON_ID: Int = 0
    }
}
