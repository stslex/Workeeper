// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.di

/**
 * Metro feature-scope marker for feature/archive — the Metro analogue of Hilt's
 * `@ViewModelScoped` (ViewModelComponent). Mirrors the frozen Phase-B spike's
 * `abstract class FeatureScope private constructor()` token form, which compiles as a
 * `@SingleIn(X::class)` scope key on the Metro version this branch ships (1.3.2).
 *
 * Every Metro-constructed archive node EXCEPT the Store is `@SingleIn(ArchiveScope::class)`:
 * `ArchiveInteractorImpl`, `ArchiveHandlerStoreImpl` and the three handlers. `ArchiveStoreImpl`
 * is deliberately UNSCOPED — its retention is owned by the Android `ViewModelStore` via
 * `rememberMetroStoreProcessor`. So [ArchiveGraph.archiveStore] is NOT a cached accessor: every
 * read builds a fresh Store whose `BaseStore.init` re-runs `storeEmitter.setStore(this)` on the
 * shared `@SingleIn` `ArchiveHandlerStoreImpl`, rebinding the emitter away from the previously
 * built Store. Read `archiveStore` EXACTLY ONCE per created extension.
 *
 * The extension is built inside the `rememberMetroStoreProcessor` factory lambda (once per
 * retained Store), so this scope key bounds the feature-scoped instances to that Store's
 * lifetime — exactly the lifetime `@ViewModelScoped` gave under Hilt.
 */
internal abstract class ArchiveScope private constructor()
