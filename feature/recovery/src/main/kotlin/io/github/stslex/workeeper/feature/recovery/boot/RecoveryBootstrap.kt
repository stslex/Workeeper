// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.boot

/**
 * Public marker for the cross-feature `AppDialog` reactor in this module. Its
 * implementer, [io.github.stslex.workeeper.feature.recovery.RestoreDialogChoiceObserver],
 * is `@SingleIn(AppScope) @ContributesBinding(AppScope)`-bound to this type and registers
 * its `observeUserActions` subscriber in its `init { ... launchIn(scope) }` block. Eagerly
 * reading the `recoveryBootstrap` accessor on the app graph in `BaseApplication.onCreate`
 * (`appGraph.recoveryBootstrap`) constructs that singleton, which arms the subscriber
 * before any UI dispatch — see `documentation/feature-specs/app-dialogs.md` →
 * "Cross-feature observation".
 *
 * The marker exists purely to type-erase the observer across the module boundary: callers
 * in `app/app` cannot reference the `internal` observer class directly, but they can hold a
 * [RecoveryBootstrap] reference (and immediately discard it — the side-effect of the
 * singleton being constructed is what we want, not the value).
 */
interface RecoveryBootstrap
