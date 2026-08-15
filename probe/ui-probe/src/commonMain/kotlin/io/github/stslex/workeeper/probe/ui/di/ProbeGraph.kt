// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.probe.ui.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject

@Inject
class ProbeInjectable {
    fun marker(): String = "metro-native"
}

@DependencyGraph
interface ProbeGraph {
    val probeInjectable: ProbeInjectable
}
