// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.harness

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Instrumentation runner for the consolidated `:app:app` androidTest suite (App-Scope Collapse Step 6,
 * Phase 3.3). Boots [TestApplication] (a `BaseApplication` subclass holding the per-test Metro graph)
 * in place of the production Application — the Metro replacement for the deleted Hilt `WorkeeperTestRunner`
 * / `HiltTestRunner` that booted `HiltTestApplication`.
 */
class MetroTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, TestApplication::class.java.name, context)
}
