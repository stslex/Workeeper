// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.test.runner

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Instrumentation runner shared by every feature integration test in the project.
 *
 * Each consuming `androidLibrary` sets `defaultConfig.testInstrumentationRunner` to this
 * class so the test APK boots Hilt's [HiltTestApplication] instead of any production
 * `BaseApplication`. Hilt forbids `@HiltAndroidApp` in library modules, so a custom
 * application class would have to live in an `app` module — `HiltTestApplication` is the
 * supported cross-module replacement and matches the pattern already used by
 * `app/dev/.../HiltTestRunner.kt`.
 */
class WorkeeperTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
