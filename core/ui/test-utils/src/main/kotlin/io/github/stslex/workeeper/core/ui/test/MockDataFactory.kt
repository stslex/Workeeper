package io.github.stslex.workeeper.core.ui.test

import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import kotlin.uuid.Uuid

/**
 * Factory for creating mock data for UI tests
 */
object MockDataFactory {

    /**
     * Creates a mock UUID
     */
    fun createUuid(): String = Uuid.random().toString()

    /**
     * Creates mock UUIDs
     */
    fun createUuids(count: Int): List<String> = List(count) { createUuid() }

    /**
     * Creates a mock date property with current timestamp
     */
    fun createDateProperty(
        timestamp: Long = System.currentTimeMillis(),
    ): PropertyHolder.DateProperty = PropertyHolder.DateProperty.new(timestamp)

    /**
     * Creates a list of test names
     */
    fun createTestNames(
        prefix: String,
        count: Int,
        startIndex: Int = 0,
    ): List<String> = List(count) { index -> "$prefix ${startIndex + index}" }
}
