package io.github.stslex.workeeper.core.data.database.common

import kotlinx.coroutines.CoroutineScope

interface DbTransitionRunner {

    suspend operator fun <T> invoke(block: suspend CoroutineScope.() -> T): T

    /** Runs a serialized write and notifies observers only after its transaction commits. */
    suspend fun <T> mutate(block: suspend CoroutineScope.() -> T): T

    /** Registers an app-scope observer for successful [mutate] commits. */
    fun addAfterMutationCommitListener(listener: () -> Unit)
}

/** Admission for the complete transaction, including post-commit listeners. */
interface DatabaseAdmission {
    suspend fun <T> use(block: suspend () -> T): T

    object Unrestricted : DatabaseAdmission {
        override suspend fun <T> use(block: suspend () -> T): T = block()
    }
}
