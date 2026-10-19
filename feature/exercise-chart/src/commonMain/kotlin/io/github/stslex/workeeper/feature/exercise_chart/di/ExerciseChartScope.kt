// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.di

/**
 * Metro feature-scope marker for feature/exercise-chart — the Metro analogue of Hilt's
 * `@ViewModelScoped`. Every Metro-constructed node is `@SingleIn(ExerciseChartScope::class)`.
 */
internal abstract class ExerciseChartScope private constructor()
