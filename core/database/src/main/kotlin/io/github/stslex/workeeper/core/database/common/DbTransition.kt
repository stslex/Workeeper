package io.github.stslex.workeeper.core.database.common

interface DbTransition {

    suspend operator fun <T> invoke(block: suspend () -> T): T
}
