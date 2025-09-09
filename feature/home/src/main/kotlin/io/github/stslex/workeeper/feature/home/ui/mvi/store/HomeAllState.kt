package io.github.stslex.workeeper.feature.home.ui.mvi.store

import androidx.compose.runtime.Stable
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.home.ui.model.ExerciseUiModel
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

@Stable
data class HomeAllState(
    val items: PagingUiState<PagingData<ExerciseUiModel>>,
    val selectedItems: ImmutableSet<ExerciseUiModel>,
    val query: String,
) {

    companion object {

        fun init(
            allItems: PagingUiState<PagingData<ExerciseUiModel>>
        ) = HomeAllState(
            items = allItems,
            selectedItems = persistentSetOf(),
            query = "",
        )
    }
}