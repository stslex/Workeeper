// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import kotlinx.coroutines.flow.MutableStateFlow

internal class ResultChannelRegistry {
    private val channels = MutableStateFlow<Map<String, MutableStateFlow<Any?>>>(emptyMap())

    fun channel(key: String): MutableStateFlow<Any?> {
        channels.value[key]?.let { return it }
        val candidate = MutableStateFlow<Any?>(null)
        while (true) {
            val snapshot = channels.value
            snapshot[key]?.let { return it }
            // Return only the installed channel; a concurrent registration may win the CAS.
            if (channels.compareAndSet(snapshot, snapshot + (key to candidate))) return candidate
        }
    }

    fun clearAll() {
        channels.value.values.forEach { channel -> channel.value = null }
    }
}
