// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge

/** Binds the first database operation of a callback to exactly one runtime generation. */
interface WearBridgeWorkLease {
    val deps: WearBridgeDeps
    fun release()
}

/** Application seam for callbacks that must not retain an AppGraph across replacement. */
interface WearBridgeWorkDepsHolder {
    suspend fun awaitWearBridgeWorkLease(): WearBridgeWorkLease?
}

/** Executes one callback against one generation and releases admission on every exit path. */
suspend fun <T> WearBridgeWorkDepsHolder.withWearBridgeWorkLease(
    block: suspend (WearBridgeDeps) -> T,
): T? {
    val lease = awaitWearBridgeWorkLease() ?: return null
    return try {
        block(lease.deps)
    } finally {
        lease.release()
    }
}
