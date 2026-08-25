// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery

import android.content.Context
import android.content.Intent

/**
 * Which failure routed the launch to [RecoveryActivity]. The two scenarios need different copy
 * and different diagnostics, so the launcher stamps it on the Intent rather than the surface
 * inferring it: the extra rides the task record and survives process death, a cached in-memory
 * verdict does not. See documentation/feature-specs/backup-recovery.md.
 */
enum class RecoveryScenario {

    /** Scenario 2: the live database is from a version this build cannot migrate. */
    StartupMigration,

    /** Scenario 1: a restore was interrupted and its outcome cannot be proven. */
    InterruptedRestore,

    ;

    companion object {

        private const val EXTRA_SCENARIO = "io.github.stslex.workeeper.recovery.SCENARIO"
        private const val EXTRA_ALLOW_CONTINUE =
            "io.github.stslex.workeeper.recovery.ALLOW_CONTINUE"

        /** The only way to launch the surface: an unstamped Intent has no defensible copy. */
        fun intent(
            context: Context,
            scenario: RecoveryScenario,
            allowContinue: Boolean = false,
        ): Intent =
            Intent(context, RecoveryActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_SCENARIO, scenario.name)
                .putExtra(EXTRA_ALLOW_CONTINUE, allowContinue)

        /** An absent or unknown value reads as [StartupMigration] — the shipped default. */
        fun fromIntent(intent: Intent?): RecoveryScenario {
            val name = intent?.getStringExtra(EXTRA_SCENARIO) ?: return StartupMigration
            return entries.firstOrNull { it.name == name } ?: StartupMigration
        }

        /** Continue is denied unless the startup preflight explicitly opted this launch in. */
        fun allowsContinue(intent: Intent?): Boolean =
            intent?.getBooleanExtra(EXTRA_ALLOW_CONTINUE, false) == true
    }
}
