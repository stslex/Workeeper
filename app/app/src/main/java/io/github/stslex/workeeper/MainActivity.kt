// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.stslex.workeeper.core.ui.mvi.performance.FirebaseScreenRenderRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceMetricsRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.RecordAction
import io.github.stslex.workeeper.di.AppGraphOwner
import io.github.stslex.workeeper.feature.recovery.RecoveryActivity
import io.github.stslex.workeeper.feature.recovery.domain.StartupCheck

// App-Scope Collapse Step 6 (cut): Hilt-free. Reads its two app-scope deps from the internal AppGraph via
// the AppGraphOwner interface (never a concrete-Application cast) — MainActivity is in app/app so it can
// see the internal seam (the tighter idiom vs the public `context.appDeps<T>()` accessor).
class MainActivity : ComponentActivity() {

    private val appGraph get() = (application as AppGraphOwner).appGraph
    private val activityProducer get() = appGraph.activityHolderProducer
    private val startupMigrationCoordinator get() = appGraph.startupMigrationCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Scenario 2 routing — `BaseApplication.onCreate` already ran the
        // startup pre-flight and cached the decision on the coordinator.
        // If the decision was RouteToRecovery, finish this activity and
        // launch `RecoveryActivity` directly via Intent. The brief MainActivity
        // frame is acceptable for a rare developer-error path; we explicitly
        // avoid `PackageManager.setComponentEnabledSetting` launcher swaps
        // because they have known OEM-ROM flakiness.
        //
        // Bootstrap-context dispatch: we launch RecoveryActivity directly
        // rather than through `Navigator.openRecovery()` (the `NavCommand.OpenRecovery`
        // flow path) because this call site fires BEFORE `setContent { App() }`
        // composes `NavigationEventBusSetup`. The bus is `MutableSharedFlow(
        // replay = 0)`; an emit with no subscriber attached is dropped, so
        // routing through `Navigator.openRecovery()` here would deterministically
        // lose the signal. Same Option Y split as the bootstrap restart path —
        // see `backup-recovery.md` → "Restart contract / OpenRecovery contract".
        // Two independent reasons to hand off to the DB-free recovery surface: the Scenario-2
        // migration decision, and a Scenario-1 attempt whose outcome is not durably provable
        // (spec §8.4 terminal recovery — that launch armed no DB-bound work either).
        if (startupMigrationCoordinator.lastDecision is StartupCheck.RouteToRecovery ||
            appGraph.restoreRecoveryCoordinator.recoverySurfaceRequired
        ) {
            startActivity(
                Intent(this, RecoveryActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            finish()
            return
        }

        PerformanceMetricsRecorder.process(RecordAction.ActivityCreated(coldStart = savedInstanceState == null))
        activityProducer.produce(this)

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityProducer.produce(null)
        FirebaseScreenRenderRecorder.clearAllTraces()
        PerformanceMetricsRecorder.process(RecordAction.ClearTraces)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // For correctly understand activity cold start or not
        outState.putString("activitySave", "saved")
        super.onSaveInstanceState(outState)
    }
}
