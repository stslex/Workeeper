// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.tag

import androidx.compose.runtime.Stable

/**
 * One tag of the shared dictionary, as the kit's tag components consume it.
 *
 * Kit-owned because the tag picker is ONE kit component (ED7): a feature that embeds it
 * carries this model in its `State` rather than declaring a per-feature copy —
 * `BlockedArchiveItem` is the precedent for a kit-owned model carried in feature State.
 */
@Stable
data class AppTagItem(
    val uuid: String,
    val name: String,
)
