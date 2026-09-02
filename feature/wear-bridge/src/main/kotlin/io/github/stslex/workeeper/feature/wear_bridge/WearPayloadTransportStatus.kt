// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge

/** The bridge remains disconnected from workout-payload transport until both owner gates close. */
enum class WearPayloadTransportStatus {
    PRIVACY_DISCLOSURE_REQUIRED,
    TRANSPORT_POLICY_REQUIRED,
}
