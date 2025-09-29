package io.github.stslex.workeeper.core.ui.kit.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

enum class ItemPosition {
    FIRST, MIDDLE, LAST, SINGLE;

    companion object {

        @Composable
        fun getItemPosition(
            index: Int,
            itemsCount: Int,
        ): ItemPosition = remember(index, itemsCount) {
            when {
                itemsCount == 1 -> SINGLE
                index == 0 -> FIRST
                index == itemsCount.dec() -> LAST
                else -> MIDDLE
            }
        }
    }
}
