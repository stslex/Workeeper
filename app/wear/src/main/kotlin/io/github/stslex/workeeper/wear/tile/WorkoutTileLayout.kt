// SPDX-License-Identifier: GPL-3.0-only
@file:Suppress("MagicNumber") // Fixed ProtoLayout dimensions for bounded round Tile content.

package io.github.stslex.workeeper.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import io.github.stslex.workeeper.core.ui.design.DARK_BODY
import io.github.stslex.workeeper.core.ui.design.DARK_MAX

internal object WorkoutTileLayout {
    const val LAUNCH_CLICK_ID = "open_controller"

    fun build(
        packageName: String,
        activityClassName: String,
        lines: List<String>,
        screenDiameterDp: Int = 192,
    ): LayoutElementBuilders.Layout {
        require(lines.isNotEmpty())
        val launch = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(activityClassName)
                    .build(),
            )
            .build()
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId(LAUNCH_CLICK_ID)
            .setMinimumClickableWidth(DimensionBuilders.dp(48f))
            .setMinimumClickableHeight(DimensionBuilders.dp(48f))
            .setOnClick(launch)
            .build()
        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.dp(screenDiameterDp * SAFE_SQUARE_FRACTION - 4f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        lines.take(MAX_LINES).forEachIndexed { index, line ->
            column.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(line)
                    .setMaxLines(if (index == 0) 1 else 2)
                    .setLineHeight(DimensionBuilders.sp(if (index == 0) 16f else 14f))
                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
                    .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(if (index == 0) 14f else 12f))
                            .setColor(
                                ColorBuilders.argb(
                                    when (index) {
                                        0 -> DARK_MAX
                                        lines.lastIndex -> DARK_MAX
                                        else -> DARK_BODY
                                    }.toInt(),
                                ),
                            )
                            .setWeight(
                                if (index == 0) {
                                    LayoutElementBuilders.FONT_WEIGHT_BOLD
                                } else {
                                    LayoutElementBuilders.FONT_WEIGHT_NORMAL
                                },
                            )
                            .build(),
                    )
                    .build(),
            )
        }
        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setSemantics(
                        ModifiersBuilders.Semantics.Builder()
                            .setContentDescription(lines.joinToString(separator = ". "))
                            .build(),
                    )
                    .setBackground(
                        ModifiersBuilders.Background.Builder().setColor(ColorBuilders.argb(0xFF000000.toInt())).build(),
                    )
                    .build(),
            )
            .addContent(column.build())
            .build()
        return LayoutElementBuilders.Layout.Builder().setRoot(root).build()
    }

    private const val MAX_LINES = 4
    private const val SAFE_SQUARE_FRACTION = 0.70710677f
}
