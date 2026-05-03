// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.converters

import androidx.room.TypeConverter
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import kotlinx.serialization.json.Json

object PlanSetsConverter {

    @TypeConverter
    fun toJson(value: List<PlanSetDataModel>?): String? =
        value?.let { Json.encodeToString(it) }

    @TypeConverter
    fun fromJson(value: String?): List<PlanSetDataModel>? =
        value?.let { Json.decodeFromString(it) }
}
