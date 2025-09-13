package io.github.stslex.workeeper.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.database.sets.SetsDao
import io.github.stslex.workeeper.core.database.sets.SetsEntity
import io.github.stslex.workeeper.core.database.sets.SetsTypeConverter

@Database(
    entities = [ExerciseEntity::class, SetsEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(
            from = 1,
            to = 2,
            spec = Migrations.OneToTwo::class
        )
    ]
)
@TypeConverters(UuidConverter::class, SetsTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract val exerciseDao: ExerciseDao

    abstract val setsDao: SetsDao

    companion object {

        const val NAME = "app.db"
    }
}

