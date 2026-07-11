package com.redcoal.redmedia.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.redcoal.redmedia.mobile.data.DownloadEntity
import com.redcoal.redmedia.mobile.download.FormatChoice
import com.redcoal.redmedia.mobile.download.BrowserRequestContext
import com.redcoal.redmedia.mobile.download.MediaAnalysis
import com.redcoal.redmedia.mobile.download.MediaAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data object Loading : AnalysisState
    data class Ready(val media: MediaAnalysis) : AnalysisState
    data class Error(val message: String) : AnalysisState
}

data class BrowserMediaCandidate(
    val url: String,
    val referer: String,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as RedMediaApp
    private val analyzer = MediaAnalyzer(application)
    private val repository = app.downloads

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _analysis = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysis: StateFlow<AnalysisState> = _analysis.asStateFlow()

    private val _selectedFormatId = MutableStateFlow<String?>(null)
    val selectedFormatId: StateFlow<String?> = _selectedFormatId.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _browserUrl = MutableStateFlow("")
    val browserUrl: StateFlow<String> = _browserUrl.asStateFlow()

    private val _browserCandidates = MutableStateFlow<List<BrowserMediaCandidate>>(emptyList())
    val browserCandidates: StateFlow<List<BrowserMediaCandidate>> = _browserCandidates.asStateFlow()

    val downloads = repository.downloads.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun setUrl(value: String, autoAnalyze: Boolean = false) {
        val extracted = extractUrl(value)
        _url.value = extracted ?: value.trim()
        _analysis.value = AnalysisState.Idle
        _selectedFormatId.value = null
        if (autoAnalyze && extracted != null) analyze()
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun openBrowser() {
        val target = extractUrl(_url.value)
        if (target == null) {
            _analysis.value = AnalysisState.Error("Paste a valid page link first")
            return
        }
        _browserUrl.value = target
        _browserCandidates.value = emptyList()
        _selectedTab.value = 1
    }

    fun updateBrowserUrl(url: String) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            _browserUrl.value = url
        }
    }

    fun addBrowserCandidate(url: String, referer: String, mediaHint: Boolean = false) {
        if (!mediaHint && !isLikelyMediaUrl(url) && !isLikelyEmbeddedPlayerUrl(url, referer)) return
        _browserCandidates.update { current ->
            (listOf(BrowserMediaCandidate(url, referer)) + current.filterNot { it.url == url })
                .take(30)
        }
    }

    fun analyzeBrowser(
        currentPageUrl: String,
        discoveredUrls: List<String>,
        cookies: String?,
        userAgent: String?,
    ) {
        val pageUrl = extractUrl(currentPageUrl) ?: return
        discoveredUrls.forEach { addBrowserCandidate(it, pageUrl) }
        val usefulDiscovered = discoveredUrls.filter {
            isLikelyMediaUrl(it) || isLikelyEmbeddedPlayerUrl(it, pageUrl)
        }
        val candidates = (
            listOf(BrowserMediaCandidate(pageUrl, pageUrl)) +
                _browserCandidates.value +
                usefulDiscovered.map { BrowserMediaCandidate(it, pageUrl) }
            ).distinctBy { it.url }.take(40)

        viewModelScope.launch {
            _analysis.value = AnalysisState.Loading
            var lastError: Throwable? = null
            for (candidate in candidates) {
                val result = runCatching {
                    analyzer.analyze(
                        candidate.url,
                        BrowserRequestContext(
                            pageUrl = pageUrl,
                            referer = candidate.referer.ifBlank { pageUrl },
                            cookies = cookies,
                            userAgent = userAgent,
                        ),
                    )
                }
                result.onSuccess { media ->
                    _url.value = pageUrl
                    _selectedFormatId.value = media.formats.firstOrNull()?.id
                    _analysis.value = AnalysisState.Ready(media.copy(sourceUrl = candidate.url))
                    _selectedTab.value = 0
                    return@launch
                }
                lastError = result.exceptionOrNull()
            }
            _analysis.value = AnalysisState.Error(
                lastError?.message?.lineSequence()?.lastOrNull()?.take(260)
                    ?: "No downloadable media was found after interacting with the page"
            )
            _selectedTab.value = 0
        }
    }

    fun selectFormat(id: String) {
        _selectedFormatId.value = id
    }

    fun analyze() {
        val target = extractUrl(_url.value)
        if (target == null) {
            _analysis.value = AnalysisState.Error("Paste a valid http or https link")
            return
        }

        viewModelScope.launch {
            _analysis.value = AnalysisState.Loading
            _analysis.value = runCatching { analyzer.analyze(target) }
                .fold(
                    onSuccess = { media ->
                        _selectedFormatId.value = media.formats.firstOrNull()?.id
                        AnalysisState.Ready(media)
                    },
                    onFailure = { error ->
                        AnalysisState.Error(
                            error.message?.lineSequence()?.lastOrNull()?.take(260)
                                ?: "Unable to inspect this link"
                        )
                    },
                )
        }
    }

    fun startDownload() {
        val ready = _analysis.value as? AnalysisState.Ready ?: return
        val selected = ready.media.formats.firstOrNull { it.id == _selectedFormatId.value } ?: return
        viewModelScope.launch {
            repository.enqueue(ready.media, selected)
            _selectedTab.value = 2
        }
    }

    fun pause(download: DownloadEntity) = viewModelScope.launch { repository.pause(download.id) }
    fun resume(download: DownloadEntity) = viewModelScope.launch { repository.resume(download.id) }
    fun retry(download: DownloadEntity) = viewModelScope.launch { repository.retry(download.id) }
    fun delete(download: DownloadEntity) = viewModelScope.launch { repository.delete(download.id) }
    fun reuse(download: DownloadEntity) {
        _url.value = download.sourceUrl
        _selectedFormatId.value = null
        _selectedTab.value = 0
        viewModelScope.launch {
            _analysis.value = AnalysisState.Loading
            _analysis.value = runCatching {
                analyzer.analyze(
                    download.sourceUrl,
                    BrowserRequestContext(
                        pageUrl = download.referer ?: download.sourceUrl,
                        referer = download.referer ?: download.sourceUrl,
                        cookies = download.cookies,
                        userAgent = download.userAgent,
                    ),
                )
            }.fold(
                onSuccess = { media ->
                    _selectedFormatId.value = media.formats.firstOrNull()?.id
                    AnalysisState.Ready(media)
                },
                onFailure = { error -> AnalysisState.Error(error.message ?: "Unable to inspect this link") },
            )
        }
    }

    companion object {
        private val urlRegex = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
        fun extractUrl(value: String): String? = urlRegex.find(value)?.value?.trimEnd('.', ',', ')', ']')

        fun isLikelyMediaUrl(value: String): Boolean {
            val normalized = value.substringBefore('#').lowercase()
            if (Regex("\\.(ts|m4s)(\\?|$)").containsMatchIn(normalized)) return false
            return Regex("\\.(m3u8|mpd|mp4|webm|mkv|m4v|mov|mp3|m4a|aac|ogg|oga|wav|flac)(\\?|$)").containsMatchIn(normalized) ||
                Regex("/(master|manifest|playlist)([./?_-]|$)").containsMatchIn(normalized) ||
                "/hls/" in normalized || "/dash/" in normalized ||
                "/videoplayback" in normalized || "mime=video" in normalized ||
                "mime=audio" in normalized || "format=m3u8" in normalized
        }

        fun isLikelyEmbeddedPlayerUrl(value: String, pageUrl: String): Boolean {
            val normalized = value.lowercase()
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) return false
            if (Regex("\\.(js|css|json|png|jpe?g|gif|svg|webp|woff2?|ttf|map)$")
                    .containsMatchIn(normalized.substringBefore('?'))) return false
            val pageHost = runCatching { java.net.URI(pageUrl).host.orEmpty() }.getOrDefault("")
            val candidateHost = runCatching { java.net.URI(value).host.orEmpty() }.getOrDefault("")
            if (candidateHost.isBlank() || candidateHost.equals(pageHost, ignoreCase = true)) return false
            return Regex("(?:^|/)(?:embed|e|v|video|watch|player|play|stream|file|f|d)(?:/|[-_.?=])")
                .containsMatchIn(runCatching { java.net.URI(value).rawPath + "?" + java.net.URI(value).rawQuery }.getOrDefault(normalized))
        }
    }
}
