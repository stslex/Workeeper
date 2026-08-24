// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.harness

import io.github.stslex.workeeper.BaseApplication
import io.github.stslex.workeeper.app.common.di.AppRootDeps
import io.github.stslex.workeeper.app.common.di.AppUiAdmissionToken
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.di.AppGraph
import kotlinx.coroutines.flow.StateFlow

/**
 * Instrumentation [android.app.Application] for the `:app:app` androidTest suite. Extends
 * [BaseApplication] so production seams resolve the per-test [MetroTestGraphHolder] graph.
 */
internal class TestApplication : BaseApplication() {

    override val isDebugLoggingAllow: Boolean = true

    override val appGraph: AppGraph
        get() = MetroTestGraphHolder.runtimeDelegate?.currentGeneration?.graph
            ?: MetroTestGraphHolder.graph

    /** Static mode serves the rule's `Generation(1)`; runtime mode serves a real `AppRuntime`. */
    override val appUiPhases: StateFlow<AppUiPhase>
        get() = MetroTestGraphHolder.effectiveUiPhases()

    // Real accounting in both harness modes; the interface forbids silent no-ops.
    override fun admitUiGeneration(id: Int): AppUiAdmissionToken? =
        MetroTestGraphHolder.admitUiGeneration(id)

    override fun releaseUiGeneration(token: AppUiAdmissionToken) =
        MetroTestGraphHolder.releaseUiGeneration(token)

    /** The real deps, counted: one resolution per composed generation region. */
    override fun appRootDeps(): AppRootDeps {
        MetroTestGraphHolder.appRootDepsResolutions.incrementAndGet()
        return super.appRootDeps()
    }

    // The harness owns the generation source, so the host-teardown clear routes there too.
    override fun onUiHostDestroyed() = MetroTestGraphHolder.onUiHostDestroyed()

    override fun onCreateGraphBootstrap() {
        // Intentionally empty: MetroTestRule installs the graph per test, after onCreate runs.
    }
}
