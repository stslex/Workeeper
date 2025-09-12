package io.github.stslex.workeeper.core.store.store

import kotlinx.coroutines.flow.Flow

interface CommonDataStore {

    val homeSelectedStartDate: Flow<Long?>

    val homeSelectedEndDate: Flow<Long?>

    suspend fun setHomeSelectedStartDate(value: Long)

    suspend fun setHomeSelectedEndDate(value: Long)

    companion object {

        internal const val NAME = "common_prefs"
    }
}
