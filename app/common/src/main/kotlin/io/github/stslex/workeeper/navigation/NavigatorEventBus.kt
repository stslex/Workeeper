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
import io.github.stslex.workeeper.core.ui.navigation.NavigatorReceiver
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
 * The one `@SingleIn(AppScope)` navigator: bound to [Navigator] for features, exposed concretely
 * through `AppRootDeps`, and — since the Nav3 swap — also the [NavResultsSource].
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

    // GUARD: every navigation but popBackWithResult resets every result channel — the transport
    // is process-wide and keyed by destination, so a stale value would leak into a later consumer.
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
        // Restart is terminal and platform-owned: invoke the injected AppReinitializer directly
        // rather than routing through the replay=0 command bus, which drops with no subscriber.
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
