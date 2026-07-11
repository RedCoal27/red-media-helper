package com.redcoal.redmedia.mobile.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val sourceUrl: String,
    val referer: String?,
    val cookies: String?,
    val userAgent: String?,
    val title: String,
    val thumbnailUrl: String?,
    val formatSelector: String,
    val formatLabel: String,
    val audioOnly: Boolean,
    val status: String,
    val progress: Float,
    val etaSeconds: Long,
    val message: String,
    val filePath: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

object DownloadStatus {
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val PAUSED = "paused"
    const val COMPLETE = "complete"
    const val FAILED = "failed"
}
