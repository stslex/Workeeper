package io.github.stslex.workeeper.feature.settings.domain.model

internal data class BackupSummaryDomain(
    val createdAtEpochMs: Long,
    val sizeBytes: Long,
    val appVersion: String,
    val schemaVersion: Int,
)
