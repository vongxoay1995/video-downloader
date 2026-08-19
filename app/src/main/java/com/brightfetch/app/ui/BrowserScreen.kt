package com.brightfetch.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.brightfetch.app.MainViewModel
import com.brightfetch.app.browser.MediaSniffer
import com.brightfetch.app.model.MediaCandidate
import com.brightfetch.app.ui.theme.Ink
import com.brightfetch.app.ui.theme.Mint
import com.brightfetch.app.ui.theme.SunnyYellow
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class BrowserState {
    var address by mutableStateOf("")
    var pageTitle by mutableStateOf("BrightFetch")
    var showLanding by mutableStateOf(true)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    internal var pendingUrl by mutableStateOf<String?>(null)
    internal var webView: WebView? = null
    internal var mediaReporter: BrowserMediaReporter? = null

    fun navigate(raw: String, searchBase: String = "https://www.google.com/search?q=") {
        val value = raw.trim()
        if (value.isBlank()) return
        val sharedUrl = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)
            .find(value)
            ?.value
            ?.trimEnd('.', ',', ';', ')', ']', '}')
        val target = when {
            sharedUrl != null -> sharedUrl
            value.contains('.') && !value.contains(' ') -> "https://$value"
            else -> searchBase + URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        }
        address = target
        pendingUrl = target
        showLanding = false
    }

    fun home() {
        showLanding = true
        address = ""
        pageTitle = "BrightFetch"
        progress = 0f
    }
}

@Composable
fun rememberBrowserState(): BrowserState = remember { BrowserState() }

@Composable
fun BrowserScreen(state: BrowserState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val candidates by viewModel.candidates.collectAsState()
    val isResolvingPage by viewModel.isResolvingPage.collectAsState()
    var showCandidateSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = !state.showLanding) {
        when {
            state.webView?.canGoBack() == true -> state.webView?.goBack()
            else -> state.home()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BrowserTopBar(
            state = state,
            onHome = {
                viewModel.clearCandidates()
                state.home()
            },
            onSubmit = {
                viewModel.clearCandidates()
                state.navigate(state.address)
            },
            onNewTab = {
                viewModel.clearCandidates()
                state.home()
            },
            onClearDetected = viewModel::clearCandidates,
        )
        if (state.progress in 0.01f..0.99f) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = SunnyYellow,
            )
        }
        Box(Modifier.weight(1f)) {
            when {
                showCandidateSheet && !isResolvingPage && candidates.isNotEmpty() -> DetectedMediaPanel(
                    candidates = candidates,
                    onClose = { showCandidateSheet = false },
                    onDownload = { candidate ->
                        viewModel.enqueue(candidate)
                        Toast.makeText(context, "Added to downloads", Toast.LENGTH_SHORT).show()
                        showCandidateSheet = false
                    },
                )
                state.showLanding -> BrowserLanding(
                    state = state,
                    onNavigate = {
                        viewModel.clearCandidates()
                        state.navigate(it)
                    },
                )
                else -> BrowserPage(state, viewModel)
            }
        }
        if (isResolvingPage) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(modifier = Modifier.width(48.dp), color = SunnyYellow)
                Spacer(Modifier.width(12.dp))
                Text("Extracting video…")
            }
        } else if (candidates.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(56.dp)
                    .background(Color(0xFFE9DFFF), RoundedCornerShape(18.dp))
                    .clickable { showCandidateSheet = !showCandidateSheet },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (showCandidateSheet) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Download,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (showCandidateSheet) "Back to browser" else "${candidates.size} video detected")
            }
        }
    }
}

@Composable
private fun DetectedMediaPanel(
    candidates: List<MediaCandidate>,
    onClose: () -> Unit,
    onDownload: (MediaCandidate) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
            Text(
                "Detected videos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Only download files you are authorized to use.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Back to browser")
            }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(bottom = 8.dp)) {
            items(candidates, key = MediaCandidate::id) { candidate ->
                CandidateRow(candidate) { onDownload(candidate) }
            }
        }
    }
}

