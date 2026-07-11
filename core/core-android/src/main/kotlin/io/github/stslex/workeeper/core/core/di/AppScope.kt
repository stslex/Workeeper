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
 * Lives in `core:core-android` (an ANDROID-only `com.android.library`), NOT `core:core`: `core:core`
 * is a live KMP module whose `commonMain` compiles to `iosSimulatorArm64`, so a marker there would leak
 * app-scope DI scaffolding (a Metro-on-Android axis concern) into the iOS binary. `core:core-android` is
 * Android-only and low in the module graph (depends only on `core:core`), so every bulk contributor can
 * depend on it without a cycle. It shares the package with the dispatcher-qualifier `@Provides` (this
 * `di` package already exists here via `CoreModule`), keeping the import path identical for consumers.
 * Kept `abstract class … private constructor()` (a plain marker, no Metro import) — the Metro annotation
 * lives on the contributing impls, not on this token.
 */
abstract class AppScope private constructor()
