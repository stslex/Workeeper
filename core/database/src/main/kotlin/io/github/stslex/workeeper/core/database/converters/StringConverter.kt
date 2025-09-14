package io.github.stslex.workeeper.core.database.converters

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

internal object StringConverter {

    @TypeConverter
    fun listToString(value: List<String>?): String = value
        .orEmpty()
        .let {
            Json.Default.encodeToString(it)
        }

    @TypeConverter
    fun stringToListStrings(value: String): List<String> = Json.Default
        .decodeFromString<List<String>>(value)
}