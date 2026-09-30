// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import io.github.stslex.workeeper.core.core.logger.telemetryTypeName
import io.github.stslex.workeeper.core.ui.navigation.NavCommand
import io.github.stslex.workeeper.core.ui.navigation.Screen

/**
 * The log label of a navigation command: command and destination type names only.
 *
 * GUARD: never the command or screen itself. [Screen.ExerciseImage] carries a picked image URI or
 * path, and navigation lines reach the Crashlytics log (wear-paired-transport.md §9.2).
 */
internal fun NavCommand.logLabel(): String = when (this) {
    is NavCommand.NavTo -> "NavTo(${screen.logLabel()})"
    is NavCommand.ReplaceTo -> "ReplaceTo(${screen.logLabel()})"
    is NavCommand.PopBackWithResult -> "PopBackWithResult($key)"
    NavCommand.PopBack -> "PopBack"
    NavCommand.OpenRecovery -> "OpenRecovery"
}

internal fun Screen.logLabel(): String = telemetryTypeName(this)
