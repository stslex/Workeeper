package io.github.stslex.workeeper.core.data.database.converters

import androidx.room3.ColumnTypeConverter
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

internal object UuidConverter {

    @ColumnTypeConverter
    fun toString(value: Uuid?): String? = value?.toString()

    @ColumnTypeConverter
    fun toUuid(value: String): Uuid? = if (value.isEmpty()) {
        null
    } else {
        Uuid.Companion.parse(value)
    }

    @ColumnTypeConverter
    fun listUuidToString(value: List<Uuid>?): String = value
        .orEmpty()
        .map { it.toString() }
        .let {
            Json.encodeToString(it)
        }

    @ColumnTypeConverter
    fun stringToListUuids(value: String): List<Uuid> = Json
        .decodeFromString<List<String>>(value)
        .map { Uuid.parse(it) }
}
