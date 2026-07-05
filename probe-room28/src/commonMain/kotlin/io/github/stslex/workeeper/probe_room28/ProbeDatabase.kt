package io.github.stslex.workeeper.probe_room28

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/**
 * P2.c — minimal Room-KMP 2.8.x schema in commonMain: 1 @Entity + 1 @Dao + 1 @Database.
 * Compile-only (no query execution). The @ConstructedBy + `expect object ... :
 * RoomDatabaseConstructor` is the KMP-specific construct that forces Room's KSP codegen
 * to generate a platform actual for iosSimulatorArm64 — the exact thing this probe tests.
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

// Room's KSP generates the `actual object` for each target (JVM/Android + K/N).
expect object ProbeDatabaseConstructor : RoomDatabaseConstructor<ProbeDatabase> {
    override fun initialize(): ProbeDatabase
}
