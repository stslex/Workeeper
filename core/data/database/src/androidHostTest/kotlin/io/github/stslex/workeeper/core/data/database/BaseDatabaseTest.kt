package io.github.stslex.workeeper.core.data.database

import android.app.Application
import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider

internal abstract class BaseDatabaseTest {

    protected lateinit var database: AppDatabase

    open fun initDb() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .allowMainThreadQueries()
            .build()
    }

    open fun clearDb() {
        database.close()
    }

    class TestApplication : Application()
}
