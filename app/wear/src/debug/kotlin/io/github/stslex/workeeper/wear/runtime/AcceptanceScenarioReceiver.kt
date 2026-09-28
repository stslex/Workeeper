// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class AcceptanceScenarioReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!isOrderedBroadcast) return
        val scenario = intent.getStringExtra(EXTRA_SCENARIO)
        val rejection = when {
            intent.action != ACTION -> "invalid_action"
            intent.component != ComponentName(context, AcceptanceScenarioReceiver::class.java) -> "invalid_component"
            scenario !in ALLOWED_SCENARIOS -> "invalid_scenario"
            else -> null
        }
        if (rejection != null) {
            setResult(Activity.RESULT_CANCELED, "rejected:$rejection", null)
            return
        }
        val accepted = runWearRuntimeUiEvent {
            WatchRuntimeFactory.handleDebugScenario(context.applicationContext, requireNotNull(scenario))
        }.getOrDefault(false)
        setResult(
            if (accepted) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            if (accepted) "accepted:$scenario" else "rejected:driver_rejected",
            null,
        )
    }

    internal companion object {
        const val ACTION = "io.github.stslex.workeeper.wear.ACCEPTANCE_SCENARIO"
        const val EXTRA_SCENARIO = "scenario"
        private val ALLOWED_SCENARIOS = setOf("refresh", "disconnect", "terminal")
    }
}
