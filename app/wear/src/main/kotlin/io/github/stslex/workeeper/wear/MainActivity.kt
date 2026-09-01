// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.stslex.workeeper.wear.state.WatchProcessState
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures
import io.github.stslex.workeeper.wear.ui.WearControllerScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val synthetic = remember {
                if (BuildConfig.DEBUG) {
                    SyntheticSurfaceFixtures.find(
                        intent.getStringExtra(SyntheticSurfaceFixtures.EXTRA_ID),
                    )
                } else {
                    null
                }
            }
            val processModel by WatchProcessState.surface.collectAsState()
            val model = synthetic ?: processModel
            WearControllerScreen(state = model, onAction = {})
        }
    }
}
