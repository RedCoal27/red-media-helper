package com.redcoal.redmedia.mobile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun get(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Query(
        """UPDATE downloads SET status = :status, progress = :progress,
        etaSeconds = :etaSeconds, message = :message, filePath = :filePath,
        updatedAt = :updatedAt WHERE id = :id"""
    )
    suspend fun updateProgress(
        id: String,
        status: String,
        progress: Float,
        etaSeconds: Long,
        message: String,
        filePath: String?,
        updatedAt: Long,
    )

    @Query("UPDATE downloads SET status = :status, message = :message, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, message: String, updatedAt: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)
}
