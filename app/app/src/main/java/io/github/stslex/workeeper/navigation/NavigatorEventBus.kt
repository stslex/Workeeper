// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.ui.navigation.NavCommand
import io.github.stslex.workeeper.core.ui.navigation.NavResultKey
import io.github.stslex.workeeper.core.ui.navigation.NavResultsSource
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.ScreenWithResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * `@ContributesBinding(AppScope)` binds it to the [Navigator] interface for the feature readers; the app
 * `AppGraph` ALSO exposes the concrete type via a self accessor for `AppRootViewModel` (which injects
 * `NavigatorEventBus` directly, then passes it as a [NavigatorReceiver] to `NavigatorExt`). One
 * `@SingleIn(AppScope)` instance backs both — the same dual concrete/interface shape as
 * `AppDialogObserverImpl`.
 *
 * Since the Nav3 swap it is ALSO the [NavResultsSource]: results live inside the Navigator
 * implementation (there is no library transport to carry them), keyed by [NavResultKey] strings,
 * written by the command executor before the pop and cleared by `NavResults` after delivery.
 */
@ContributesBinding(AppScope::class, binding = binding<Navigator>())
@SingleIn(AppScope::class)
@Inject
class NavigatorEventBus(
    private val appReinitializer: AppReinitializer,
) : Navigator, NavigatorReceiver, NavResultsSource {

    private val log = Log.tag(TAG)

    private val _commands = MutableSharedFlow<NavCommand>(
        extraBufferCapacity = 64,
    )
    override val commands: SharedFlow<NavCommand> = _commands.asSharedFlow()

    private val results = ConcurrentHashMap<String, MutableStateFlow<Any?>>()

    override fun result(key: String): StateFlow<Any?> = resultFlow(key)

    override fun setResult(key: String, result: Any) {
        resultFlow(key).value = result
    }

    override fun clearResult(key: String) {
        resultFlow(key).value = null
    }

    private fun resultFlow(key: String): MutableStateFlow<Any?> =
        results.getOrPut(key) { MutableStateFlow(null) }

    // A pending result survives ONLY the pop that delivers it (popBackWithResult does not
    // clear). Every other navigation resets every channel: the transport is process-wide and
    // keyed by destination, not by entry, so without this a value written over a
    // non-consuming screen would leak into a later, unrelated composition of the consumer.
    // Pinned by NavigatorEventBusTest's pending-result lifecycle tests.
    private fun clearAllResults() {
        results.values.forEach { flow -> flow.value = null }
    }

    override fun navTo(screen: Screen) {
        clearAllResults()
        consume(NavCommand.NavTo(screen))
    }

    override fun popBack() {
        clearAllResults()
        consume(NavCommand.PopBack)
    }

    override fun <S, R : Any> popBackWithResult(
        destination: KClass<S>,
        result: R,
    ) where S : ScreenWithResult<R> {
        consume(NavCommand.PopBackWithResult(NavResultKey.of(destination), result))
    }

    override fun replaceTo(screen: Screen) {
        clearAllResults()
        consume(NavCommand.ReplaceTo(screen))
    }

    override fun restartApp() {
        // Restart is terminal and platform-owned — resolve the process-scoped
        // AppReinitializer by constructor injection and invoke it directly rather than
        // routing a NavCommand through the replay=0 command bus (which would silently
        // drop with no mounted subscriber, the OpenRecovery hazard).
        appReinitializer.reinitialize()
    }

    override fun openRecovery() {
        consume(NavCommand.OpenRecovery)
    }

    private fun consume(command: NavCommand) {
        log.d { "Processing navigation command: $command" }
        _commands.tryEmit(command).also { emitted ->
            if (emitted.not()) {
                log.w { "Failed to emit navigation command: $command" }
            }
        }
    }

    companion object {

        private const val TAG = "NavigatorEventBus"
    }
}
