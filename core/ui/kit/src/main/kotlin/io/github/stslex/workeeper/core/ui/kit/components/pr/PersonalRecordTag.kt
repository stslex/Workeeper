// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.pr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stslex.workeeper.core.ui.kit.components.setchip.CHIP_HEIGHT
import io.github.stslex.workeeper.core.ui.kit.components.setchip.CHIP_MIN_WIDTH
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode

/**
 * The set row's `.prtag` (extraction §1.6): the trailing chip a record row shows **instead
 * of** the type chip — same 34×32 slot geometry as `AppSetTypeChip`, never both.
 *
 * Outlined, not filled: 1px `molten.border` ring, molten text — distinct from
 * [PersonalRecordBadge], which is the solid pill. Type is `mono.caption` at **SemiBold** with
 * the mockup's `.1em` tracking — the first consumer of the bundled IBM Plex Mono 600, which
 * B2 shipped for exactly this selector ("174 608 bytes on account", `AppTypography`'s mono
 * KDoc).
 *
 * The label is the latin "PR" in every locale, like the badge.
 */
@Composable
fun PersonalRecordTag(
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AppDimension.Radius.small)
    Box(
        modifier = modifier
            .border(AppDimension.Border.small, AppUi.colors.record.border, shape)
            .height(CHIP_HEIGHT)
            .widthIn(min = CHIP_MIN_WIDTH)
            .clip(shape)
            .background(Color.Transparent)
            .padding(horizontal = AppDimension.Space.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = PR_TAG_LABEL,
            style = prTagTextStyle(),
            color = AppUi.colors.record.textPrimary,
        )
    }
}

/**
 * The tag's own intrinsic width: its label at its own style, plus its own padding.
 *
 * `CHIP_MIN_WIDTH` is a MINIMUM, and this label outgrows it — at fontScale 2.0 the tag
 * measures wider than the type chip it replaces. A trailing slot pinned to the minimum
 * therefore leaves a record row's fields narrower than its siblings' and than the header's
 * columns, which is why `SetRowGeometry.resolveTrailingSlotWidth` measures this rather than
 * assuming the minimum. Measured here, beside the label and style it measures, so the two
 * cannot drift.
 */
@Composable
internal fun personalRecordTagIntrinsicWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val label = measurer.measure(
        text = AnnotatedString(PR_TAG_LABEL),
        style = prTagTextStyle(),
        maxLines = 1,
        softWrap = false,
    )
    val labelWidth = with(LocalDensity.current) { label.size.width.toDp() }
    return labelWidth + AppDimension.Space.xs * 2
}

@Composable
private fun prTagTextStyle(): TextStyle = AppUi.typography.mono.caption.copy(
    fontWeight = FontWeight.SemiBold,
    letterSpacing = PR_TAG_TRACKING,
)

/** Latin in every locale, like the badge. */
private const val PR_TAG_LABEL = "PR"

/** `.prtag{letter-spacing:.1em}` at the 11sp caption rung. */
private val PR_TAG_TRACKING = 1.1.sp

@Preview
@Composable
private fun PersonalRecordTagPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.padding(16.dp)) {
            PersonalRecordTag()
        }
    }
}
