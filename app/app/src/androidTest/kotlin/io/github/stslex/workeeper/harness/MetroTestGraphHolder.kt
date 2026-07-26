// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.harness

import io.github.stslex.workeeper.di.AppGraph

/**
 * Process-wide, resettable slot for the per-test Metro [AppGraph] (App-Scope Collapse Step 6, Phase 3.3).
 *
 * Android instrumentation runs the whole suite in ONE process and creates the [TestApplication] exactly
 * once, so the app-scope graph cannot be a construction-time constant — fail-fast-DB and in-memory-DB
 * tests would collide on a single shared graph. [MetroTestRule] rebuilds a fresh graph per test and
 * publishes it here in `@Before`; [TestApplication.appGraph] reads it; `@After` clears it.
 *
 * Lives in `:app:app` androidTest so it can name the module-`internal` [AppGraph] — the entire reason the
 * harness consolidates here rather than in a shared upstream infra module (which cannot see it).
 */
internal object MetroTestGraphHolder {

    @Volatile
    private var current: AppGraph? = null

    /** The graph installed for the currently-running test. Throws if read before [MetroTestRule] sets it. */
    val graph: AppGraph
        get() = current ?: error(
            "No test AppGraph installed. A test that reads the app graph must run with MetroTestRule " +
                "(the rule builds and installs the graph in @Before).",
        )

    fun install(graph: AppGraph) {
        current = graph
    }

    fun reset() {
        current = null
    }
}
