// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.probe.ui.di

import dev.zacsweers.metro.createGraph
import kotlin.test.Test
import kotlin.test.assertEquals

class ProbeGraphIosTest {

    @Test
    fun metroGraphResolvesOnNative() {
        val graph = createGraph<ProbeGraph>()
        assertEquals("metro-native", graph.probeInjectable.marker())
    }
}
