// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui.components

import androidx.compose.runtime.Composable
import androidx.paging.compose.LazyPagingItems
import io.github.stslex.workeeper.core.ui.kit.components.paging.rememberDeferredSurface

/**
 * The verdict archive DRAWS — [archiveListSurface] behind the loading deferral.
 *
 * One entry point, because the defect it replaces was two readings of the same data: the body swap
 * read the raw verdict while the region read the deferred one, so the minimum hold ran inside a
 * composable the raw verdict had already removed. Call this **once**, in the body that owns both
 * branches, and pass the result down.
 */
@Composable
internal fun rememberArchiveSurface(items: LazyPagingItems<*>): ArchiveListSurface? =
    rememberDeferredSurface(
        surface = archiveListSurface(items.itemCount, items.loadState),
        loadingSurface = ArchiveListSurface.LOADING,
    )
