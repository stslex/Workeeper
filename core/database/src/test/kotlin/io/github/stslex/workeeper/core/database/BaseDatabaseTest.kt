package io.github.stslex.workeeper.core.database

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

internal abstract class BaseDatabaseTest {

    protected lateinit var database: AppDatabase

    open fun initDb() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    open fun clearDb() {
        database.close()
    }

    class TestApplication : Application()

}