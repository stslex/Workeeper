// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.stslex.workeeper.wear.ambient.AndroidWearAmbientProvider
import io.github.stslex.workeeper.wear.mvi.store.WearPlatformState
import io.github.stslex.workeeper.wear.ongoing.AndroidWearNotificationAccess
import io.github.stslex.workeeper.wear.ongoing.NotificationEnableAction
import io.github.stslex.workeeper.wear.runtime.WatchRuntime
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeFactory
import io.github.stslex.workeeper.wear.runtime.runWearRuntimeUiEvent
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures
import io.github.stslex.workeeper.wear.ui.WearControllerRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var runtime: WatchRuntime
    private lateinit var notificationAccess: AndroidWearNotificationAccess
    private val platform = MutableStateFlow(WearPlatformState())
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshRuntime()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = WatchRuntimeFactory.get(applicationContext)
        notificationAccess = AndroidWearNotificationAccess(this)
        if (savedInstanceState == null) {
            acceptDebugIntent(intent)
        } else if (BuildConfig.DEBUG) {
            showStaticPreview(savedInstanceState.getString(STATIC_PREVIEW_KEY))
        }
        refreshRuntime()
        val ambientProvider = AndroidWearAmbientProvider(
            activity = this,
            expireAuthority = { runWearRuntimeUiEvent { runtime.onWake() } },
        )
        // The controller is interactive while resumed and not ambient (wear-paired-transport.md
        // §7.4 O1). A dedicated signal: onWake also runs for ambient updates, deadlines and Tiles.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    ambientProvider.state.map { !it.isAmbient }.distinctUntilChanged().collect { interactive ->
                        runWearRuntimeUiEvent { runtime.onControllerInteractive(interactive) }
                    }
                } finally {
                    runWearRuntimeUiEvent { runtime.onControllerInteractive(false) }
                }
            }
        }
        setContent {
            val access by platform.collectAsState()
            val ambient by ambientProvider.state.collectAsState()
            WearControllerRoute(
                factory = {
                    (application as WearApplication).graph.wearGraphFactory.createWearGraph(runtime).store
                },
                platform = access.copy(ambient = ambient),
                onEnableNotifications = ::enableNotifications,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRuntime()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptDebugIntent(intent)
        refreshRuntime()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATIC_PREVIEW_KEY, platform.value.previewId)
        super.onSaveInstanceState(outState)
    }

    private fun refreshRuntime() {
        val locale = resources.configuration.locales[0]
        runWearRuntimeUiEvent {
            runtime.setLocale(locale)
            runtime.onWake()
        }
        platform.value = platform.value.copy(notificationsEnabled = notificationAccess.read().enabled)
    }

    private fun acceptDebugIntent(intent: Intent) {
        showStaticPreview(null)
        if (!BuildConfig.DEBUG) return
        val scenario = intent.getStringExtra(SyntheticSurfaceFixtures.EXTRA_ID) ?: return
        val handled = runWearRuntimeUiEvent {
            WatchRuntimeFactory.handleDebugScenario(applicationContext, scenario)
        }
        if (handled.getOrNull() == false) showStaticPreview(scenario.removePrefix("fixture:"))
    }

    private fun showStaticPreview(id: String?) {
        platform.value = platform.value.copy(previewId = id.takeIf { BuildConfig.DEBUG })
    }

    private fun enableNotifications() {
        when (notificationAccess.read().action) {
            NotificationEnableAction.REQUEST_PERMISSION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationAccess.markRequested()
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    notificationAccess.openSettings()
                }
            }
            NotificationEnableAction.OPEN_SETTINGS -> notificationAccess.openSettings()
            NotificationEnableAction.NONE -> refreshRuntime()
        }
    }

    private companion object {
        const val STATIC_PREVIEW_KEY = "static_preview_id"
    }
}
