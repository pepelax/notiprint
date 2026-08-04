package ru.notiprint.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedSenderDao {
    @Query("SELECT * FROM blocked_senders ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<BlockedSender>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<BlockedSender>): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_senders WHERE normalized = :normalized)")
    suspend fun isBlocked(normalized: String): Boolean

    @Delete
    suspend fun delete(entry: BlockedSender)
}
