// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.di

/**
 * Metro feature-scope marker for feature/all-trainings — the Metro analogue of Hilt's
 * `@ViewModelScoped`. Every Metro-constructed node is `@SingleIn(AllTrainingsScope::class)`.
 */
internal abstract class AllTrainingsScope private constructor()
