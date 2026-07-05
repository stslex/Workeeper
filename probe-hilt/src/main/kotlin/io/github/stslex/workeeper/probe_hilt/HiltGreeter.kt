package io.github.stslex.workeeper.probe_hilt

import javax.inject.Inject

/** P2.b — a Hilt-provided dependency (javax.inject.@Inject, processed by Hilt's KSP). */
class HiltGreeter @Inject constructor() {
    fun greet(): String = "hilt-provided"
}
