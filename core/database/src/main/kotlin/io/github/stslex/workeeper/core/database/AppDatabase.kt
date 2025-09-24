package io.github.stslex.workeeper.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.stslex.workeeper.core.database.converters.SetsTypeConverter
import io.github.stslex.workeeper.core.database.converters.StringConverter
import io.github.stslex.workeeper.core.database.converters.UuidConverter
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.database.training.TrainingDao
import io.github.stslex.workeeper.core.database.training.TrainingEntity
import io.github.stslex.workeeper.core.database.trainingLabels.TrainingLabelDao
import io.github.stslex.workeeper.core.database.trainingLabels.TrainingLabelEntity

@Database(
    entities = [ExerciseEntity::class, TrainingEntity::class, TrainingLabelEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(UuidConverter::class, SetsTypeConverter::class, StringConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract val exerciseDao: ExerciseDao

    abstract val trainingDao: TrainingDao

    abstract val labelsDao: TrainingLabelDao

    companion object {

        const val NAME = "app.db"
    }
}
