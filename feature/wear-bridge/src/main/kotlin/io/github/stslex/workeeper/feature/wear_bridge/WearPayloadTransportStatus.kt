// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge

/**
 * An open owner gate (wear-phase-1-active-workout-tile.md §6.1). The transport handler answers
 * nothing while any is reported, so the enum stays as the kill switch even though the bridge
 * reports none.
 */
enum class WearPayloadTransportStatus {
    PRIVACY_DISCLOSURE_REQUIRED,
    TRANSPORT_POLICY_REQUIRED,
}
