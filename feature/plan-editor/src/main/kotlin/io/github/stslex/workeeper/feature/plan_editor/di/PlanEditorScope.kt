// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.di

/**
 * Metro feature-scope marker for feature/plan-editor — the Metro analogue of Hilt's
 * `@ViewModelScoped`. Every Metro-constructed node is `@SingleIn(PlanEditorScope::class)`.
 */
internal abstract class PlanEditorScope private constructor()
