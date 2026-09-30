// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.state

import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload

internal data class AdmittedSnapshotMeta(
    val source: WorkoutSourceVersion,
    val localGeneration: Long,
    val leaseGeneration: Long?,
)

internal enum class SnapshotAdmission {
    REJECT,
    READ_ONLY,
    AUTHORITY_ELIGIBLE,
}

internal object SnapshotAdmissionPolicy {

    fun decide(
        current: AdmittedSnapshotMeta?,
        incoming: SnapshotData,
        incomingGeneration: Long,
        latestIssuedGeneration: Long,
        unsolicited: Boolean,
    ): SnapshotAdmission {
        val incomingSource = incoming.sourceVersion()
        if (unsolicited) {
            return decideUnsolicited(current, incoming, incomingSource)
        }
        if (current == null) {
            return latestOnly(incomingGeneration, latestIssuedGeneration)
        }
        if (incomingSource.databaseEpoch != current.source.databaseEpoch ||
            incomingSource.identity != current.source.identity
        ) {
            return latestOnly(incomingGeneration, latestIssuedGeneration)
        }
        if (incomingSource.identity is ActiveIdentity.NoSession) {
            return latestOnly(incomingGeneration, latestIssuedGeneration)
        }

        val currentRevision = requireNotNull(current.source.sessionRevision)
        val incomingRevision = requireNotNull(incomingSource.sessionRevision)
        if (incomingRevision < currentRevision) return SnapshotAdmission.REJECT
        if (incomingRevision > currentRevision) {
            return if (incomingGeneration == latestIssuedGeneration) {
                authorityShape(incoming)
            } else {
                SnapshotAdmission.READ_ONLY
            }
        }

        val active = incoming.payload as? SnapshotPayload.ActiveWithTarget
        val granted = active?.mutationAuthority as? MutationAuthority.Granted
        if (granted != null) {
            val currentLease = current.leaseGeneration ?: Long.MIN_VALUE
            if (granted.mutationLeaseGeneration <= currentLease) return SnapshotAdmission.REJECT
            return if (incomingGeneration == latestIssuedGeneration) {
                SnapshotAdmission.AUTHORITY_ELIGIBLE
            } else {
                SnapshotAdmission.READ_ONLY
            }
        }
        return latestOnly(incomingGeneration, latestIssuedGeneration)
    }

    private fun decideUnsolicited(
        current: AdmittedSnapshotMeta?,
        incoming: SnapshotData,
        incomingSource: WorkoutSourceVersion,
    ): SnapshotAdmission {
        if (current == null || incomingSource.identity is ActiveIdentity.NoSession) {
            return SnapshotAdmission.REJECT
        }
        if (incomingSource.databaseEpoch != current.source.databaseEpoch ||
            incomingSource.identity != current.source.identity
        ) {
            return SnapshotAdmission.REJECT
        }
        val currentRevision = requireNotNull(current.source.sessionRevision)
        val incomingRevision = requireNotNull(incomingSource.sessionRevision)
        if (incomingRevision > currentRevision) return SnapshotAdmission.READ_ONLY
        if (incomingRevision < currentRevision) return SnapshotAdmission.REJECT
        val incomingLease = (incoming.payload as? SnapshotPayload.ActiveWithTarget)
            ?.mutationAuthority
            ?.let { it as? MutationAuthority.Granted }
            ?.mutationLeaseGeneration
            ?: return SnapshotAdmission.REJECT
        return if (incomingLease > (current.leaseGeneration ?: Long.MIN_VALUE)) {
            SnapshotAdmission.READ_ONLY
        } else {
            SnapshotAdmission.REJECT
        }
    }

    private fun latestOnly(incomingGeneration: Long, latestIssuedGeneration: Long): SnapshotAdmission =
        if (incomingGeneration == latestIssuedGeneration) {
            SnapshotAdmission.AUTHORITY_ELIGIBLE
        } else {
            SnapshotAdmission.REJECT
        }

    private fun authorityShape(incoming: SnapshotData): SnapshotAdmission {
        val active = incoming.payload as? SnapshotPayload.ActiveWithTarget
            ?: return SnapshotAdmission.AUTHORITY_ELIGIBLE
        return if (active.mutationAuthority is MutationAuthority.Granted) {
            SnapshotAdmission.AUTHORITY_ELIGIBLE
        } else {
            SnapshotAdmission.AUTHORITY_ELIGIBLE
        }
    }
}
