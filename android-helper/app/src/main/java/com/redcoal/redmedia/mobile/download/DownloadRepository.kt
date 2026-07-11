package com.redcoal.redmedia.mobile.download

import android.content.Context
import androidx.core.net.toUri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.redcoal.redmedia.mobile.data.DownloadDao
import com.redcoal.redmedia.mobile.data.DownloadEntity
import com.redcoal.redmedia.mobile.data.DownloadStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.io.File

class DownloadRepository(
    private val context: Context,
    private val dao: DownloadDao,
) {
    val downloads: Flow<List<DownloadEntity>> = dao.observeAll()

    suspend fun enqueue(analysis: MediaAnalysis, format: FormatChoice): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsert(
            DownloadEntity(
                id = id,
                sourceUrl = analysis.sourceUrl,
                referer = analysis.referer,
                cookies = analysis.cookies,
                userAgent = analysis.userAgent,
                title = analysis.title,
                thumbnailUrl = analysis.thumbnailUrl,
                formatSelector = format.selector,
                formatLabel = format.label,
                audioOnly = format.audioOnly,
                status = DownloadStatus.QUEUED,
                progress = 0f,
                etaSeconds = 0,
                message = "Waiting to start",
                filePath = null,
                createdAt = now,
                updatedAt = now,
            )
        )
        enqueueWorker(id)
        return id
    }

    suspend fun pause(id: String) {
        dao.updateStatus(id, DownloadStatus.PAUSED, "Paused", System.currentTimeMillis())
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    suspend fun resume(id: String) {
        dao.updateStatus(id, DownloadStatus.QUEUED, "Resuming", System.currentTimeMillis())
        enqueueWorker(id)
    }

    suspend fun retry(id: String) {
        dao.updateStatus(id, DownloadStatus.QUEUED, "Retrying", System.currentTimeMillis())
        enqueueWorker(id)
    }

    suspend fun delete(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        dao.get(id)?.filePath?.let { location ->
            runCatching {
                if (location.startsWith("content://")) {
                    context.contentResolver.delete(location.toUri(), null, null)
                } else {
                    File(location).delete()
                }
            }
        }
        dao.delete(id)
    }

    private fun enqueueWorker(id: String) {
        val request = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
            .setInputData(workDataOf(MediaDownloadWorker.KEY_DOWNLOAD_ID to id))
            .addTag(workName(id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        fun workName(id: String) = "media-download-$id"
    }
}
