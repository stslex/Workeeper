// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceMetricsRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.RecordAction
import io.github.stslex.workeeper.core.ui.navigation.NavCommand
import io.github.stslex.workeeper.core.ui.navigation.NavResultsSource
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.recovery.RecoveryActivity

object NavigatorExt {

    private const val TAG = "NAVIGATION"

    private val logger = Log.tag(TAG)

    @Composable
    fun NavigationEventBusSetup(
        navigatorHolder: NavigatorHolder,
        navigator: NavigatorReceiver,
        results: NavResultsSource,
    ) {
        val context = LocalContext.current
        LaunchedEffect(navigatorHolder) {
            navigator.commands.collect { command ->
                processCommand(
                    holder = navigatorHolder,
                    command = command,
                    results = results,
                    context = context,
                )
            }
        }
    }

    private fun processCommand(
        holder: NavigatorHolder,
        command: NavCommand,
        results: NavResultsSource,
        context: Context,
    ) {
        logger.i { "Processing navigation command: $command" }
        when (command) {
            is NavCommand.NavTo -> navTo(holder, command.screen)
            is NavCommand.PopBack -> popBack(holder)
            is NavCommand.PopBackWithResult -> popBackWithResult(
                holder = holder,
                results = results,
                key = command.key,
                result = command.result,
            )

            is NavCommand.ReplaceTo -> replaceTo(holder, command.screen)
            NavCommand.OpenRecovery -> openRecovery(context)
        }
    }

    /**
     * Push, or — for a singleTop destination, i.e. a bottom-bar root — replace the top entry.
     *
     * Replace-last is behaviour-identical to Nav2's `popUpTo(current) { inclusive = true;
     * saveState = true } + launchSingleTop`: the `saveState` half wrote state that NOTHING ever
     * restored (no `restoreState` existed anywhere), so tab round trips arrived reset — pinned by
     * `BackStackStateRestorationTest.selectionModeArrivesResetAfterABottomBarRoundTrip`, whose
     * mutation proof shows the pin sees the difference. One deliberate delta, recorded in the
     * swap PR: re-tapping the ACTIVE tab no longer mints a fresh entry, because the three roots
     * are `data object`s and Nav3 keys entry state by the key's identity — the old
     * fresh-entry-on-retap reset was an artefact of Nav2's per-entry UUIDs, observable only as a
     * same-tab state reset, and no oracle pins it.
     */
    private fun navTo(
        holder: NavigatorHolder,
        screen: Screen,
    ) {
        logger.d("navTo $screen")
        try {
            PerformanceMetricsRecorder.process(RecordAction.Navigation.NavTo(screen::class))
            val stack = holder.backStack
            if (screen.isSingleTop) {
                stack[stack.lastIndex] = screen
            } else {
                stack.add(screen)
            }
        } catch (ignore: Exception) {
            logger.e(ignore, "screen: $screen")
        }
    }

    /**
     * Pops only when there is something underneath — the same observable as Nav2's
     * `popBackStack()`, which returns `false` at the root. System back at the root is the
     * platform's (the activity finishes); `NavDisplay` must never be handed an empty stack.
     */
    private fun popBack(holder: NavigatorHolder) {
        logger.d("popBack")
        val stack = holder.backStack
        if (stack.size > 1) {
            stack.removeLastOrNull()
        } else {
            logger.w { "popBack ignored on the root entry" }
        }
    }

    /**
     * The Nav3 half of [io.github.stslex.workeeper.core.ui.navigation.ScreenWithResult]:
     * publish the result into the app-owned [NavResultsSource], then pop.
     *
     * Order matters and is unchanged from the Nav2 adapter — the value has to be readable
     * *before* the pop reveals the consumer, or it recomposes on arrival with nothing there and
     * the result is lost. This is the only place the untyped key/`Any` shape executes; both
     * sides of it are typed off the destination.
     */
    private fun popBackWithResult(
        holder: NavigatorHolder,
        results: NavResultsSource,
        key: String,
        result: Any,
    ) {
        logger.d { "popBackWithResult($key=$result)" }

        results.setResult(key, result)
        popBack(holder)
    }

    private fun replaceTo(
        holder: NavigatorHolder,
        screen: Screen,
    ) {
        logger.d("replaceTo $screen")
        try {
            PerformanceMetricsRecorder.process(RecordAction.Navigation.ReplaceTo(screen::class))
            val stack = holder.backStack
            stack[stack.lastIndex] = screen
        } catch (ignore: Exception) {
            logger.e(ignore, "screen: $screen")
        }
    }

    private fun openRecovery(context: Context) {
        val intent = Intent(context, RecoveryActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
