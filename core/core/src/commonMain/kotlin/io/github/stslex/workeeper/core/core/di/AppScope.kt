// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.di

/**
 * Metro app-scope marker (App-Scope Collapse) — the Metro analogue of Hilt's `@Singleton` /
 * `SingletonComponent` tier. Mirrors the feature-scope token form
 * (`abstract class X private constructor()`, proven on Metro 1.1.1) used by every flipped
 * feature (e.g. `ArchiveScope`).
 *
 * A Metro-CONSTRUCTED app-scoped node annotated `@SingleIn(AppScope::class)` (or contributed via
 * `@ContributesBinding(AppScope::class)`) is owned by the single app-scope graph held on
 * `BaseApplication` for the whole process — the lifetime Hilt's `@Singleton` gave.
 *
 * Lives in `core:core` `commonMain` (the KMP shared surface), alongside the dispatcher qualifiers in
 * the same `di` package. It is an INERT token — a plain `abstract class … private constructor()` with no
 * Metro or Android import — so it compiles cleanly to every KMP target (incl. `iosSimulatorArm64`) and
 * leaks nothing: the Metro annotations that make it a real DI scope (`@DependencyGraph(AppScope::class)`,
 * `@SingleIn`/`@ContributesBinding(AppScope::class)`) live on the app graph and the contributing impls,
 * not on this token. Placing it here means a KMP feature that only needs the scope token for its
 * `@GraphExtension`/`@ContributesTo` no longer takes an Android-only `core:core-android` edge just to see
 * `AppScope`. (`core:core-android` still re-exposes it via `api(core:core)`, so existing consumers keep
 * the identical import path with no change.)
 */
abstract class AppScope private constructor()
