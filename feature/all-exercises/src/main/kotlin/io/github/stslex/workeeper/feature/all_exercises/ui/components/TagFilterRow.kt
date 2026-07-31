// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagChip
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.TagUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * The tag filter band — `pass2d.html` `#s-list` `.tagrow`, above the list.
 *
 * §26 "Tag filter band". The band is what lets the row stop enumerating tags and start confirming
 * them: the full set lives here, so the meta line can carry the tail and truncate it.
 *
 * The band's own measures (padding, spacing and the closing rule) are feature-owned and match the
 * sibling exactly — the two screens draw one band. The **chip** inside it is `AppTagChip`'s, and its
 * treatment is not this screen's to change: B20 records that its selected and unselected states
 * resolve to the same fill in both themes, which is every `AppTagChip.Selectable` in the app and a
 * kit pass.
 *
 * Multi-select is deliberate and diverges from the drawing's grammar: `.tag` is drawn single-select
 * (`pickTag` is a radio group in the chart) and that grammar does **not** transfer — D1 (a).
 */
@Composable
internal fun TagFilterRow(
    tags: ImmutableList<TagUiModel>,
    activeTagFilter: ImmutableSet<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        LazyRow(
            modifier = Modifier.testTag("AllExercisesTagFilter"),
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
            contentPadding = PaddingValues(
                horizontal = AppDimension.screenEdge,
                vertical = AppDimension.Space.md,
            ),
        ) {
            items(items = tags, key = { it.uuid }) { tag ->
                AppTagChip.Selectable(
                    modifier = Modifier.testTag("AllExercisesTagFilter_${tag.uuid}"),
                    label = tag.name,
                    selected = tag.uuid in activeTagFilter,
                    onSelectedChange = { onToggle(tag.uuid) },
                )
            }
        }
        // The drawn band closes with a rule — the same hairline grammar `.bulk`, `.perr` and
        // `.nb.track` use to separate one band from the next. Without it the chips and the first
        // row read as one block.
        HorizontalDivider(
            thickness = AppDimension.borderHairline,
            color = AppUi.colors.borderSubtle,
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun TagFilterRowPreview() {
    AppTheme {
        TagFilterRow(
            tags = persistentListOf(
                TagUiModel("1", "Push"),
                TagUiModel("2", "Pull"),
                TagUiModel("3", "Legs"),
            ),
            activeTagFilter = persistentSetOf("1"),
            onToggle = {},
        )
    }
}
