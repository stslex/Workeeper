// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui

import android.content.Intent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.net.toUri
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.LocalNavigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.settings.di.SettingsFeature
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event

fun NavGraphBuilder.settingsGraph(
    modifier: Modifier = Modifier,
) {
    navComponentScreen(SettingsFeature) { processor ->
        val context = LocalContext.current
        val navigator = LocalNavigator.current
        val haptic = LocalHapticFeedback.current

        processor.Handle { event ->
            when (event) {
                Event.NavigateToArchive -> navigator.navTo(Screen.Archive)
                Event.NavigateBack -> navigator.popBack()
                is Event.ShowExternalLink -> {
                    val intent = Intent(Intent.ACTION_VIEW, event.url.toUri()).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                }

                is Event.Haptic -> haptic.performHapticFeedback(event.type)
            }
        }

        SettingsScreen(
            modifier = modifier,
            state = processor.state.value,
            consume = processor::consume,
        )
    }
}
