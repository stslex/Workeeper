package io.github.stslex.workeeper.probe_metro

import dev.zacsweers.metro.Inject

/** P2.b — a Metro-injectable dependency (dev.zacsweers.metro.@Inject). Exposed to the
 * root wiring, where a Metro graph provides it alongside a Hilt-provided dep. */
@Inject
class MetroGreeter {
    fun greet(): String = "metro-provided"
}
