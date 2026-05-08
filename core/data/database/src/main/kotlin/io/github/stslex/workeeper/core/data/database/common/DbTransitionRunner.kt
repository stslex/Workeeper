package io.github.stslex.workeeper.core.data.database.common

import kotlinx.coroutines.CoroutineScope

interface DbTransitionRunner {

    suspend operator fun <T> invoke(block: suspend CoroutineScope.() -> T): T
}
