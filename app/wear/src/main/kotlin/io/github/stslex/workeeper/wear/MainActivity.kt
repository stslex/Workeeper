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
import io.github.stslex.workeeper.wear.ambient.AndroidWearAmbientProvider
import io.github.stslex.workeeper.wear.ambient.WearAmbientState
import io.github.stslex.workeeper.wear.ongoing.AndroidWearNotificationAccess
import io.github.stslex.workeeper.wear.ongoing.NotificationEnableAction
import io.github.stslex.workeeper.wear.ongoing.WearNotificationAccess
import io.github.stslex.workeeper.wear.runtime.WatchRuntime
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeFactory
import io.github.stslex.workeeper.wear.runtime.runWearRuntimeUiEvent
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures
import io.github.stslex.workeeper.wear.ui.WearControllerScreen
import io.github.stslex.workeeper.wear.ui.WearOngoingNotice
import io.github.stslex.workeeper.wear.ui.WearSurfaceModel
import io.github.stslex.workeeper.wear.ui.ongoingNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class MainActivity : ComponentActivity() {
    private lateinit var runtime: WatchRuntime
    private lateinit var notificationAccess: AndroidWearNotificationAccess
    private val access = MutableStateFlow(
        WearNotificationAccess(enabled = true, action = NotificationEnableAction.NONE),
    )
    private val staticPreview = MutableStateFlow<WearSurfaceModel?>(null)
    private var staticPreviewId: String? = null
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
        val initial = presentation(ambientProvider.state.value)
        val presentations = combine(
            runtime.surface,
            runtime.ongoingStatus,
            ambientProvider.state,
            access,
            staticPreview,
        ) { _, _, ambient, _, _ -> presentation(ambient) }
        setContent {
            val current by presentations.collectAsState(initial)
            WearControllerScreen(
                state = current.model,
                ambient = current.ambient,
                ongoingNotice = current.notice,
                onEnableNotifications = ::enableNotifications,
                onAction = { action ->
                    if (staticPreview.value == null) runWearRuntimeUiEvent { runtime.onAction(action) }
                },
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
        outState.putString(STATIC_PREVIEW_KEY, staticPreviewId)
        super.onSaveInstanceState(outState)
    }

    private fun refreshRuntime() {
        val locale = resources.configuration.locales[0]
        runWearRuntimeUiEvent {
            runtime.setLocale(locale)
            runtime.onWake()
        }
        access.value = notificationAccess.read()
        staticPreview.value = staticPreview.value?.copy(selectedLocale = locale)
    }

    private fun presentation(ambient: WearAmbientState): WearPresentation {
        // Read the synchronous expiry result even when a collector still holds an earlier surface emission.
        val preview = staticPreview.value
        val model = preview ?: runtime.surface.value
        val notice = if (preview == null) {
            ongoingNotice(model, runtime.ongoingStatus.value, access.value.enabled)
        } else {
            null
        }
        return WearPresentation(model, ambient, notice)
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
        staticPreviewId = id.takeIf { BuildConfig.DEBUG }
        staticPreview.value = if (BuildConfig.DEBUG) {
            SyntheticSurfaceFixtures.find(id)?.copy(selectedLocale = resources.configuration.locales[0])
        } else {
            null
        }
    }

    private fun enableNotifications() {
        when (notificationAccess.read().action) {
            NotificationEnableAction.REQUEST_PERMISSION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationAccess.markRequested()
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startActivity(notificationAccess.settingsIntent())
                }
            }
            NotificationEnableAction.OPEN_SETTINGS -> startActivity(notificationAccess.settingsIntent())
            NotificationEnableAction.NONE -> refreshRuntime()
        }
    }

    private data class WearPresentation(
        val model: WearSurfaceModel,
        val ambient: WearAmbientState,
        val notice: WearOngoingNotice?,
    )

    private companion object {
        const val STATIC_PREVIEW_KEY = "static_preview_id"
    }
}
