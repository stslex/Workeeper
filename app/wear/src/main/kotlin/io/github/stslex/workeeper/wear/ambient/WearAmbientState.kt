// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ambient

import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class WearAmbientOffset(val xPx: Int = 0, val yPx: Int = 0)

internal data class WearAmbientState(
    val isAmbient: Boolean = false,
    val timestampMillis: Long? = null,
    val deviceHasLowBitAmbient: Boolean = false,
    val burnInProtectionRequired: Boolean = false,
    val offset: WearAmbientOffset = WearAmbientOffset(),
)

/** Ambient events never own navigation, drafts, commands, or an authority deadline. */
@MainThread
internal class WearAmbientController(
    private val wallClockMillis: () -> Long,
    private val expireAuthority: () -> Unit,
) : WearAmbientProvider {

    private val mutableState = MutableStateFlow(WearAmbientState())
    override val state = mutableState.asStateFlow()

    fun onEnterAmbient(deviceHasLowBitAmbient: Boolean, burnInProtectionRequired: Boolean) {
        expireAuthority()
        val timestamp = wallClockMillis()
        mutableState.value = WearAmbientState(
            isAmbient = true,
            timestampMillis = timestamp,
            deviceHasLowBitAmbient = deviceHasLowBitAmbient,
            burnInProtectionRequired = burnInProtectionRequired,
            offset = ambientOffset(timestamp, burnInProtectionRequired),
        )
    }

    fun onSystemUpdate() {
        expireAuthority()
        val current = state.value
        if (!current.isAmbient) return
        val timestamp = wallClockMillis()
        mutableState.value = current.copy(
            timestampMillis = timestamp,
            offset = ambientOffset(timestamp, current.burnInProtectionRequired),
        )
    }

    fun onExitAmbient() {
        // Expire before exposing the retained editor; waking must never renew its authority.
        expireAuthority()
        mutableState.value = state.value.copy(
            isAmbient = false,
            timestampMillis = wallClockMillis(),
            offset = WearAmbientOffset(),
        )
    }
}

internal const val AMBIENT_MAX_OFFSET_PX = 2

internal fun ambientOffset(timestampMillis: Long, burnInProtectionRequired: Boolean): WearAmbientOffset {
    if (!burnInProtectionRequired) return WearAmbientOffset()
    val minute = Math.floorDiv(timestampMillis, AMBIENT_MINUTE_MILLIS)
    val position = Math.floorMod(minute, AMBIENT_OFFSETS.size.toLong()).toInt()
    return AMBIENT_OFFSETS[position]
}

private const val AMBIENT_MINUTE_MILLIS = 60_000L
private val AMBIENT_OFFSETS = listOf(
    WearAmbientOffset(-1, -1),
    WearAmbientOffset(0, -AMBIENT_MAX_OFFSET_PX),
    WearAmbientOffset(1, -1),
    WearAmbientOffset(-AMBIENT_MAX_OFFSET_PX, 0),
    WearAmbientOffset(),
    WearAmbientOffset(AMBIENT_MAX_OFFSET_PX, 0),
    WearAmbientOffset(-1, 1),
    WearAmbientOffset(0, AMBIENT_MAX_OFFSET_PX),
    WearAmbientOffset(1, 1),
)
