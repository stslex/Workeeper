// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.stslex.workeeper.wear.ambient.AndroidWearAmbientProvider
import io.github.stslex.workeeper.wear.state.WatchProcessState
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures
import io.github.stslex.workeeper.wear.ui.WearControllerScreen
import kotlinx.coroutines.flow.combine

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
        val ambientProvider = AndroidWearAmbientProvider(
            activity = this,
            expireAuthority = { WatchProcessState.expireAuthority() },
        )
        val initialModel = synthetic ?: WatchProcessState.currentSurface().copy(selectedLocale = locale)
        val initialPresentation = initialModel to ambientProvider.state.value
        val presentedSurface = combine(WatchProcessState.surface, ambientProvider.state) { _, ambient ->
            // Read the synchronous expiry result even if the surface collector is behind the ambient event.
            val model = synthetic ?: WatchProcessState.currentSurface().copy(selectedLocale = locale)
            model to ambient
        }
        setContent {
            val presentation by presentedSurface.collectAsState(initial = initialPresentation)
            val (model, ambient) = presentation
            WearControllerScreen(state = model, ambient = ambient, onAction = {})
        }
    }
}
