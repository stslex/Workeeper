// SPDX-License-Identifier: GPL-3.0-only
@file:Suppress("MagicNumber") // Fixed ProtoLayout dimensions for bounded round Tile content.

package io.github.stslex.workeeper.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders

internal object WorkoutTileLayout {
    const val LAUNCH_CLICK_ID = "open_controller"

    fun build(
        packageName: String,
        activityClassName: String,
        lines: List<String>,
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
            .setWidth(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        lines.take(MAX_LINES).forEachIndexed { index, line ->
            column.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(line)
                    .setMaxLines(if (index < 2) 2 else 1)
                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
                    .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(if (index == 0) 18f else 14f))
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
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setAll(DimensionBuilders.dp(18f))
                            .build(),
                    )
                    .build(),
            )
            .addContent(column.build())
            .build()
        return LayoutElementBuilders.Layout.Builder().setRoot(root).build()
    }

    private const val MAX_LINES = 4
}
