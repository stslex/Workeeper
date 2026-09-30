// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.wear

import androidx.room3.ColumnInfo
import kotlin.uuid.Uuid

data class SessionWearSyncRow(
    @ColumnInfo(name = "session_uuid")
    val sessionUuid: Uuid,
    @ColumnInfo(name = "wear_revision")
    val revision: Long,
    @ColumnInfo(name = "wear_lease_generation")
    val leaseGeneration: Long,
    @ColumnInfo(name = "wear_receipt_command_id")
    val receiptCommandId: String?,
    @ColumnInfo(name = "wear_receipt_attempt_fingerprint")
    val receiptAttemptFingerprint: ByteArray?,
    @ColumnInfo(name = "wear_receipt_database_epoch")
    val receiptDatabaseEpoch: String?,
    @ColumnInfo(name = "wear_receipt_revision")
    val receiptRevision: Long?,
)
