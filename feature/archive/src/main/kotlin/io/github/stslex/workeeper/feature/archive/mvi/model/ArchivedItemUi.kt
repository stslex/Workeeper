// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.feature.archive.domain.model.ArchivedItem

/**
 * One archived row, with its meta line **already composed**.
 *
 * The sibling screens compose their meta line in the row composable, off `stringResource`. This
 * screen composes it in `ArchiveUiMapper` instead, and the difference is not stylistic: archive
 * already had a `ResourceWrapper`-backed mapper, and CLAUDE.md allows either — but only the mapper
 * form can be asserted without a composition, and the kind token is the one thing on this row that
 * a picture cannot check (`ArchiveMetaLineTest`).
 *
 * §26 "Meta-line order": `kind · archived-since · tags`, one line, no wrap, tags last because the
 * tail is what truncates.
 */
@Stable
sealed interface ArchivedItemUi {

    val item: ArchivedItem

    /** `kind · archived-since · tags`, pre-joined. See `ArchiveUiMapper.composeMetaLine`. */
    val metaLine: String

    @Stable
    data class Exercise(
        override val item: ArchivedItem.Exercise,
        override val metaLine: String,
    ) : ArchivedItemUi

    @Stable
    data class Training(
        override val item: ArchivedItem.Training,
        override val metaLine: String,
    ) : ArchivedItemUi
}
