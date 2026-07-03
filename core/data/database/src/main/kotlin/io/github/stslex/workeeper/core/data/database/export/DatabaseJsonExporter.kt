// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.export

/**
 * Produces the AI-readable workout snapshot as UTF-8 JSON bytes (spec `drive-ai-export.md`).
 * Reads the full Room graph and encodes it; it is **Drive-agnostic** — upload, scheduling,
 * and the user toggle live in other modules. [appVersion] / [deviceModel] /
 * [exportedAtEpochMs] are supplied by the caller (the orchestration seam owns package-info
 * and clock access); the DB schema version is read from the live database.
 *
 * Reading entities straight into export DTOs here deliberately **bypasses the domain layer**
 * (no `*Domain` mapping): the snapshot is a verbatim data-layer projection of the database,
 * exactly like the binary `.db` copy the backup path makes — so it is not a layering
 * violation, it is the same "dump the data layer" contract in text form.
 */
interface DatabaseJsonExporter {

    suspend fun export(
        appVersion: String,
        deviceModel: String?,
        exportedAtEpochMs: Long,
    ): ByteArray
}
