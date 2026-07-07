// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.di

/**
 * Metro feature-scope marker for feature/archive — the Metro analogue of Hilt's
 * `@ViewModelScoped` (ViewModelComponent). Mirrors the frozen Phase-B spike's
 * `abstract class FeatureScope private constructor()` token form (proven to compile as a
 * `@SingleIn(X::class)` scope key on Metro 1.1.1).
 *
 * Every Metro-CONSTRUCTED archive node is `@SingleIn(ArchiveScope::class)`, so one
 * [ArchiveGraph] == one retained `ArchiveStoreImpl` ViewModel == one `NavBackStackEntry`
 * — exactly the lifetime `@ViewModelScoped` gave under Hilt. The graph is built inside the
 * `rememberMetroStoreProcessor` factory lambda (once per retained Store), so its scope key
 * bounds the feature-scoped instances to the Store's lifetime.
 */
internal abstract class ArchiveScope private constructor()
