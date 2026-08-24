// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.snackbar

/**
 * Marks the generation-transition controls on [SnackbarManager]. They are public only because the
 * runtime that drives them lives in another Gradle module, not because features may call them:
 * a [SnackbarManager.fenceResolves] without its paired [SnackbarManager.unfenceResolves] wedges
 * every snackbar in the process, and a stray [SnackbarManager.advanceGenerationEpoch] silently
 * discards in-flight toasts.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Generation-transition control: owned by the app runtime, not by feature code.",
)
@Retention(AnnotationRetention.BINARY)
annotation class SnackbarGenerationTransition
