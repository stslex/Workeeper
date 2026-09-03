// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.export

/**
 * Produces the AI-readable workout snapshot as UTF-8 JSON bytes; Drive-agnostic.
 * See documentation/feature-specs/drive-ai-export.md.
 */
interface DatabaseJsonExporter {

    suspend fun export(
        appVersion: String,
        deviceModel: String?,
        exportedAtEpochMs: Long,
    ): ByteArray
}
