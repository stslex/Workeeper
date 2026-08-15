// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.probe.data

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

/**
 * P7 subject: the smallest schema that forces Room 3's KSP codegen on every declared target
 * (kspAndroid + kspIosSimulatorArm64). The pass criterion is COUNTED generated files per
 * target, never the task exit code — KSP 2.3.6 has already false-greened this repo once by
 * silently skipping KMP codegen.
 */
@Entity
data class ProbeEntity(
    @PrimaryKey val id: Long,
    val name: String,
)

@Dao
interface ProbeDao {

    @Insert
    suspend fun insert(entity: ProbeEntity)

    @Query("SELECT * FROM ProbeEntity")
    suspend fun getAll(): List<ProbeEntity>
}

@Database(entities = [ProbeEntity::class], version = 1)
@ConstructedBy(ProbeDatabaseConstructor::class)
abstract class ProbeDatabase : RoomDatabase() {

    abstract fun probeDao(): ProbeDao
}

// The generated actuals come from Room's KSP step per target.
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object ProbeDatabaseConstructor : RoomDatabaseConstructor<ProbeDatabase> {
    override fun initialize(): ProbeDatabase
}
