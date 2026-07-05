package io.github.stslex.workeeper.probe_room3

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
 * P2.c — same minimal schema as probe-room28 but on Room 3.0 (androidx.room3). Room 3 is
 * KSP-only + Coroutines-required (DAO fns must be suspend/Flow). Compile-only on android +
 * iosSimulatorArm64; the @ConstructedBy + expect RoomDatabaseConstructor forces Room 3's
 * KSP codegen onto K/N.
 */
@Entity(tableName = "probe")
data class ProbeEntity(
    @PrimaryKey val id: Long,
    val name: String,
)

@Dao
interface ProbeDao {

    @Insert
    suspend fun insert(entity: ProbeEntity)

    @Query("SELECT * FROM probe")
    suspend fun getAll(): List<ProbeEntity>
}

@Database(entities = [ProbeEntity::class], version = 1, exportSchema = false)
@ConstructedBy(ProbeDatabaseConstructor::class)
abstract class ProbeDatabase : RoomDatabase() {
    abstract fun probeDao(): ProbeDao
}

expect object ProbeDatabaseConstructor : RoomDatabaseConstructor<ProbeDatabase> {
    override fun initialize(): ProbeDatabase
}
