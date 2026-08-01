// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui.components

import androidx.compose.runtime.Composable
import androidx.paging.compose.LazyPagingItems
import io.github.stslex.workeeper.core.ui.kit.components.paging.rememberDeferredSurface

/**
 * Which of an archive tab's two bodies is on screen.
 *
 * Archive is the one screen in this arc that **swaps** its body rather than layering an empty
 * region over a list: `LazyColumn` or region, never both. That swap is a second gate above the
 * region's own `when`, and it is the gate that decides whether the deferral is in composition at
 * all — so it must read the DEFERRED verdict, from [rememberArchiveSurface], and never
 * [archiveListSurface] a second time.
 *
 * `null` belongs with [REGION] and that is the row worth stating: during the deferral window the
 * region draws nothing, which is the point — the outgoing frame persists. Sending `null` to
 * [LIST] instead would draw an empty `LazyColumn` for the first 140 ms of every cold open, which
 * is the blank frame B22 is about, arriving by a new route.
 */
internal enum class ArchiveBody {
    /** Rows. */
    LIST,

    /** The region: spinner, error or empty state — or, in the deferral window, nothing. */
    REGION,
}

/** Pure, so the swap above the region is assertable. See `ArchiveBodyTest`. */
internal fun archiveBody(surface: ArchiveListSurface?): ArchiveBody =
    if (surface == ArchiveListSurface.CONTENT) ArchiveBody.LIST else ArchiveBody.REGION

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
