// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.transport

/**
 * What the transport coordinator needs from the Data Layer, and nothing else
 * (wear-paired-transport.md §7.1). The one implementation that talks to Google Play services is
 * [PlayServicesWearLink]; tests serve bytes from the real codec through a fake.
 *
 * Every call may throw; the coordinator classifies a failure, never the link.
 */
internal interface WearLink {

    /** The watch's own node id. Feeds only the local command fingerprint; never sent (§5.2). */
    suspend fun localNodeId(): String

    /** Reachable nodes advertising the phone capability, direct or relayed (§5.2). */
    suspend fun reachablePhones(): List<PhoneNode>

    /** One request/response exchange; an empty result means "no semantic response" (§5.3). */
    suspend fun request(nodeId: String, request: ByteArray): ByteArray

    /**
     * Registers [onChange] for the process lifetime: `true` when some phone becomes reachable.
     * Returns once the registration took effect; throws when it failed, so the caller can retry.
     */
    suspend fun observeReachability(onChange: (reachable: Boolean) -> Unit)
}

internal data class PhoneNode(val id: String, val isNearby: Boolean) {
    override fun toString(): String = "PhoneNode(isNearby=$isNearby)"
}

/**
 * §5.2 step 2: a nearby node if any, otherwise any reachable node; ties break on the smallest id.
 * `isNearby` only prefers a direct route; a relayed one is permitted (owner decision D2).
 */
internal fun List<PhoneNode>.preferredPhone(): PhoneNode? =
    sortedWith(compareByDescending<PhoneNode> { it.isNearby }.thenBy { it.id }).firstOrNull()
