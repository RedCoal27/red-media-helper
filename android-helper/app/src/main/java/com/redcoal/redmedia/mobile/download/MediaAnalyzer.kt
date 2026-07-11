package com.redcoal.redmedia.mobile.download

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class FormatChoice(
    val id: String,
    val label: String,
    val detail: String,
    val selector: String,
    val audioOnly: Boolean = false,
    val estimatedBytes: Long = 0,
    val videoSelector: String? = null,
)

data class AudioTrackChoice(
    val formatId: String,
    val label: String,
    val language: String,
    val detail: String,
)

data class MediaAnalysis(
    val sourceUrl: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    val formats: List<FormatChoice>,
    val audioTracks: List<AudioTrackChoice> = emptyList(),
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
            .addOption("--dump-single-json")
            .addOption("--skip-download")
        if (isYouTubeUrl(url)) {
            request
                .addOption("--js-runtimes", "quickjs")
                .addOption("--extractor-args", "youtube:player_client=all")
        }
        browserContext?.referer?.takeIf { it.isNotBlank() }?.let {
            request.addOption("--referer", it)
        }
        browserContext?.cookies?.takeIf { it.isNotBlank() }?.let {
            request.addOption("--add-header", "Cookie:$it")
        }
        browserContext?.userAgent?.takeIf { it.isNotBlank() }?.let {
            request.addOption("--user-agent", it)
        }
        val response = YoutubeDL.getInstance().execute(request, "inspect-${UUID.randomUUID()}")
        val info = JSONObject(response.out.trim())
        val formats = info.optJSONArray("formats") ?: JSONArray()
        val audioTracks = extractAudioTracks(formats)
        val heights = formats
            .jsonObjects()
            .filter { it.optInt("height") >= 144 && it.optString("vcodec") != "none" }
            .map { it.optInt("height") }
            .distinct()
            .sortedDescending()
            .take(8)
            .toList()

        val choices = buildList {
            add(
                FormatChoice(
                    id = "best",
                    label = "Best quality",
                    detail = audioDetail(audioTracks.size),
                    selector = "bestvideo+bestaudio/best",
                    videoSelector = "bestvideo",
                )
            )

            heights.forEach { height ->
                val approx = formats.jsonObjects()
                    .filter { it.optInt("height") == height }
                    .maxOfOrNull { maxOf(it.optLong("filesize"), it.optLong("filesize_approx")) }
                    ?: 0L
                add(
                    FormatChoice(
                        id = "video-$height",
                        label = "${height}p",
                        detail = listOfNotNull(
                            audioDetail(audioTracks.size),
                            approx.takeIf { it > 0 }?.let(::formatBytes),
                        ).joinToString(" · "),
                        selector = "bestvideo[height<=$height]+bestaudio/best[height<=$height]",
                        estimatedBytes = approx,
                        videoSelector = "bestvideo[height<=$height]",
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
            title = info.optString("title").ifBlank { info.optString("fulltitle", "Untitled media") },
            subtitle = info.optString("uploader").ifBlank { info.optString("extractor", "Online media") },
            thumbnailUrl = info.optString("thumbnail").takeIf(String::isNotBlank),
            durationSeconds = info.optInt("duration"),
            formats = choices,
            audioTracks = audioTracks,
            referer = browserContext?.referer,
            cookies = browserContext?.cookies,
            userAgent = browserContext?.userAgent,
        )
    }
}

private fun JSONArray.jsonObjects(): Sequence<JSONObject> = sequence {
    for (index in 0 until length()) optJSONObject(index)?.let { yield(it) }
}

private fun extractAudioTracks(formats: JSONArray): List<AudioTrackChoice> {
    data class RankedTrack(val choice: AudioTrackChoice, val score: Int)
    val tracks = linkedMapOf<String, RankedTrack>()

    formats.jsonObjects()
        .filter { format ->
            format.optString("format_id").isNotBlank() &&
                format.optString("acodec") !in setOf("", "none") &&
                format.optString("vcodec", "none") == "none"
        }
        .forEach { format ->
            val metadata = format.optJSONObject("audio_track")
            val language = listOf(
                format.optString("language"),
                metadata?.optString("lang").orEmpty(),
                metadata?.optString("languageCode").orEmpty(),
                metadata?.optString("language").orEmpty(),
                metadata?.optString("id").orEmpty(),
            ).firstOrNull(String::isNotBlank) ?: return@forEach
            val label = listOf(
                metadata?.optString("displayName").orEmpty(),
                metadata?.optString("name").orEmpty(),
                metadata?.optString("language").orEmpty(),
                format.optString("format_note"),
                language,
            ).firstOrNull(String::isNotBlank) ?: language
            val formatId = format.optString("format_id")
            val description = "$formatId ${format.optString("format")} ${format.optString("format_note")} $label"
            val bitrate = maxOf(format.optInt("abr"), format.optInt("tbr"))
            val score = bitrate +
                (if (format.optString("ext") == "m4a") 20 else 0) +
                (if (description.contains("original", true)) 10 else 0) -
                (if (Regex("\\bdrc\\b|-drc", RegexOption.IGNORE_CASE).containsMatchIn(description)) 1_000 else 0)
            val key = language.lowercase().replace('_', '-')
            val choice = AudioTrackChoice(
                formatId = formatId,
                label = label,
                language = language,
                detail = listOf(format.optString("ext"), bitrate.takeIf { it > 0 }?.let { "$it kbps" })
                    .filterNotNull().filter(String::isNotBlank).joinToString(" · "),
            )
            if (tracks[key]?.score?.let { score > it } != false) tracks[key] = RankedTrack(choice, score)
        }

    return tracks.values.map(RankedTrack::choice).sortedBy { it.label.lowercase() }
}

private fun audioDetail(count: Int) = when (count) {
    0 -> "Video + best available audio"
    1 -> "Video + 1 audio language"
    else -> "Video + $count audio languages"
}

internal fun isYouTubeUrl(value: String) = runCatching {
    val host = java.net.URI(value).host.orEmpty().lowercase()
    host == "youtu.be" || host.endsWith("youtube.com")
}.getOrDefault(false)

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "Unknown size"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}
