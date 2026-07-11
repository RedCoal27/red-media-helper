package com.redcoal.videoplayback.mobile.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.redcoal.videoplayback.mobile.AnalysisState
import com.redcoal.videoplayback.mobile.MainViewModel
import com.redcoal.videoplayback.mobile.R
import com.redcoal.videoplayback.mobile.data.DownloadEntity
import com.redcoal.videoplayback.mobile.data.DownloadStatus
import com.redcoal.videoplayback.mobile.download.FormatChoice
import com.redcoal.videoplayback.mobile.download.MediaAnalysis
import org.json.JSONArray
import org.json.JSONTokener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlaybackRoot(
    viewModel: MainViewModel,
    onOpenDownloads: () -> Unit,
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val activeCount = downloads.count { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.app_logo),
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Video Playback", fontWeight = FontWeight.SemiBold)
                            Text(
                                when (selectedTab) {
                                    0 -> "Media downloader"
                                    1 -> "Interactive browser"
                                    else -> "Download queue"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenDownloads) {
                        Icon(Icons.Default.Folder, contentDescription = "Open Downloads")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("New") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.Language, contentDescription = null) },
                    label = { Text("Browser") },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    label = { Text(if (activeCount > 0) "Downloads ($activeCount)" else "Downloads") },
                )
            }
        },
    ) { padding ->
        when (selectedTab) {
            0 -> NewDownloadScreen(
                modifier = Modifier.padding(padding),
                viewModel = viewModel,
            )
            1 -> BrowserScreen(
                modifier = Modifier.padding(padding),
                viewModel = viewModel,
            )
            else -> DownloadsScreen(
                modifier = Modifier.padding(padding),
                downloads = downloads,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onRetry = viewModel::retry,
                onDelete = viewModel::delete,
            )
        }
    }
}

