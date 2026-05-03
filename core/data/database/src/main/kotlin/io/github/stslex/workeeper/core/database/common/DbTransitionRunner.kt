package io.github.stslex.workeeper.core.database.common

interface DbTransitionRunner {

    suspend operator fun <T> invoke(block: suspend () -> T): T
}