@Composable
private fun BrowserTopBar(
    state: BrowserState,
    onHome: () -> Unit,
    onSubmit: () -> Unit,
    onNewTab: () -> Unit,
    onClearDetected: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val submitAndDismissKeyboard = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onSubmit()
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onHome) {
            Icon(Icons.Default.Home, contentDescription = "Home", tint = Ink)
        }
        OutlinedTextField(
            value = state.address,
            onValueChange = { state.address = it },
            modifier = Modifier.weight(1f).height(54.dp),
            singleLine = true,
            placeholder = { Text("Search with Google or enter URL", maxLines = 1) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submitAndDismissKeyboard() }),
            trailingIcon = {
                IconButton(onClick = submitAndDismissKeyboard) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go")
                }
            },
        )
        IconButton(onClick = onNewTab) {
            Icon(Icons.Default.Add, contentDescription = "New tab", tint = Ink)
        }
        Box(
            Modifier.size(38.dp).background(Color.Transparent, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("1", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Ink)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Clear detected videos") },
                    onClick = {
                        menuExpanded = false
                        onClearDetected()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Browser home") },
                    onClick = {
                        menuExpanded = false
                        state.home()
                    },
                )
            }
        }
    }
}

@Composable
private fun BrowserLanding(state: BrowserState, onNavigate: (String) -> Unit) {
    var showDisclaimer by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize()) {
        SunnyBackground()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xEEF4F3EF)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    QuickAction(Icons.AutoMirrored.Filled.ArrowBack, "Back", state.canGoBack) { state.webView?.goBack() }
                    QuickAction(Icons.AutoMirrored.Filled.ArrowForward, "Forward", state.canGoForward) { state.webView?.goForward() }
                    QuickAction(Icons.Default.Refresh, "Refresh") { state.webView?.reload() }
                    QuickAction(Icons.Default.Palette, "Theme") { }
                    QuickAction(Icons.Default.DarkMode, "Reading") { }
                    QuickAction(Icons.Default.Tune, "Settings") { }
                }
            }

            if (showDisclaimer) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xEEF4F3EF)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Disclaimer", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { showDisclaimer = false }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This app only downloads user-authorized, publicly accessible video files. " +
                                "Copyright-protected, paywalled, DRM-protected and encrypted content—including YouTube—is not supported.\n\n" +
                                "Make sure the content belongs to you, is licensed for download, or is in the public domain.",
                            lineHeight = 22.sp,
                            color = Color(0xFF30352F),
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xEAF5F3ED)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Search Engine", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        SearchEngineButton("G", "Google", Color(0xFF4285F4)) { onNavigate("https://www.google.com") }
                        SearchEngineButton("D", "DuckDuckGo", Color(0xFFDE5833)) { onNavigate("https://duckduckgo.com") }
                        SearchEngineButton("b", "Bing", Color(0xFF008373)) { onNavigate("https://www.bing.com") }
                        SearchEngineButton("Y!", "Yahoo", Color(0xFF6001D2)) { onNavigate("https://search.yahoo.com") }
                    }
                }
            }
            Spacer(Modifier.height(200.dp))
        }
    }
}

@Composable
private fun SunnyBackground() {
    Canvas(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFFFFCED), Color(0xFFFFDF62), Color(0xFFFFD84D)))
        )
    ) {
        val wave = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height * .39f)
            quadraticTo(size.width * .52f, size.height * .57f, 0f, size.height * .63f)
            close()
        }
        drawPath(wave, color = Color(0xCCFFFDF5), style = Fill)
        drawCircle(
            color = Color(0x22FFFFFF),
            radius = size.width * .55f,
            center = Offset(size.width * .85f, size.height * .8f),
        )
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = label, tint = if (enabled) Ink else Ink.copy(alpha = .3f))
    }
}

