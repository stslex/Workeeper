// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.wear

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlin.uuid.Uuid

@Dao
interface WearSyncDao {

    @Query("SELECT * FROM wear_database_metadata WHERE singleton_id = 0")
    suspend fun getDatabaseMetadata(): WearDatabaseMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDatabaseMetadata(metadata: WearDatabaseMetadataEntity): Long

    @Query("UPDATE wear_database_metadata SET database_epoch = :epoch WHERE singleton_id = 0")
    suspend fun updateDatabaseEpoch(epoch: String): Int

    @Query(
        """
        SELECT uuid AS session_uuid,
               wear_revision,
               wear_lease_generation,
               wear_receipt_command_id,
               wear_receipt_attempt_fingerprint,
               wear_receipt_database_epoch,
               wear_receipt_revision
        FROM session_table
        WHERE uuid = :sessionUuid
        """,
    )
    suspend fun getSessionSync(sessionUuid: Uuid): SessionWearSyncRow?

    @Query(
        """
        SELECT s.uuid AS session_uuid,
               s.wear_revision,
               s.wear_lease_generation,
               s.wear_receipt_command_id,
               s.wear_receipt_attempt_fingerprint,
               s.wear_receipt_database_epoch,
               s.wear_receipt_revision
        FROM session_table s
        WHERE s.state = 'IN_PROGRESS'
        LIMIT 1
        """,
    )
    suspend fun getActiveSessionSync(): SessionWearSyncRow?

    @Query(
        """
        UPDATE session_table
        SET wear_lease_generation = wear_lease_generation + 1
        WHERE uuid = :sessionUuid
          AND wear_revision = :revision
        """,
    )
    suspend fun incrementLeaseGeneration(sessionUuid: Uuid, revision: Long): Int

    @Query(
        """
        UPDATE session_table
        SET wear_receipt_command_id = :commandId,
            wear_receipt_attempt_fingerprint = :attemptFingerprint,
            wear_receipt_database_epoch = :databaseEpoch,
            wear_receipt_revision = :revision
        WHERE uuid = :sessionUuid
          AND wear_revision = :revision
        """,
    )
    suspend fun storeReceipt(
        sessionUuid: Uuid,
        commandId: String,
        attemptFingerprint: ByteArray,
        databaseEpoch: String,
        revision: Long,
    ): Int

    @Query(
        """
        UPDATE session_table
        SET wear_receipt_command_id = NULL,
            wear_receipt_attempt_fingerprint = NULL,
            wear_receipt_database_epoch = NULL,
            wear_receipt_revision = NULL
        """,
    )
    suspend fun clearAllReceipts()
}
