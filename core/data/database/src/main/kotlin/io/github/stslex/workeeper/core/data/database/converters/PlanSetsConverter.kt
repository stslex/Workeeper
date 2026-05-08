// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.converters

import androidx.room.TypeConverter
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import kotlinx.serialization.json.Json

object PlanSetsConverter {

    private val json by lazy {
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }

    @TypeConverter
    fun toJson(value: List<PlanSetDataModel>?): String? =
        value?.let { json.encodeToString(it) }

    @TypeConverter
    fun fromJson(value: String?): List<PlanSetDataModel>? =
        value?.let { json.decodeFromString(it) }
}
