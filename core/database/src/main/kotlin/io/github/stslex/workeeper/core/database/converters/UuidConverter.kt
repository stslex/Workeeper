package io.github.stslex.workeeper.core.database.converters

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

internal object UuidConverter {

    @TypeConverter
    fun toString(value: Uuid?): String? = value?.toString()

    @TypeConverter
    fun toUuid(value: String): Uuid? = if (value.isEmpty()) {
        null
    } else {
        Uuid.Companion.parse(value)
    }

    @TypeConverter
    fun listUuidToString(value: List<Uuid>?): String = value
        .orEmpty()
        .map { it.toString() }
        .let {
            Json.encodeToString(it)
        }

    @TypeConverter
    fun stringToListUuids(value: String): List<Uuid> = Json
        .decodeFromString<List<String>>(value)
        .map { Uuid.parse(it) }
}

