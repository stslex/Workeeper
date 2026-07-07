// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

/**
 * Metro feature-scope marker for feature/settings — the Metro analogue of Hilt's
 * `@ViewModelScoped`. Every Metro-constructed settings node is `@SingleIn(SettingsScope::class)`,
 * so one [SettingsGraph] == one retained `SettingsStoreImpl` ViewModel == one `NavBackStackEntry`.
 * Same token form as archive's `ArchiveScope`.
 */
internal abstract class SettingsScope private constructor()
