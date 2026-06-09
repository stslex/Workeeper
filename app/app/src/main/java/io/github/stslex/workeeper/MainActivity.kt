// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.stslex.workeeper.core.ui.kit.utils.activityHolder.ActivityHolderProducer
import io.github.stslex.workeeper.core.ui.mvi.performance.FirebaseScreenRenderRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceMetricsRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.RecordAction
import io.github.stslex.workeeper.feature.recovery.RecoveryActivity
import io.github.stslex.workeeper.feature.recovery.domain.StartupCheck
import io.github.stslex.workeeper.feature.recovery.domain.StartupMigrationCoordinator
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var activityProducer: ActivityHolderProducer

    @Inject
    internal lateinit var startupMigrationCoordinator: StartupMigrationCoordinator

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
        if (startupMigrationCoordinator.lastDecision is StartupCheck.RouteToRecovery) {
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
