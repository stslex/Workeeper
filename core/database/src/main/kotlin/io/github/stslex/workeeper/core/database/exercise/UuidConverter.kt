package io.github.stslex.workeeper.core.database.exercise

import androidx.room.TypeConverter
import kotlin.uuid.Uuid

internal object UuidConverter {

    @TypeConverter
    fun toString(value: Uuid?): String = value?.toString() ?: Uuid.random().toString()

    @TypeConverter
    fun toUuid(value: String): Uuid = Uuid.parse(value)
}