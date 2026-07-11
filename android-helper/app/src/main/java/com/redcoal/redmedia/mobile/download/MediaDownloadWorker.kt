package com.redcoal.redmedia.mobile.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.redcoal.redmedia.mobile.R
import com.redcoal.redmedia.mobile.RedMediaApp
import com.redcoal.redmedia.mobile.data.DownloadStatus
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File

class MediaDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val dao = (appContext as RedMediaApp).database.downloadDao()
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private var lastUpdateAt = 0L

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
        val download = dao.get(id) ?: return@withContext Result.failure()
        createNotificationChannel()
        setForeground(createForegroundInfo(download.title, download.progress.toInt(), true))
        dao.updateStatus(id, DownloadStatus.RUNNING, "Preparing download", System.currentTimeMillis())

        try {
            MediaEngine.initialize(applicationContext)
            val outputDirectory = File(
                applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: applicationContext.filesDir,
                "Red Media/$id",
            ).apply { mkdirs() }

            val request = YoutubeDLRequest(download.sourceUrl)
                .addOption("--no-playlist")
                .addOption("--continue")
                .addOption("--newline")
                .addOption("--concurrent-fragments", 6)
                .addOption("--no-mtime")
                .addOption("-f", download.formatSelector)
                .addOption("-o", File(outputDirectory, "%(title).180B [%(id)s].%(ext)s").absolutePath)

            download.referer?.takeIf { it.isNotBlank() }?.let {
                request.addOption("--referer", it)
            }
            download.cookies?.takeIf { it.isNotBlank() }?.let {
                request.addOption("--add-header", "Cookie:$it")
            }
            download.userAgent?.takeIf { it.isNotBlank() }?.let {
                request.addOption("--user-agent", it)
            }

            if (download.audioOnly) {
                request
                    .addOption("--extract-audio")
                    .addOption("--audio-format", "mp3")
                    .addOption("--audio-quality", "0")
            } else {
                request
                    .addOption("--audio-multistreams")
                    .addOption("--merge-output-format", "mkv")
            }

            var finalPath: String? = download.filePath
            runInterruptible(Dispatchers.IO) {
                YoutubeDL.getInstance().execute(request, id) { progress, etaSeconds, line ->
                    val now = System.currentTimeMillis()
                    val detectedPath = extractDestination(line)
                    if (detectedPath != null) finalPath = detectedPath
                    val isProcessing = line.contains("Merging formats", true) ||
                        line.contains("Post-process", true) ||
                        line.contains("Remuxing", true) ||
                        line.contains("ExtractAudio", true)

                    if (now - lastUpdateAt >= 450 || progress >= 100f || detectedPath != null) {
                        lastUpdateAt = now
                        val message = humanizeLine(line)
                        runBlocking(Dispatchers.IO) {
                            dao.updateProgress(
                                id = id,
                                status = if (isProcessing) DownloadStatus.PROCESSING else DownloadStatus.RUNNING,
                                progress = if (isProcessing) 95f else progress.coerceIn(0f, 94f),
                                etaSeconds = etaSeconds,
                                message = message,
                                filePath = finalPath,
                                updatedAt = now,
                            )
                        }
                        showProgressNotification(download.title, progress.toInt(), message)
                    }
                }
            }

            val generatedFile = finalPath
                ?.let(::File)
                ?.takeIf(File::isFile)
                ?: outputDirectory.walkTopDown()
                    .filter(File::isFile)
                    .maxByOrNull(File::lastModified)
            dao.updateProgress(
                id = id,
                status = DownloadStatus.PROCESSING,
                progress = 98f,
                etaSeconds = 0,
                message = "Saving to Downloads",
                filePath = generatedFile?.absolutePath ?: finalPath,
                updatedAt = System.currentTimeMillis(),
            )
            val publishedPath = generatedFile?.let(::publishToDownloads) ?: finalPath

            dao.updateProgress(
                id = id,
                status = DownloadStatus.COMPLETE,
                progress = 100f,
                etaSeconds = 0,
                message = "Download complete",
                filePath = publishedPath,
                updatedAt = System.currentTimeMillis(),
            )
            showCompleteNotification(download.title)
            Result.success()
        } catch (_: YoutubeDL.CanceledException) {
            val current = dao.get(id)
            if (current?.status != DownloadStatus.PAUSED) {
                dao.updateStatus(id, DownloadStatus.PAUSED, "Paused", System.currentTimeMillis())
            }
            Result.success()
        } catch (error: CancellationException) {
            dao.updateStatus(id, DownloadStatus.PAUSED, "Paused", System.currentTimeMillis())
            throw error
        } catch (error: Exception) {
            dao.updateStatus(
                id,
                DownloadStatus.FAILED,
                error.message?.lineSequence()?.lastOrNull()?.take(240) ?: "Download failed",
                System.currentTimeMillis(),
            )
            showFailedNotification(download.title)
            Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = applicationContext.getString(R.string.download_channel_description)
            }
        )
    }

    private fun createForegroundInfo(title: String, progress: Int, indeterminate: Boolean): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText("Preparing download")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress.coerceIn(0, 100), indeterminate)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun showProgressNotification(title: String, progress: Int, message: String) {
        notifySafely(
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(title)
                .setContentText(message)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
                .build()
        )
    }

    private fun showCompleteNotification(title: String) {
        notifySafely(
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(title)
                .setContentText("Saved to Download/Red Media")
                .setAutoCancel(true)
                .build()
        )
    }

    private fun showFailedNotification(title: String) {
        notifySafely(
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(title)
                .setContentText("Download failed")
                .setAutoCancel(true)
                .build()
        )
    }

    @SuppressLint("MissingPermission")
    private fun notifySafely(notification: Notification) {
        if (!canPostNotifications()) return
        try {
            notificationManager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Notification permission may be revoked while a download is active.
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    private val notificationId: Int
        get() = (inputData.getString(KEY_DOWNLOAD_ID)?.hashCode() ?: id.hashCode()) and 0x7fffffff

    private fun publishToDownloads(source: File): String {
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(source.extension.lowercase())
            ?: "application/octet-stream"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, source.name)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Red Media")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = applicationContext.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Unable to create the destination file")

            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("Unable to open the destination file")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            source.delete()
            return uri.toString()
        }

        val destinationDirectory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Red Media",
        ).apply { mkdirs() }
        val destination = File(destinationDirectory, source.name)
        source.copyTo(destination, overwrite = true)
        source.delete()
        MediaScannerConnection.scanFile(
            applicationContext,
            arrayOf(destination.absolutePath),
            arrayOf(mimeType),
            null,
        )
        return destination.absolutePath
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        private const val CHANNEL_ID = "media_downloads"

        private fun extractDestination(line: String): String? {
            val patterns = listOf("Destination:", "Merging formats into", "Remuxing video from")
            val marker = patterns.firstOrNull(line::contains) ?: return null
            return line.substringAfter(marker).trim().trim('"', '\'').takeIf { it.isNotBlank() }
        }

        private fun humanizeLine(line: String): String {
            val compact = line.trim().replace(Regex("\\s+"), " ")
            return when {
                "Merging formats" in compact -> "Merging video and audio tracks"
                "Remuxing" in compact -> "Finalizing media container"
                "ExtractAudio" in compact -> "Converting audio"
                "Destination:" in compact -> "Downloading"
                compact.isBlank() -> "Downloading"
                else -> compact.take(180)
            }
        }
    }
}
