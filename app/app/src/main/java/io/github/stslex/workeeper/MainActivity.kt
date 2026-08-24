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

// Reads its app-scope deps from the internal AppGraph through AppGraphOwner, never a concrete cast.
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

        // Hand off to the DB-free recovery surface: a Scenario-2 migration decision, or a
        // Scenario-1 attempt with no provable outcome. See feature-specs/backup-recovery.md.
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
