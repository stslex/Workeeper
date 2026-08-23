// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi.di

import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime

/**
 * The app-scope surface [io.github.stslex.workeeper.core.ui.mvi.processor.rememberStoreProcessor]
 * reads (Phase 5 R3, spec §8.4): the CURRENT generation's lifetime, so every Store job it starts
 * is a descendant of it and therefore joinable by the runtime's teardown before the generation's
 * database closes.
 *
 * Acquired through the established `context.appDeps<T>()` point-acquisition — the app graph
 * implements it, so the cast is safe by construction. Deliberately ONE member: the processor
 * needs the lifetime and nothing else, and a Store must not gain a path to the graph itself.
 */
interface StoreGenerationDeps {

    val appScopeLifetime: AppScopeLifetime
}
