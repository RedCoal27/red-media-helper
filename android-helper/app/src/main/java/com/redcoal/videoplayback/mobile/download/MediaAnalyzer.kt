package com.redcoal.videoplayback.mobile.download

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class FormatChoice(
    val id: String,
    val label: String,
    val detail: String,
    val selector: String,
    val audioOnly: Boolean = false,
    val estimatedBytes: Long = 0,
)

data class MediaAnalysis(
    val sourceUrl: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    val formats: List<FormatChoice>,
    val referer: String? = null,
    val cookies: String? = null,
    val userAgent: String? = null,
)

data class BrowserRequestContext(
    val pageUrl: String,
    val referer: String = pageUrl,
    val cookies: String? = null,
    val userAgent: String? = null,
)

object MediaEngine {
    private val initializationMutex = Mutex()
    @Volatile private var initialized = false

    suspend fun initialize(context: Context) = initializationMutex.withLock {
        if (initialized) return

        withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().init(context.applicationContext)
            FFmpeg.getInstance().init(context.applicationContext)
            runCatching {
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            }
        }
        initialized = true
    }
}

class MediaAnalyzer(private val context: Context) {
    suspend fun analyze(
        url: String,
        browserContext: BrowserRequestContext? = null,
    ): MediaAnalysis = withContext(Dispatchers.IO) {
        MediaEngine.initialize(context)

        val request = YoutubeDLRequest(url)
            .addOption("--no-playlist")
            .addOption("--no-warnings")
        browserContext?.referer?.takeIf { it.isNotBlank() }?.let {
            request.addOption("--referer", it)
        }
        browserContext?.cookies?.takeIf { it.isNotBlank() }?.let {
            request.addOption("--add-header", "Cookie:$it")
        }
        browserContext?.userAgent?.takeIf { it.isNotBlank() }?.let {
            request.addOption("--user-agent", it)
        }
        val info = YoutubeDL.getInstance().getInfo(request)
        val formats = info.formats.orEmpty()
        val heights = formats
            .asSequence()
            .filter { it.height >= 144 && !it.vcodec.isNullOrBlank() && it.vcodec != "none" }
            .map { it.height }
            .distinct()
            .sortedDescending()
            .take(8)
            .toList()

        val choices = buildList {
            add(
                FormatChoice(
                    id = "best",
                    label = "Best quality",
                    detail = "Best video · all audio tracks when available",
                    selector = "bestvideo+mergeall[vcodec=none]/bestvideo+bestaudio/best",
                )
            )

            heights.forEach { height ->
                val approx = formats
                    .filter { it.height == height }
                    .maxOfOrNull { maxOf(it.fileSize, it.fileSizeApproximate) }
                    ?: 0L
                add(
                    FormatChoice(
                        id = "video-$height",
                        label = "${height}p",
                        detail = listOfNotNull(
                            "Video + available audio tracks",
                            approx.takeIf { it > 0 }?.let(::formatBytes),
                        ).joinToString(" · "),
                        selector = "bestvideo[height<=$height]+mergeall[vcodec=none]/bestvideo[height<=$height]+bestaudio/best[height<=$height]",
                        estimatedBytes = approx,
                    )
                )
            }

            add(
                FormatChoice(
                    id = "audio",
                    label = "Audio only",
                    detail = "MP3 · best available audio",
                    selector = "bestaudio/best",
                    audioOnly = true,
                )
            )
        }.distinctBy { it.id }

        MediaAnalysis(
            sourceUrl = url,
            title = info.title ?: info.fulltitle ?: "Untitled media",
            subtitle = info.uploader ?: info.extractor ?: "Online media",
            thumbnailUrl = info.thumbnail,
            durationSeconds = info.duration,
            formats = choices,
            referer = browserContext?.referer,
            cookies = browserContext?.cookies,
            userAgent = browserContext?.userAgent,
        )
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "Unknown size"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}
