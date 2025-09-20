package io.github.stslex.workeeper.core.core.utils

import kotlin.uuid.Uuid

object CommonExt {

    fun Uuid.Companion.parseOrRandom(
        uuidString: String?
    ): Uuid = uuidString
        ?.let(Uuid::parse)
        ?: Uuid.random()
}