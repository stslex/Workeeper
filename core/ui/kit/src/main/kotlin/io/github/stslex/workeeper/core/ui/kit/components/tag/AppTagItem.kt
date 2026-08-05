// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.tag

import androidx.compose.runtime.Stable

/**
 * One tag of the shared dictionary, as the kit's tag components consume it.
 *
 * This is the model both feature editors used to duplicate as their own `TagUiModel`
 * (`v3-editors.md` §2, "one of the two internal TagPickerInline copies dies"): the picker is
 * ONE kit component now (ED7), so the two byte-identical models collapse onto its own.
 * `BlockedArchiveItem` is the precedent for a kit-owned model carried in feature State.
 */
@Stable
data class AppTagItem(
    val uuid: String,
    val name: String,
)
