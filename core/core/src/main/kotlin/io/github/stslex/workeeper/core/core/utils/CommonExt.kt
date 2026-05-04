package io.github.stslex.workeeper.core.core.utils

import kotlin.uuid.Uuid

object CommonExt {

    fun Uuid.Companion.parseOrRandom(
        uuidString: String?,
    ): Uuid = uuidString
        ?.let(Uuid::parse)
        ?: Uuid.random()

    inline fun <T> runIf(condition: Boolean, block: () -> T): T? = if (condition) block() else null

    inline fun <T, R> runIfNotNull(
        value: T?,
        block: (T) -> R,
    ): R? = if (value != null) block(value) else null
}
