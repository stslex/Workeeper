// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.boot

/**
 * Public marker exposed by the cross-feature `AppDialog` reactor in this module
 * for the bootstrap Hilt EntryPoint. The implementing `@Singleton` (the
 * `internal RestoreDialogChoiceObserver`) registers its `observeUserActions`
 * subscriber in its `init { ... }` block, so eagerly resolving this binding
 * via [AppDialogObserverBootstrapEntryPoint] in `BaseApplication.onCreate`
 * is what arms the subscriber before any UI dispatch — see
 * `documentation/feature-specs/app-dialogs.md` → "Bootstrap (BLOCKER 1)".
 *
 * The marker exists purely to type-erase the observer for the cross-module
 * EntryPoint signature: callers in `app/app` cannot reference the internal
 * observer class directly, but they can hold a [RecoveryBootstrap] reference
 * (and immediately discard it — the side-effect of the singleton being
 * constructed is what we want, not the value).
 */
interface RecoveryBootstrap
