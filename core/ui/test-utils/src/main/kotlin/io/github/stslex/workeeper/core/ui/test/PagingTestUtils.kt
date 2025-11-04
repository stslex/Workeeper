package io.github.stslex.workeeper.core.ui.test

import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Utilities for testing Paging components
 */
object PagingTestUtils {

    /**
     * Creates a simple PagingData flow from a list
     */
    fun <T : Any> createPagingFlow(items: List<T>): Flow<PagingData<T>> {
        return flowOf(PagingData.from(items))
    }

    /**
     * Creates an empty PagingData flow
     */
    fun <T : Any> createEmptyPagingFlow(): Flow<PagingData<T>> {
        return flowOf(PagingData.from(emptyList()))
    }

    /**
     * Creates a test PagingSource that can simulate errors
     */
    class TestPagingSource<T : Any>(
        private val items: List<T>,
        private val shouldFail: Boolean = false,
        private val errorMessage: String = "Test error",
    ) : PagingSource<Int, T>() {

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
            if (shouldFail) {
                return LoadResult.Error(Exception(errorMessage))
            }

            val page = params.key ?: 0
            val pageSize = params.loadSize
            val startIndex = page * pageSize
            val endIndex = minOf(startIndex + pageSize, items.size)

            if (startIndex >= items.size) {
                return LoadResult.Page(
                    data = emptyList(),
                    prevKey = if (page > 0) page - 1 else null,
                    nextKey = null,
                )
            }

            return LoadResult.Page(
                data = items.subList(startIndex, endIndex),
                prevKey = if (page > 0) page - 1 else null,
                nextKey = if (endIndex < items.size) page + 1 else null,
            )
        }

        override fun getRefreshKey(state: PagingState<Int, T>): Int? {
            return state.anchorPosition?.let { anchorPosition ->
                state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                    ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
            }
        }
    }

    /**
     * Creates a PagingData flow that simulates loading error
     */
    fun <T : Any> createErrorPagingFlow(): Flow<PagingData<T>> {
        return flowOf(PagingData.empty())
    }
}
