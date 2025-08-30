package io.github.stslex.workeeper.core.core.coroutine.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.MainCoroutineDispatcher

interface AppDispatcher {

    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
    val main: MainCoroutineDispatcher
    val immediate: MainCoroutineDispatcher
}
