// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

/**
 * Metro app-scope marker (KMP C.1 app-collapse Phase 1) — the Metro analogue of Hilt's
 * `@Singleton` / `SingletonComponent` tier. Mirrors the feature-scope token form
 * (`abstract class X private constructor()`, proven on Metro 1.1.1) used by every flipped
 * feature (e.g. `ArchiveScope`).
 *
 * A Metro-CONSTRUCTED app-scoped node annotated `@SingleIn(AppScope::class)` is owned by the
 * single [AppGraph] instance held on `BaseApplication` for the whole process — the lifetime
 * Hilt's `@Singleton` gave. This leaf spike owns exactly one such node (`AnalyticsHolder`); the
 * bulk migration extends the same graph.
 */
internal abstract class AppScope private constructor()
