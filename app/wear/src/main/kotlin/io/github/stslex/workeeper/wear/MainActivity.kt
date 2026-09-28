// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.stslex.workeeper.wear.state.WatchProcessState
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures
import io.github.stslex.workeeper.wear.ui.WearControllerScreen
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val locale = resources.configuration.locales[0]
        val synthetic = if (BuildConfig.DEBUG) {
            SyntheticSurfaceFixtures.find(intent.getStringExtra(SyntheticSurfaceFixtures.EXTRA_ID))
                ?.copy(selectedLocale = locale)
        } else {
            null
        }
        val initialModel = synthetic ?: WatchProcessState.currentSurface().copy(selectedLocale = locale)
        val presentedSurface = WatchProcessState.surface.map { model ->
            synthetic ?: model.copy(selectedLocale = locale)
        }
        setContent {
            val model by presentedSurface.collectAsState(initial = initialModel)
            WearControllerScreen(state = model, onAction = {})
        }
    }
}