@Composable
private fun SearchEngineButton(mark: String, label: String, color: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(72.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Text(mark, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(7.dp))
        Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun BrowserPage(state: BrowserState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val reporter = state.mediaReporter ?: BrowserMediaReporter(viewModel).also {
        state.mediaReporter = it
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            state.webView ?: WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                settings.userAgentString = settings.userAgentString
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                reporter.updateContext(userAgent = settings.userAgentString)
                addJavascriptInterface(MediaJavascriptBridge(reporter), MEDIA_BRIDGE_NAME)

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        state.progress = newProgress / 100f
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        if (!title.isNullOrBlank()) {
                            state.pageTitle = title
                            reporter.updateContext(pageTitle = title)
                        }
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        if (!url.isNullOrBlank()) {
                            state.address = url
                            viewModel.clearCandidates()
                            reporter.updateContext(pageUrl = url, pageTitle = state.pageTitle)
                            reporter.resolveKnownPage(url)
                        }
                        updateNavigationState(state, view)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        state.progress = 1f
                        updateNavigationState(state, view)
                        reporter.updateContext(pageUrl = url, pageTitle = state.pageTitle)
                        if (view != null) installMediaObserver(view)
                    }

                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        super.onPageCommitVisible(view, url)
                        if (view != null) installMediaObserver(view)
                    }

                    override fun onLoadResource(view: WebView?, url: String?) {
                        if (!url.isNullOrBlank()) reporter.reportNetworkRequest(url)
                    }

                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val requestUrl = request?.url?.toString() ?: return null
                        // This callback runs on a WebView worker thread. Never touch WebView,
                        // Compose state, or CookieManager here; the reporter posts to main.
                        reporter.reportNetworkRequest(requestUrl)
                        return null
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val target = request?.url ?: return false
                        return when (target.scheme?.lowercase()) {
                            "http", "https" -> false
                            "intent" -> {
                                val fallback = runCatching {
                                    Intent.parseUri(target.toString(), Intent.URI_INTENT_SCHEME)
                                        .getStringExtra("browser_fallback_url")
                                }.getOrNull()
                                if (fallback?.startsWith("http", ignoreCase = true) == true) {
                                    view?.loadUrl(fallback)
                                }
                                true
                            }
                            "tel", "mailto", "sms", "smsto" -> {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target)) }
                                true
                            }
                            // Keep app-specific deep links (tiktok:, snssdk:, market:, etc.)
                            // from taking the user out of the downloader browser.
                            else -> true
                        }
                    }
                }
                setDownloadListener(DownloadListener { downloadUrl, userAgent, disposition, mimeType, _ ->
                    if (downloadUrl != null) {
                        reporter.reportDownload(downloadUrl, disposition, mimeType, userAgent)
                    }
                })
            }.also { state.webView = it }
        },
        update = { webView ->
            state.pendingUrl?.let { target ->
                state.pendingUrl = null
                webView.loadUrl(target)
            }
        },
    )
}

private fun updateNavigationState(state: BrowserState, webView: WebView?) {
    state.canGoBack = webView?.canGoBack() == true
    state.canGoForward = webView?.canGoForward() == true
}

