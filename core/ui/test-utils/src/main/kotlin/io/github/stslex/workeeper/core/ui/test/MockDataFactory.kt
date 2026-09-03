package io.github.stslex.workeeper.core.ui.test

import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import kotlin.uuid.Uuid

/** Mock data for UI tests. */
object MockDataFactory {

    fun createUuid(): String = Uuid.random().toString()

    fun createUuids(count: Int): List<String> = List(count) { createUuid() }

    fun createDateProperty(
        timestamp: Long = System.currentTimeMillis(),
    ): PropertyHolder.DateProperty = PropertyHolder.DateProperty.new(timestamp)

    fun createTestNames(
        prefix: String,
        count: Int,
        startIndex: Int = 0,
    ): List<String> = List(count) { index -> "$prefix ${startIndex + index}" }
}
