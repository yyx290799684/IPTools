package com.yangyx.iptools.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_ips")
data class FavoriteIp(
    @PrimaryKey val ip: String,
    val title: String,
    val note: String = "",
    val addedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "history_records")
data class HistoryRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolType: String, // PING, TRACE, PORT_SCAN, FSCAN, IPERF, DNS, WHOIS, IP_GEO, SUBNET
    val target: String,
    val resultSummary: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface FavoriteIpDao {
    @Query("SELECT * FROM favorite_ips ORDER BY addedTimestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteIp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteIp)

    @Query("DELETE FROM favorite_ips WHERE ip = :ip")
    suspend fun deleteFavorite(ip: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_ips WHERE ip = :ip)")
    suspend fun isFavorite(ip: String): Boolean
}

@Dao
interface HistoryRecordDao {
    @Query("SELECT * FROM history_records ORDER BY timestamp DESC LIMIT 100")
    fun getRecentHistory(): Flow<List<HistoryRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(record: HistoryRecord)

    @Query("DELETE FROM history_records")
    suspend fun clearAllHistory()
}

@Database(entities = [FavoriteIp::class, HistoryRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteIpDao(): FavoriteIpDao
    abstract fun historyRecordDao(): HistoryRecordDao
}