private fun installMediaObserver(webView: WebView) {
    val script = """
        (function() {
          if (window.__brightFetchInstalled) {
            if (window.__brightFetchScan) window.__brightFetchScan();
            return 'already-installed';
          }
          window.__brightFetchInstalled = true;
          var seen = new Set();

          function isLikelyMedia(url) {
            return /(\.mp4|\.m3u8|\.webm|\.mov|\.mkv)(?:[?#]|${'$'})|mime_type=video|mime=video|video_mp4|\/video\/tos\/|\/aweme\/v1\/play\//i.test(url);
          }

          function report(rawUrl, force) {
            if (!rawUrl || typeof rawUrl !== 'string' || rawUrl.indexOf('blob:') === 0) return;
            var url;
            try { url = new URL(rawUrl, document.baseURI).href; } catch (_) { return; }
            if (!/^https?:\/\//i.test(url) || (!force && !isLikelyMedia(url)) || seen.has(url)) return;
            seen.add(url);
            try { $MEDIA_BRIDGE_NAME.report(url, document.title || '', location.href || ''); } catch (_) {}
          }

          function scanElements() {
            document.querySelectorAll('video').forEach(function(video) {
              report(video.currentSrc, true);
              report(video.src, true);
              video.querySelectorAll('source').forEach(function(source) { report(source.src, true); });
            });
            document.querySelectorAll('source').forEach(function(source) { report(source.src, true); });
          }

          function scanPerformance() {
            try {
              performance.getEntriesByType('resource').forEach(function(entry) { report(entry.name, false); });
            } catch (_) {}
          }

          function scanEmbeddedJson() {
            var scripts = document.querySelectorAll('script[type="application/json"], script#__NEXT_DATA__, script#SIGI_STATE, script#__UNIVERSAL_DATA_FOR_REHYDRATION__');
            scripts.forEach(function(script) {
              var text = script.textContent || '';
              if (!text || text.length > 5000000) return;
              var root;
              try { root = JSON.parse(text); } catch (_) { return; }
              var budget = 25000;
              function walk(value, mediaContext, depth) {
                if (budget-- <= 0 || depth > 18 || value == null) return;
                if (typeof value === 'string') {
                  report(value, mediaContext);
                  return;
                }
                if (typeof value !== 'object') return;
                if (Array.isArray(value)) {
                  value.forEach(function(item) { walk(item, mediaContext, depth + 1); });
                  return;
                }
                Object.keys(value).forEach(function(key) {
                  var childContext = mediaContext || /(playaddr|downloadaddr|playurl|playapi)/i.test(key);
                  walk(value[key], childContext, depth + 1);
                });
              }
              walk(root, false, 0);
            });
          }

          window.__brightFetchScan = function() {
            scanElements();
            scanPerformance();
            scanEmbeddedJson();
          };
          window.__brightFetchScan();
          new MutationObserver(window.__brightFetchScan).observe(document.documentElement || document, {
            childList: true, subtree: true, attributes: true, attributeFilter: ['src']
          });
          ['play', 'loadedmetadata', 'canplay'].forEach(function(eventName) {
            document.addEventListener(eventName, window.__brightFetchScan, true);
          });
          setInterval(window.__brightFetchScan, 1500);
          return 'installed';
        })();
    """.trimIndent()
    webView.evaluateJavascript(script, null)
}

