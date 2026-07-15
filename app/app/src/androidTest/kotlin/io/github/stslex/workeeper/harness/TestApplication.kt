// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.harness

import io.github.stslex.workeeper.BaseApplication
import io.github.stslex.workeeper.di.AppGraph

/**
 * The instrumentation [android.app.Application] for the consolidated `:app:app` androidTest suite
 * (App-Scope Collapse Step 6, Phase 3.3). Booted by [MetroTestRunner] in place of the production
 * `DevMobileApp`/`StoreMobileApp`; because it extends [BaseApplication] it satisfies every seam the
 * production Application does — `AppGraphOwner`, `AppGraphContractHolder`, `AppDialogPublisherHolder`,
 * `AppDialogInternalsHolder`, `Configuration.Provider` — so `MainActivity`
 * (`application as AppGraphOwner`) and `RecoveryActivity` (`context.appGraphContract()`) resolve the
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
        get() = MetroTestGraphHolder.graph

    override fun onCreateGraphBootstrap() {
        // Intentionally empty — see class doc. The graph is installed per test by MetroTestRule.
    }
}
