// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.harness

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.di.AppGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide, resettable slot for the per-test Metro [AppGraph] (App-Scope Collapse Step 6, Phase 3.3).
 *
 * Android instrumentation runs the whole suite in ONE process and creates the [TestApplication] exactly
 * once, so the app-scope graph cannot be a construction-time constant — fail-fast-DB and in-memory-DB
 * tests would collide on a single shared graph. [MetroTestRule] rebuilds a fresh graph per test and
 * publishes it here in `@Before`; [TestApplication.appGraph] reads it; `@After` clears it.
 *
 * Phase 5 adds the UI-generation face: the rule also publishes [AppUiPhase.Generation] `(1, a
 * fresh per-test ViewModelStore)` so `App()` composes — with no published generation the shell
 * shows only the Transitioning interstitial. The per-test store gives `AppRootViewModel` and the
 * app-dialog Store the same fresh-per-test lifecycle the per-test graph gives everything else,
 * and [reset] clears it so nothing retains a dead graph's deps across tests.
 *
 * Lives in `:app:app` androidTest so it can name the module-`internal` [AppGraph] — the entire reason the
 * harness consolidates here rather than in a shared upstream infra module (which cannot see it).
 */
internal object MetroTestGraphHolder {

    @Volatile
    private var current: AppGraph? = null

    @Volatile
    private var currentStore: ViewModelStore? = null

    private val nextGenerationId = java.util.concurrent.atomic.AtomicInteger(1)

    private val uiPhaseFlow = MutableStateFlow<AppUiPhase>(AppUiPhase.Transitioning)

    /** What [TestApplication] serves as `appUiPhases`. */
    val uiPhases: StateFlow<AppUiPhase> = uiPhaseFlow.asStateFlow()

    /** The graph installed for the currently-running test. Throws if read before [MetroTestRule] sets it. */
    val graph: AppGraph
        get() = current ?: error(
            "No test AppGraph installed. A test that reads the app graph must run with MetroTestRule " +
                "(the rule builds and installs the graph in @Before).",
        )

    /**
     * Installs a graph as the CURRENT test generation. Re-installing within one test is the
     * harness's generation swap (the UI-swap suite uses it): the previous generation's
     * ViewModelStore is cleared deterministically (mirroring the runtime's disposal) and a fresh
     * id is published, which is what re-keys `App()`'s generation region. Ids increment per
     * install — stable WITHIN a test (Activity recreation restores against the same id), fresh
     * ACROSS installs (a swap must never reuse the old saveable slot).
     */
    fun install(graph: AppGraph) {
        uiPhaseFlow.value = AppUiPhase.Transitioning
        currentStore?.clear()
        current = graph
        val store = ViewModelStore()
        currentStore = store
        uiPhaseFlow.value = AppUiPhase.Generation(
            id = nextGenerationId.getAndIncrement(),
            viewModelStoreOwner = object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = store
            },
        )
    }

    fun reset() {
        uiPhaseFlow.value = AppUiPhase.Transitioning
        currentStore?.clear()
        currentStore = null
        current = null
    }

}