@Composable
private fun NewDownloadScreen(
    modifier: Modifier,
    viewModel: MainViewModel,
) {
    val url by viewModel.url.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val selectedFormatId by viewModel.selectedFormatId.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Paste a page or video link",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "You can also share a link from your browser directly to Video Playback.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        item {
            OutlinedTextField(
                value = url,
                onValueChange = viewModel::setUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Media URL") },
                placeholder = { Text("https://...") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                trailingIcon = {
                    if (url.isNotBlank()) {
                        IconButton(onClick = { viewModel.setUrl("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear URL")
                        }
                    } else {
                        IconButton(
                            onClick = {
                                clipboard.getText()?.text?.let { viewModel.setUrl(it) }
                            }
                        ) {
                            Icon(Icons.Default.AddLink, contentDescription = "Paste URL")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = viewModel::analyze,
                enabled = url.isNotBlank() && analysis !is AnalysisState.Loading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                if (analysis is AnalysisState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Inspecting link")
                } else {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Find media")
                }
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = viewModel::openBrowser,
                enabled = url.isNotBlank() && analysis !is AnalysisState.Loading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Default.Language, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open page in browser")
            }
        }

        when (val state = analysis) {
            is AnalysisState.Error -> item {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.13f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        state.message,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            is AnalysisState.Ready -> item {
                MediaResult(
                    media = state.media,
                    selectedFormatId = selectedFormatId,
                    onSelectFormat = viewModel::selectFormat,
                    onDownload = viewModel::startDownload,
                )
            }
            else -> Unit
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserScreen(
    modifier: Modifier,
    viewModel: MainViewModel,
) {
    val initialUrl by viewModel.browserUrl.collectAsStateWithLifecycle()
    val candidates by viewModel.browserCandidates.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    var loading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
            }
            IconButton(onClick = { webView?.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload")
            }
            Text(
                currentUrl,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                WebView(context).apply {
                    webView = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            loading = true
                            currentUrl = url
                            viewModel.updateBrowserUrl(url)
                            updateNavigation(view)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                            currentUrl = url
                            viewModel.updateBrowserUrl(url)
                            updateNavigation(view)
                        }

                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                            val requestUrl = request.url.toString()
                            val headers = request.requestHeaders
                            val accept = headers.entries.firstOrNull { it.key.equals("Accept", true) }?.value.orEmpty()
                            val hasRange = headers.keys.any { it.equals("Range", true) }
                            val mediaHint = hasRange || accept.contains("video", true) ||
                                accept.contains("audio", true) || accept.contains("mpegurl", true) ||
                                accept.contains("dash+xml", true)
                            if (mediaHint || MainViewModel.isLikelyMediaUrl(requestUrl)) {
                                viewModel.addBrowserCandidate(
                                    requestUrl,
                                    headers.entries.firstOrNull { it.key.equals("Referer", true) }?.value ?: currentUrl,
                                    mediaHint = mediaHint,
                                )
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        private fun updateNavigation(view: WebView) {
                            canGoBack = view.canGoBack()
                            canGoForward = view.canGoForward()
                        }
                    }
                    if (initialUrl.isNotBlank()) loadUrl(initialUrl)
                }
            },
        )

        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(
                    if (candidates.isEmpty()) "Interact with the page, then inspect it"
                    else "${candidates.size} media request${if (candidates.size == 1) "" else "s"} detected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val active = webView ?: return@Button
                        val pageUrl = active.url ?: currentUrl
                        active.evaluateJavascript(MEDIA_DISCOVERY_SCRIPT) { result ->
                            val discovered = runCatching {
                                val encoded = if (result == "null") "[]" else JSONTokener(result).nextValue() as String
                                val values = JSONArray(encoded)
                                List(values.length()) { values.optString(it) }.filter { it.isNotBlank() }
                            }.getOrDefault(emptyList())
                            viewModel.analyzeBrowser(
                                currentPageUrl = pageUrl,
                                discoveredUrls = discovered,
                                cookies = CookieManager.getInstance().getCookie(pageUrl),
                                userAgent = active.settings.userAgentString,
                            )
                        }
                    },
                    enabled = analysis !is AnalysisState.Loading && currentUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    if (analysis is AnalysisState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Inspecting page")
                    } else {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Find media")
                    }
                }
            }
        }
    }
}

private const val MEDIA_DISCOVERY_SCRIPT = """
    (() => {
      const absolute = value => {
        try { return new URL(value, location.href).href; } catch (_) { return ''; }
      };
      const mediaPattern = /\.(m3u8|mpd|mp4|webm|mkv|m4v|mov|mp3|m4a|aac|ogg|oga|wav|flac)([?#]|$)|\/(hls|dash|master|manifest|playlist|videoplayback)([/?#._-]|$)|mime=(video|audio)/i;
      const playerPattern = /(?:^|\/)(embed|e|v|video|watch|player|play|stream|file|f|d)(?:\/|[-_.?=])/i;
      const urls = [];
      const add = (value, force = false) => {
        const url = absolute(value);
        if (url && (force || mediaPattern.test(url) || playerPattern.test(new URL(url).pathname + new URL(url).search))) urls.push(url);
      };
      document.querySelectorAll('video, audio, source').forEach(node => {
        add(node.currentSrc, true); add(node.src, true);
      });
      document.querySelectorAll('iframe, embed').forEach(node => {
        add(node.src, true); add(node.getAttribute('data-src'), true); add(node.getAttribute('data-lazy-src'), true);
      });
      performance.getEntriesByType('resource').forEach(entry => add(entry.name));
      const attributes = ['src', 'href', 'data-src', 'data-url', 'data-video', 'data-file', 'data-embed', 'data-player'];
      document.querySelectorAll('*').forEach(node => attributes.forEach(name => add(node.getAttribute(name))));
      return JSON.stringify([...new Set(urls)].slice(0, 80));
    })()
"""

@Composable
private fun MediaResult(
    media: MediaAnalysis,
    selectedFormatId: String?,
    onSelectFormat: (String) -> Unit,
    onDownload: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
        ) {
            Column {
                AsyncImage(
                    model = media.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.padding(14.dp)) {
                    Text(
                        media.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(
                            media.subtitle,
                            media.durationSeconds.takeIf { it > 0 }?.let(::formatDuration),
                        ).joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Column {
            Text("Quality", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            media.formats.forEachIndexed { index, format ->
                FormatRow(
                    format = format,
                    selected = selectedFormatId == format.id,
                    onClick = { onSelectFormat(format.id) },
                )
                if (index < media.formats.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            }
        }

        FilledTonalButton(
            onClick = onDownload,
            enabled = selectedFormatId != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add to downloads")
        }
    }
}

@Composable
private fun FormatRow(format: FormatChoice, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(format.label, fontWeight = FontWeight.Medium)
            Text(
                format.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadsScreen(
    modifier: Modifier,
    downloads: List<DownloadEntity>,
    onPause: (DownloadEntity) -> Unit,
    onResume: (DownloadEntity) -> Unit,
    onRetry: (DownloadEntity) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
) {
    if (downloads.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text("No downloads yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Add a link from the New tab or share one from your browser.",
                    modifier = Modifier.padding(horizontal = 42.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
    ) {
        items(downloads, key = { it.id }) { download ->
            DownloadRow(download, onPause, onResume, onRetry, onDelete)
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
private fun DownloadRow(
    download: DownloadEntity,
    onPause: (DownloadEntity) -> Unit,
    onResume: (DownloadEntity) -> Unit,
    onRetry: (DownloadEntity) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = download.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(width = 96.dp, height = 58.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    download.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${download.formatLabel} · ${statusLabel(download.status)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor(download.status),
                )
            }
            DownloadActions(download, onPause, onResume, onRetry, onDelete)
        }

        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { download.progress.coerceIn(0f, 100f) / 100f },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row {
            Text(
                if (download.status == DownloadStatus.COMPLETE) "100%" else "${download.progress.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                download.message,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (download.etaSeconds > 0 && download.status == DownloadStatus.RUNNING) {
                Text(
                    "ETA ${formatDuration(download.etaSeconds.toInt())}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DownloadActions(
    download: DownloadEntity,
    onPause: (DownloadEntity) -> Unit,
    onResume: (DownloadEntity) -> Unit,
    onRetry: (DownloadEntity) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
) {
    when (download.status) {
        DownloadStatus.RUNNING, DownloadStatus.QUEUED -> IconButton(onClick = { onPause(download) }) {
            Icon(Icons.Default.Pause, contentDescription = "Pause")
        }
        DownloadStatus.PAUSED -> IconButton(onClick = { onResume(download) }) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
        }
        DownloadStatus.FAILED -> IconButton(onClick = { onRetry(download) }) {
            Icon(Icons.Default.Refresh, contentDescription = "Retry")
        }
        else -> Unit
    }
    if (download.status != DownloadStatus.RUNNING && download.status != DownloadStatus.QUEUED) {
        IconButton(onClick = { onDelete(download) }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete download")
        }
    }
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    DownloadStatus.COMPLETE -> MaterialTheme.colorScheme.primary
    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
    DownloadStatus.PAUSED -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusLabel(status: String): String = when (status) {
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.RUNNING -> "Downloading"
    DownloadStatus.PAUSED -> "Paused"
    DownloadStatus.COMPLETE -> "Complete"
    DownloadStatus.FAILED -> "Failed"
    else -> status
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remaining)
    else "%d:%02d".format(minutes, remaining)
}
