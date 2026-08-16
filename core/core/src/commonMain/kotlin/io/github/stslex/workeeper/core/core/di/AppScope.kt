// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.di

/**
 * Metro app-scope marker (App-Scope Collapse) — the Metro analogue of Hilt's `@Singleton` /
 * `SingletonComponent` tier. Mirrors the feature-scope token form
 * (`abstract class X private constructor()`) used by every flipped feature — e.g. `ArchiveScope` in
 * `feature/archive/src/main/kotlin/io/github/stslex/workeeper/feature/archive/di/ArchiveScope.kt`.
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
 *
 * Placement rule for contribution SITES, corrected by measurement in KMP phase 6. The rule this
 * KDoc previously stated — that every `@ContributesBinding(AppScope::class)` /
 * `@ContributesTo(AppScope::class)` site must live in an Android-compiled source set, never
 * `commonMain` — is **not true**, and its stated reason (that `core:core` does not apply the Metro
 * compiler plugin) had already stopped being true when phase 3 added
 * `alias(libs.plugins.metro)` to `core/core/build.gradle.kts`.
 *
 * **Measured:** a `@ContributesBinding(AppScope::class)` declared in the `commonMain` of a KMP
 * module that applies the Metro plugin DOES aggregate into `:app:app`'s `@DependencyGraph`.
 * `CommonDataStoreImpl` (`core:data:dataStore` `commonMain`) is bound that way and
 * `:app:app:assembleDebug` resolves it; deleting the annotation reds the build with
 * `[Metro/MissingBinding] No binding found for CommonDataStore`, so the resolution is real and not a
 * green over an unrequested binding. The mechanism is simply that `commonMain` sources are part of
 * the Android compilation, which is where the Metro plugin runs.
 *
 * What IS still required: the module must apply the Metro plugin, and a contribution compiled into a
 * target with no graph (`iosSimulatorArm64` today) is inert rather than an error. A site in
 * `iosMain` remains pointless while the iOS composition root does not exist.
 */
abstract class AppScope private constructor()