internal class BrowserMediaReporter(private val viewModel: MainViewModel) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var pageTitle: String = "Detected video"
    @Volatile private var pageUrl: String? = null
    @Volatile private var userAgent: String? = null

    fun updateContext(pageTitle: String? = null, pageUrl: String? = null, userAgent: String? = null) {
        pageTitle?.takeIf(String::isNotBlank)?.let { this.pageTitle = it }
        pageUrl?.takeIf(String::isNotBlank)?.let { this.pageUrl = it }
        userAgent?.takeIf(String::isNotBlank)?.let { this.userAgent = it }
    }

    fun reportNetworkRequest(url: String) {
        if (!com.brightfetch.app.browser.MediaUrlClassifier.isLikelyNetworkMedia(url)) return
        val titleSnapshot = pageTitle
        val pageSnapshot = pageUrl
        val agentSnapshot = userAgent
        mainHandler.post {
            MediaSniffer.fromRequest(
                url = url,
                pageTitle = titleSnapshot,
                pageUrl = pageSnapshot,
                userAgent = agentSnapshot,
                cookie = cookieFor(url),
            )?.let(viewModel::addCandidate)
        }
    }

    fun resolveKnownPage(url: String) {
        viewModel.resolveKnownPage(
            pageUrl = url,
            pageTitle = pageTitle,
            userAgent = userAgent,
            cookie = cookieFor(url),
        )
    }

    fun reportVideoElement(url: String, reportedTitle: String?, reportedPageUrl: String?) {
        if (!com.brightfetch.app.browser.MediaUrlClassifier.isUsableVideoElementUrl(url)) return
        val titleSnapshot = reportedTitle?.takeIf(String::isNotBlank) ?: pageTitle
        val pageSnapshot = reportedPageUrl?.takeIf(String::isNotBlank) ?: pageUrl
        val agentSnapshot = userAgent
        mainHandler.post {
            MediaSniffer.fromVideoElement(
                url = url,
                pageTitle = titleSnapshot,
                pageUrl = pageSnapshot,
                userAgent = agentSnapshot,
                cookie = cookieFor(url),
            )?.let(viewModel::addCandidate)
        }
    }

    fun reportDownload(url: String, disposition: String?, mimeType: String?, reportedUserAgent: String?) {
        val titleSnapshot = pageTitle
        val pageSnapshot = pageUrl
        val agentSnapshot = reportedUserAgent?.takeIf(String::isNotBlank) ?: userAgent
        mainHandler.post {
            MediaSniffer.fromDownload(
                url = url,
                contentDisposition = disposition,
                mimeType = mimeType,
                pageTitle = titleSnapshot,
                pageUrl = pageSnapshot,
                userAgent = agentSnapshot,
                cookie = cookieFor(url),
            )?.let(viewModel::addCandidate)
        }
    }

    private fun cookieFor(url: String): String? = runCatching {
        CookieManager.getInstance().getCookie(url)
    }.getOrNull()
}

private class MediaJavascriptBridge(private val reporter: BrowserMediaReporter) {
    @JavascriptInterface
    fun report(url: String, pageTitle: String?, pageUrl: String?) {
        reporter.reportVideoElement(url, pageTitle, pageUrl)
    }
}

private const val MEDIA_BRIDGE_NAME = "BrightFetchMedia"

@Composable
private fun CandidateRow(candidate: MediaCandidate, onDownload: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(Mint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Download, contentDescription = null)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(candidate.suggestedFileName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val extension = candidate.suggestedFileName.substringAfterLast('.', "mp4").lowercase(Locale.US)
            Row(
                modifier = Modifier.padding(top = 4.dp, bottom = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CandidateMetadataBadge(extension, Color(0xFFF28B38), Color.White)
                candidate.height?.takeIf { it > 0 }?.let {
                    CandidateMetadataBadge("${it}p", Color(0xFF76B852), Color.White)
                }
                candidate.contentLengthBytes?.takeIf { it > 0L }?.let {
                    CandidateMetadataBadge(formatFileSize(it), Color(0xFFE7E7E7), Ink)
                }
            }
            val duration = candidate.durationSeconds?.takeIf { it >= 0L }?.let(::formatDuration)
            Text(
                if (candidate.isHls) {
                    "HLS stream"
                } else {
                    listOfNotNull(Uri.parse(candidate.url).host, duration).joinToString(" • ")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(onClick = onDownload) { Text("Download") }
    }
}

@Composable
private fun CandidateMetadataBadge(text: String, background: Color, foreground: Color) {
    Text(
        text = text,
        color = foreground,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier.background(background, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

private fun formatFileSize(bytes: Long): String {
    val megabytes = bytes / 1_000_000.0
    return when {
        megabytes >= 100.0 -> String.format(Locale.US, "%.0f MB", megabytes)
        megabytes >= 10.0 -> String.format(Locale.US, "%.1f MB", megabytes)
        else -> String.format(Locale.US, "%.2f MB", megabytes)
    }
}

private fun formatDuration(seconds: Long): String = String.format(
    Locale.US,
    "%02d:%02d",
    seconds / 60,
    seconds % 60,
)
