// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.di

/**
 * Metro feature-scope marker for feature/home — the Metro analogue of Hilt's `@ViewModelScoped`.
 * Every Metro-constructed node is `@SingleIn(HomeScope::class)`.
 */
internal abstract class HomeScope private constructor()
