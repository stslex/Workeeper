package io.github.stslex.workeeper.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity

@Database(
    entities = [ExerciseEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract val exerciseDao: ExerciseDao

    companion object {

        const val NAME = "app.db"
    }
}