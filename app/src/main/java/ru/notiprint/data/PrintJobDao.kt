package ru.notiprint.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PrintJobDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(job: PrintJob): Long

    @Query("SELECT * FROM print_jobs ORDER BY receivedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PrintJob>>

    @Query("SELECT COUNT(*) FROM print_jobs WHERE status IN ('PENDING', 'RETRY', 'PRINTING')")
    fun observeWaitingCount(): Flow<Int>

    @Query("SELECT * FROM print_jobs WHERE status IN ('PENDING', 'RETRY') ORDER BY receivedAt ASC LIMIT :limit")
    suspend fun nextJobs(limit: Int): List<PrintJob>

    @Query("UPDATE print_jobs SET status = 'PENDING' WHERE status = 'PRINTING'")
    suspend fun recoverInterruptedPrints()

    @Query("UPDATE print_jobs SET status = 'PRINTING', lastError = NULL WHERE id = :id")
    suspend fun markPrinting(id: Long)

    @Query("UPDATE print_jobs SET status = 'PRINTED', lastError = NULL WHERE id = :id")
    suspend fun markPrinted(id: Long)

    @Query("UPDATE print_jobs SET status = 'RETRY', attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun markForRetry(id: Long, error: String)
}
