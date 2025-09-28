package io.github.stslex.workeeper.core.ui.kit.utils

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.createListShapeWithPadding(
    shape: CornerBasedShape,
    itemsPadding: Dp,
    index: Int,
    itemsCount: Int
): Modifier = this
    .clip(
        shape = when {
            itemsCount == 1 -> shape
            index == 0 -> RoundedCornerShape(
                topStart = shape.topStart,
                topEnd = shape.topEnd,
                bottomStart = CornerSize(0.dp),
                bottomEnd = CornerSize(0.dp),
            )

            index == itemsCount.dec() -> RoundedCornerShape(
                topStart = CornerSize(0.dp),
                topEnd = CornerSize(0.dp),
                bottomStart = shape.bottomStart,
                bottomEnd = shape.bottomEnd,
            )

            else -> RoundedCornerShape(0.dp)
        }
    )
    .padding(
        bottom = if (index == itemsCount.dec()) {
            0.dp
        } else {
            itemsPadding
        }
    )