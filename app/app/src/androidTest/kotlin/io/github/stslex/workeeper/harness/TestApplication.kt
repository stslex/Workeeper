// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.harness

import io.github.stslex.workeeper.BaseApplication
import io.github.stslex.workeeper.app.common.di.AppRootDeps
import io.github.stslex.workeeper.app.common.di.AppUiAdmissionToken
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.di.AppGraph
import kotlinx.coroutines.flow.StateFlow

/**
 * The instrumentation [android.app.Application] for the consolidated `:app:app` androidTest suite
 * (App-Scope Collapse Step 6, Phase 3.3). Booted by [MetroTestRunner] in place of the production
 * `DevMobileApp`/`StoreMobileApp`; because it extends [BaseApplication] it satisfies every seam the
 * production Application does — `AppGraphOwner`, `AppDepsHolder`, `RecoveryDepsHolder`,
 * `BackupWorkerDepsHolder`, `Configuration.Provider` — so `MainActivity`
 * (`application as AppGraphOwner`) and `RecoveryActivity` (via its typed holder) resolve the
 * per-test graph transparently.
 *
 * Two overrides make it test-safe:
 *  - [appGraph] reads the resettable [MetroTestGraphHolder] instead of building the production `by lazy`
 *    graph, so [MetroTestRule] controls the `create()` roots (in-memory / fail-fast DB, fake image
 *    storage) per test.
 *  - [onCreateGraphBootstrap] is a no-op: the production body runs the recovery pre-flight + temp-file
 *    cleanup + dialog-observer bootstrap, all of which read `appGraph`. `Application.onCreate` fires at
 *    process start — BEFORE any test's `@Before` installs a graph — so skipping it is what lets the rule
 *    set roots first. Tests drive activities directly; nothing here needs the process-start bootstrap.
 */
internal class TestApplication : BaseApplication() {

    override val isDebugLoggingAllow: Boolean = true

    override val appGraph: AppGraph
        get() = MetroTestGraphHolder.runtimeDelegate?.currentGeneration?.graph
            ?: MetroTestGraphHolder.graph

    /**
     * Harness-controlled generation stream (Phase 5, spec §8.7): static mode serves the rule's
     * `Generation(1, per-test store)`; runtime mode (`MetroTestGraphHolder.runtimeDelegate`)
     * serves a REAL `AppRuntime`'s stream — the production UI-disposal handshake, used by the
     * handshake device tests.
     */
    override val appUiPhases: StateFlow<AppUiPhase>
        get() = MetroTestGraphHolder.effectiveUiPhases()

    // REAL accounting in both harness modes (the interface forbids silent no-ops): static mode
    // counts into the holder's assertable map; runtime mode drives the production gate.
    override fun admitUiGeneration(id: Int): AppUiAdmissionToken? =
        MetroTestGraphHolder.admitUiGeneration(id)

    override fun releaseUiGeneration(token: AppUiAdmissionToken) =
        MetroTestGraphHolder.releaseUiGeneration(token)

    /**
     * The REAL deps, counted. `App()`'s generation region is the only caller in the whole app, and
     * it calls this once per composed region — so the count IS "how many times a generation region
     * resolved anything from the graph", which is what `UiAdmissionRaceTest` asserts is zero for a
     * retired generation.
     */
    override fun appRootDeps(): AppRootDeps {
        MetroTestGraphHolder.appRootDepsResolutions.incrementAndGet()
        return super.appRootDeps()
    }

    override fun onCreateGraphBootstrap() {
        // Intentionally empty — see class doc. The graph is installed per test by MetroTestRule.
    }
}
