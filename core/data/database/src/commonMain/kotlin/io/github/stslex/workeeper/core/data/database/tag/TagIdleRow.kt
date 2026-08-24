// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.tag

import androidx.room3.ColumnInfo

/**
 * Projection for the start card's «Отставшие группы» readout (home-start-card.md §3.3):
 * one tag and the finish time of the last session in which any exercise carrying it was
 * performed. Tags that were never trained have no row — the metric is idleness since a
 * training that happened, and a tag with no history has no date to measure from.
 */
data class TagIdleRow(
    @ColumnInfo(name = "tag_name") val tagName: String,
    @ColumnInfo(name = "last_trained_at") val lastTrainedAt: Long,
)
