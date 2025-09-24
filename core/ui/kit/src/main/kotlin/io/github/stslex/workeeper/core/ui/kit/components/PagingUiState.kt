package io.github.stslex.workeeper.core.ui.kit.components

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow

@Immutable
fun interface PagingUiState<T : Any> {

    operator fun invoke(): Flow<T>
}
