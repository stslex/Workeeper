// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.di

/**
 * Metro feature-scope marker for feature/archive, the analogue of Hilt's `@ViewModelScoped`. Every
 * archive node except the deliberately unscoped Store is `@SingleIn(ArchiveScope::class)`.
 */
internal abstract class ArchiveScope private constructor()
