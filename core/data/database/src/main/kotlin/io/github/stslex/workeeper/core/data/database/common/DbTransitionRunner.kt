package io.github.stslex.workeeper.core.data.database.common

interface DbTransitionRunner {

    suspend operator fun <T> invoke(block: suspend () -> T): T
}
