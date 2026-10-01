// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui.components

import androidx.compose.runtime.Composable
import androidx.paging.compose.LazyPagingItems
import io.github.stslex.workeeper.core.ui.kit.components.paging.rememberDeferredSurface

/**
 * The verdict archive draws: [archiveListSurface] behind the loading deferral. GUARD: call it once
 * in the body that owns both branches and pass the result down, or the deferral dies.
 */
@Composable
internal fun rememberArchiveSurface(items: LazyPagingItems<*>): ArchiveListSurface? =
    rememberDeferredSurface(
        surface = archiveListSurface(items.itemCount, items.loadState),
        loadingSurface = ArchiveListSurface.LOADING,
    )
