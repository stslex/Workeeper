package io.github.stslex.workeeper.core.database

import androidx.room.Room
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.core.scope.Scope

@Module
@ComponentScan("io.github.stslex.workeeper.core.database")
class ModuleCoreDatabase {

    @Single
    fun createAppDataBase(scope: Scope): AppDatabase = Room.databaseBuilder(
        scope.androidContext(),
        AppDatabase::class.java,
        AppDatabase.NAME
    )
        .build()

    @Single
    fun createExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao
}
