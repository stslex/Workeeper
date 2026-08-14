// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.navigation

sealed interface NavCommand {
    data class NavTo(val screen: Screen) : NavCommand
    data class ReplaceTo(val screen: Screen) : NavCommand
    data object PopBack : NavCommand

    /**
     * Pop, handing [result] back to the destination underneath.
     *
     * [key] and the erased [result] are the adapter boundary: the untyped shape stops
     * here, inside the module that is allowed to know the navigation library, because the
     * Nav2 transport underneath ([androidx.lifecycle.SavedStateHandle]) is itself untyped.
     * Everything a feature touches — [ScreenWithResult], [Navigator.popBackWithResult],
     * and the read side — is fully typed, and the type comes from the destination.
     *
     * [result] is [Any], not `Any?`: [ScreenWithResult] bounds its parameter to non-null
     * so that absence is expressed by the *read* returning `null`.
     */
    data class PopBackWithResult(val key: String, val result: Any) : NavCommand

    data object OpenRecovery : NavCommand
}
