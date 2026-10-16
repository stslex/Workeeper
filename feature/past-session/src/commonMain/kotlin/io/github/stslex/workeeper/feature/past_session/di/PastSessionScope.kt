// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.di

/**
 * Metro feature-scope marker for feature/past-session — the Metro analogue of Hilt's
 * `@ViewModelScoped`. Every Metro-constructed node is `@SingleIn(PastSessionScope::class)`.
 */
internal abstract class PastSessionScope private constructor()
