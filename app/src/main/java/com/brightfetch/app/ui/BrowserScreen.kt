package com.brightfetch.app.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.ViewGroup
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.brightfetch.app.MainViewModel
import com.brightfetch.app.browser.BrowserBookmark
import com.brightfetch.app.browser.BrowserHistoryEntry
import com.brightfetch.app.browser.BrowserNavigation
import com.brightfetch.app.browser.BrowserSearchEngine
import com.brightfetch.app.browser.BrowserSettings
import com.brightfetch.app.browser.MediaSniffer
import com.brightfetch.app.model.MediaCandidate
import com.brightfetch.app.model.MediaFormatInspectionState
import com.brightfetch.app.ui.theme.Ink
import com.brightfetch.app.ui.theme.Mint
import com.brightfetch.app.ui.theme.SunnyYellow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

internal class BrowserTabState(val id: Long = nextBrowserTabId()) {
    var address by mutableStateOf("")
    var pageTitle by mutableStateOf("BrightFetch")
    var showLanding by mutableStateOf(true)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    internal var pendingUrl by mutableStateOf<String?>(null)
    internal var webView: WebView? = null
    internal var mediaReporter: BrowserMediaReporter? = null
    internal var mobileUserAgent: String? = null
    internal var appliedDesktopMode: Boolean? = null
    internal var appliedJavaScript: Boolean? = null
    internal var needsMediaRescan: Boolean = false
}

class BrowserState {
    internal val tabs = mutableStateListOf(BrowserTabState())
    internal var activeTabIndex by mutableIntStateOf(0)
    private val fallbackTab = BrowserTabState()
    private var disposed = false

    internal val currentTab: BrowserTabState
        get() = tabs.getOrNull(activeTabIndex.coerceAtLeast(0)) ?: fallbackTab

    var address: String
        get() = currentTab.address
        set(value) { currentTab.address = value }
    val pageTitle: String get() = currentTab.pageTitle
    val showLanding: Boolean get() = currentTab.showLanding
    val canGoBack: Boolean get() = currentTab.canGoBack
    val canGoForward: Boolean get() = currentTab.canGoForward
    val progress: Float get() = currentTab.progress
    val tabCount: Int get() = tabs.size
    internal val webView: WebView? get() = currentTab.webView
    internal val hasHiddenPage: Boolean
        get() = currentTab.showLanding && isHttpUrl(currentTab.webView?.url.orEmpty())

    fun navigate(raw: String, searchEngine: BrowserSearchEngine = BrowserSearchEngine.GOOGLE) {
        val target = BrowserNavigation.resolve(raw, searchEngine) ?: return
        currentTab.address = target
        currentTab.pageTitle = titleFromUrl(target)
        currentTab.pendingUrl = target
        currentTab.showLanding = false
    }

    fun home() {
        currentTab.webView?.stopLoading()
        currentTab.showLanding = true
        currentTab.pendingUrl = null
        currentTab.address = ""
        currentTab.pageTitle = "BrightFetch"
        currentTab.progress = 0f
    }

    fun newTab() {
        if (disposed) return
        tabs.add(BrowserTabState())
        activeTabIndex = tabs.lastIndex
    }

    internal fun selectTab(id: Long): Boolean {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0 || index == activeTabIndex) return false
        activeTabIndex = index
        currentTab.needsMediaRescan = true
        return true
    }

    internal fun closeTab(id: Long) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        if (tabs.size == 1) {
            val oldTab = tabs[0]
            tabs[0] = BrowserTabState()
            activeTabIndex = 0
            destroyTab(oldTab)
            return
        }
        val wasActive = index == activeTabIndex
        val tab = tabs.removeAt(index)
        activeTabIndex = when {
            index < activeTabIndex -> activeTabIndex - 1
            activeTabIndex > tabs.lastIndex -> tabs.lastIndex
            else -> activeTabIndex
        }
        if (wasActive) currentTab.needsMediaRescan = true
        destroyTab(tab)
    }

    internal fun goBack() {
        currentTab.webView?.takeIf { it.canGoBack() }?.let {
            currentTab.showLanding = false
            it.goBack()
        }
    }

    internal fun goForward() {
        currentTab.webView?.takeIf { it.canGoForward() }?.let {
            currentTab.showLanding = false
            it.goForward()
        }
    }

    internal fun reload() {
        currentTab.webView?.let {
            currentTab.showLanding = false
            it.reload()
        }
    }

    internal fun restoreHiddenPage() {
        val tab = currentTab
        val url = tab.webView?.url?.takeIf(::isHttpUrl) ?: return
        tab.showLanding = false
        tab.address = url
        tab.pageTitle = tab.webView?.title?.takeIf(String::isNotBlank) ?: titleFromUrl(url)
        tab.progress = 1f
        tab.needsMediaRescan = true
    }

    internal fun stopLoading() {
        currentTab.webView?.stopLoading()
        currentTab.progress = 1f
    }

    internal fun clearWebData() {
        tabs.forEach { tab ->
            tab.webView?.apply {
                stopLoading()
                clearCache(true)
                clearHistory()
            }
            tab.canGoBack = false
            tab.canGoForward = false
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        tabs.toList().forEach(::destroyTab)
        tabs.clear()
    }

    private fun destroyTab(tab: BrowserTabState) {
        tab.webView?.apply {
            stopLoading()
            removeJavascriptInterface(MEDIA_BRIDGE_NAME)
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        tab.webView = null
        tab.mediaReporter = null
    }
}

private val browserTabIds = AtomicLong(0L)
private fun nextBrowserTabId(): Long = browserTabIds.incrementAndGet()

@Composable
fun rememberBrowserState(): BrowserState = remember { BrowserState() }

private enum class BrowserPanel {
    NONE,
    DETECTED,
    HISTORY,
    BOOKMARKS,
    SETTINGS,
    TABS,
}

@Composable
fun BrowserScreen(state: BrowserState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val candidates by viewModel.candidates.collectAsState()
    val isResolvingPage by viewModel.isResolvingPage.collectAsState()
    val formatInspection by viewModel.mediaFormatInspection.collectAsState()
    val history by viewModel.browserHistory.collectAsState()
    val bookmarks by viewModel.browserBookmarks.collectAsState()
    val settings by viewModel.browserSettings.collectAsState()
    var panel by remember { mutableStateOf(BrowserPanel.NONE) }
    val currentPageUrl = state.address.takeIf(::isHttpUrl)
    val isBookmarked = currentPageUrl != null && bookmarks.any { it.url == currentPageUrl }

    LaunchedEffect(candidates.isEmpty()) {
        if (candidates.isEmpty() && panel == BrowserPanel.DETECTED) panel = BrowserPanel.NONE
    }

    LaunchedEffect(settings) {
        state.tabs.forEach { tab ->
            val webView = tab.webView
            val reporter = tab.mediaReporter
            if (webView != null && reporter != null) {
                applyBrowserSettings(webView, tab, settings, reporter)
            }
        }
    }

    BackHandler(
        enabled = panel != BrowserPanel.NONE || state.hasHiddenPage || !state.showLanding || state.tabCount > 1,
    ) {
        when {
            panel != BrowserPanel.NONE -> panel = BrowserPanel.NONE
            state.hasHiddenPage -> {
                state.restoreHiddenPage()
                viewModel.clearCandidates()
            }
            state.webView?.canGoBack() == true -> state.goBack()
            !state.showLanding -> state.home()
            state.tabCount > 1 -> {
                state.closeTab(state.currentTab.id)
                viewModel.clearCandidates()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BrowserTopBar(
            state = state,
            onHome = {
                viewModel.clearCandidates()
                state.home()
                panel = BrowserPanel.NONE
            },
            onSubmit = {
                viewModel.clearCandidates()
                state.navigate(state.address, settings.searchEngine)
                panel = BrowserPanel.NONE
            },
            onNewTab = {
                viewModel.clearCandidates()
                state.newTab()
                panel = BrowserPanel.NONE
            },
            searchEngineName = settings.searchEngine.displayName,
            isBookmarked = isBookmarked,
            onToggleBookmark = {
                currentPageUrl?.let { url ->
                    val added = viewModel.toggleBrowserBookmark(url, state.pageTitle)
                    Toast.makeText(context, if (added) "Bookmark added" else "Bookmark removed", Toast.LENGTH_SHORT).show()
                }
            },
            onShowTabs = { panel = BrowserPanel.TABS },
            onShowHistory = { panel = BrowserPanel.HISTORY },
            onShowBookmarks = { panel = BrowserPanel.BOOKMARKS },
            onShowSettings = { panel = BrowserPanel.SETTINGS },
            onRefresh = state::reload,
            onStop = state::stopLoading,
            onShare = { currentPageUrl?.let { sharePage(context, it, state.pageTitle) } },
            onCopy = { currentPageUrl?.let { copyPageUrl(context, it) } },
            onOpenExternal = {
                currentPageUrl?.let { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
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
            when (panel) {
                BrowserPanel.DETECTED -> DetectedMediaPanel(
                    candidates = candidates,
                    onClose = { panel = BrowserPanel.NONE },
                    onInspect = viewModel::inspectMediaFormats,
                )
                BrowserPanel.HISTORY -> HistoryPanel(
                    entries = history,
                    onOpen = { url ->
                        viewModel.clearCandidates()
                        state.navigate(url, settings.searchEngine)
                        panel = BrowserPanel.NONE
                    },
                    onClear = viewModel::clearBrowserHistory,
                    onClose = { panel = BrowserPanel.NONE },
                )
                BrowserPanel.BOOKMARKS -> BookmarksPanel(
                    bookmarks = bookmarks,
                    onOpen = { url ->
                        viewModel.clearCandidates()
                        state.navigate(url, settings.searchEngine)
                        panel = BrowserPanel.NONE
                    },
                    onRemove = viewModel::removeBrowserBookmark,
                    onClear = viewModel::clearBrowserBookmarks,
                    onClose = { panel = BrowserPanel.NONE },
                )
                BrowserPanel.SETTINGS -> BrowserSettingsPanel(
                    settings = settings,
                    onSearchEngineChange = viewModel::setBrowserSearchEngine,
                    onJavaScriptChange = viewModel::setBrowserJavaScriptEnabled,
                    onCookiesChange = viewModel::setBrowserCookiesEnabled,
                    onDesktopModeChange = viewModel::setBrowserDesktopModeEnabled,
                    onClearHistory = viewModel::clearBrowserHistory,
                    onClearBrowsingData = {
                        viewModel.clearBrowserHistory()
                        state.clearWebData()
                        CookieManager.getInstance().removeAllCookies {
                            CookieManager.getInstance().flush()
                            Toast.makeText(context, "History, cookies and cache cleared", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onReset = viewModel::resetBrowserSettings,
                    onClose = { panel = BrowserPanel.NONE },
                )
                BrowserPanel.TABS -> TabsPanel(
                    tabs = state.tabs,
                    activeTabId = state.currentTab.id,
                    onSelect = { id ->
                        if (state.selectTab(id)) viewModel.clearCandidates()
                        panel = BrowserPanel.NONE
                    },
                    onCloseTab = { id ->
                        val wasActive = state.currentTab.id == id
                        state.closeTab(id)
                        if (wasActive) viewModel.clearCandidates()
                    },
                    onNewTab = {
                        state.newTab()
                        viewModel.clearCandidates()
                        panel = BrowserPanel.NONE
                    },
                    onClose = { panel = BrowserPanel.NONE },
                )
                BrowserPanel.NONE -> when {
                    state.showLanding -> BrowserLanding(
                        state = state,
                        onNavigate = {
                            viewModel.clearCandidates()
                            state.navigate(it, settings.searchEngine)
                        },
                        onOpenHistory = { panel = BrowserPanel.HISTORY },
                        onOpenBookmarks = { panel = BrowserPanel.BOOKMARKS },
                        onOpenSettings = { panel = BrowserPanel.SETTINGS },
                    )
                    else -> key(state.currentTab.id) {
                        BrowserPage(state, state.currentTab, viewModel, settings)
                    }
                }
            }
        }
        if (panel == BrowserPanel.NONE && isResolvingPage) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(modifier = Modifier.width(48.dp), color = SunnyYellow)
                Spacer(Modifier.width(12.dp))
                Text("Extracting video…")
            }
        } else if ((panel == BrowserPanel.NONE || panel == BrowserPanel.DETECTED) && candidates.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(56.dp)
                    .background(Color(0xFFE9DFFF), RoundedCornerShape(18.dp))
                    .clickable {
                        panel = if (panel == BrowserPanel.DETECTED) BrowserPanel.NONE else BrowserPanel.DETECTED
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (panel == BrowserPanel.DETECTED) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Download,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (panel == BrowserPanel.DETECTED) "Back to browser" else "${candidates.size} video detected")
            }
        }
    }

    when (val inspection = formatInspection) {
        MediaFormatInspectionState.Hidden -> Unit
        is MediaFormatInspectionState.Loading -> MediaFormatSheet(
            candidate = inspection.candidate,
            isLoading = true,
            options = emptyList(),
            errorMessage = null,
            onRetry = viewModel::retryMediaFormats,
            onDismiss = viewModel::dismissMediaFormats,
            onDownload = { option -> viewModel.enqueue(inspection.candidate, option) },
            onCopyLink = { copyDownloadUrl(context, it) },
        )
        is MediaFormatInspectionState.Ready -> MediaFormatSheet(
            candidate = inspection.candidate,
            isLoading = false,
            options = inspection.options,
            errorMessage = null,
            onRetry = viewModel::retryMediaFormats,
            onDismiss = viewModel::dismissMediaFormats,
            onDownload = { option ->
                viewModel.enqueue(inspection.candidate, option)
                Toast.makeText(context, "Added to downloads", Toast.LENGTH_SHORT).show()
                panel = BrowserPanel.NONE
            },
            onCopyLink = { copyDownloadUrl(context, it) },
        )
        is MediaFormatInspectionState.Failed -> MediaFormatSheet(
            candidate = inspection.candidate,
            isLoading = false,
            options = emptyList(),
            errorMessage = inspection.message,
            onRetry = viewModel::retryMediaFormats,
            onDismiss = viewModel::dismissMediaFormats,
            onDownload = { option -> viewModel.enqueue(inspection.candidate, option) },
            onCopyLink = { copyDownloadUrl(context, it) },
        )
    }
}

@Composable
private fun DetectedMediaPanel(
    candidates: List<MediaCandidate>,
    onClose: () -> Unit,
    onInspect: (MediaCandidate) -> Unit,
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
                CandidateRow(candidate) { onInspect(candidate) }
            }
        }
    }
}

@Composable
private fun PanelHeader(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 2,
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Back to browser")
        }
    }
    HorizontalDivider()
}

@Composable
private fun EmptyBrowserPanel(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryPanel(
    entries: List<BrowserHistoryEntry>,
    onOpen: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PanelHeader(
            title = "History",
            subtitle = "Pages you visited are stored only on this device.",
            onClose = onClose,
            actionLabel = "Clear".takeIf { entries.isNotEmpty() },
            onAction = onClear.takeIf { entries.isNotEmpty() },
        )
        if (entries.isEmpty()) {
            EmptyBrowserPanel(Icons.Default.History, "No browsing history yet")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = BrowserHistoryEntry::url) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(entry.url) }
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(42.dp).background(Color(0xFFE8F0FE), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF3F67B3))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.title.ifBlank { Uri.parse(entry.url).host ?: entry.url },
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                entry.url,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                DateUtils.getRelativeTimeSpanString(entry.visitedAt).toString(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 74.dp))
                }
            }
        }
    }
}

@Composable
private fun BookmarksPanel(
    bookmarks: List<BrowserBookmark>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PanelHeader(
            title = "Bookmarks",
            subtitle = "Saved pages are available after restarting the app.",
            onClose = onClose,
            actionLabel = "Clear".takeIf { bookmarks.isNotEmpty() },
            onAction = onClear.takeIf { bookmarks.isNotEmpty() },
        )
        if (bookmarks.isEmpty()) {
            EmptyBrowserPanel(Icons.Default.BookmarkBorder, "No bookmarks yet")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(bookmarks, key = BrowserBookmark::url) { bookmark ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(bookmark.url) }
                            .padding(start = 20.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(42.dp).background(Color(0xFFFFE69B), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFF7A5B00))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                bookmark.title.ifBlank { Uri.parse(bookmark.url).host ?: bookmark.url },
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                bookmark.url,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { onRemove(bookmark.url) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove bookmark")
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 74.dp))
                }
            }
        }
    }
}

@Composable
private fun TabsPanel(
    tabs: List<BrowserTabState>,
    activeTabId: Long,
    onSelect: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onNewTab: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PanelHeader(
            title = "Tabs",
            subtitle = "${tabs.size} open ${if (tabs.size == 1) "tab" else "tabs"}",
            onClose = onClose,
            actionLabel = "New tab",
            onAction = onNewTab,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tabs, key = BrowserTabState::id) { tab ->
                val selected = tab.id == activeTabId
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(tab.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) Color(0xFFFFE69B) else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (tab.showLanding) Icons.Default.Home else Icons.Default.OpenInBrowser,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (tab.showLanding) "New tab" else tab.pageTitle.ifBlank { "Web page" },
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (tab.showLanding) "BrightFetch home" else tab.address,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (selected) Text("Current", fontSize = 11.sp, color = Color(0xFF725700))
                        IconButton(onClick = { onCloseTab(tab.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Close tab")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserSettingsPanel(
    settings: BrowserSettings,
    onSearchEngineChange: (BrowserSearchEngine) -> Unit,
    onJavaScriptChange: (Boolean) -> Unit,
    onCookiesChange: (Boolean) -> Unit,
    onDesktopModeChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onClearBrowsingData: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PanelHeader(
            title = "Browser settings",
            subtitle = "These preferences are stored on this device.",
            onClose = onClose,
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Search engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            BrowserSearchEngine.entries.forEach { engine ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSearchEngineChange(engine) }.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.searchEngine == engine, onClick = { onSearchEngineChange(engine) })
                    Spacer(Modifier.width(8.dp))
                    Text(engine.displayName)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            SettingsToggleRow(
                title = "JavaScript",
                description = "Required by most modern sites and video detection.",
                checked = settings.javaScriptEnabled,
                onCheckedChange = onJavaScriptChange,
            )
            SettingsToggleRow(
                title = "Accept cookies",
                description = "Keeps sign-ins and site preferences between pages.",
                checked = settings.cookiesEnabled,
                onCheckedChange = onCookiesChange,
            )
            SettingsToggleRow(
                title = "Desktop site",
                description = "Request the desktop version and reload open pages.",
                checked = settings.desktopModeEnabled,
                onCheckedChange = onDesktopModeChange,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Text("Privacy and storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onClearHistory, modifier = Modifier.fillMaxWidth()) {
                Text("Clear browsing history")
            }
            OutlinedButton(onClick = onClearBrowsingData, modifier = Modifier.fillMaxWidth()) {
                Text("Clear history, cookies and cache")
            }
            TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text("Reset browser settings")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BrowserTopBar(
    state: BrowserState,
    onHome: () -> Unit,
    onSubmit: () -> Unit,
    onNewTab: () -> Unit,
    searchEngineName: String,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onShowTabs: () -> Unit,
    onShowHistory: () -> Unit,
    onShowBookmarks: () -> Unit,
    onShowSettings: () -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onOpenExternal: () -> Unit,
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
            placeholder = { Text("Search with $searchEngineName or enter URL", maxLines = 1) },
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
            Modifier
                .size(38.dp)
                .background(Color.Transparent, RoundedCornerShape(8.dp))
                .clickable(onClick = onShowTabs),
            contentAlignment = Alignment.Center,
        ) {
            Text(state.tabCount.toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Ink)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(if (isBookmarked) "Remove bookmark" else "Bookmark this page") },
                    leadingIcon = {
                        Icon(if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = null)
                    },
                    enabled = isHttpUrl(state.address),
                    onClick = {
                        menuExpanded = false
                        onToggleBookmark()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Bookmarks") },
                    leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onShowBookmarks()
                    },
                )
                DropdownMenuItem(
                    text = { Text("History") },
                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onShowHistory()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Tabs") },
                    leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onShowTabs()
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(if (state.progress in 0.01f..0.99f) "Stop loading" else "Refresh") },
                    leadingIcon = {
                        Icon(
                            if (state.progress in 0.01f..0.99f) Icons.Default.Stop else Icons.Default.Refresh,
                            contentDescription = null,
                        )
                    },
                    enabled = state.webView != null,
                    onClick = {
                        menuExpanded = false
                        if (state.progress in 0.01f..0.99f) onStop() else onRefresh()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Share page") },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    enabled = isHttpUrl(state.address),
                    onClick = {
                        menuExpanded = false
                        onShare()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Copy page URL") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    enabled = isHttpUrl(state.address),
                    onClick = {
                        menuExpanded = false
                        onCopy()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Open in another browser") },
                    leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                    enabled = isHttpUrl(state.address),
                    onClick = {
                        menuExpanded = false
                        onOpenExternal()
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onShowSettings()
                    },
                )
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
                        onHome()
                    },
                )
            }
        }
    }
}

@Composable
private fun BrowserLanding(
    state: BrowserState,
    onNavigate: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
) {
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
                    QuickAction(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Return to page",
                        state.hasHiddenPage,
                        state::restoreHiddenPage,
                    )
                    QuickAction(Icons.AutoMirrored.Filled.ArrowForward, "Forward", state.canGoForward, state::goForward)
                    QuickAction(Icons.Default.Refresh, "Refresh", state.webView != null, state::reload)
                    QuickAction(Icons.Default.Bookmark, "Bookmarks", onClick = onOpenBookmarks)
                    QuickAction(Icons.Default.History, "History", onClick = onOpenHistory)
                    QuickAction(Icons.Default.Tune, "Settings", onClick = onOpenSettings)
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
private fun BrowserPage(
    state: BrowserState,
    tab: BrowserTabState,
    viewModel: MainViewModel,
    browserSettings: BrowserSettings,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reporter = tab.mediaReporter ?: BrowserMediaReporter(viewModel) {
        state.tabs.getOrNull(state.activeTabIndex)?.id == tab.id && !tab.showLanding
    }.also {
        tab.mediaReporter = it
    }

    DisposableEffect(lifecycleOwner, tab.id) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> tab.webView?.onResume()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> tab.webView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            tab.webView?.onResume()
        } else {
            tab.webView?.onPause()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            tab.webView?.onPause()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            tab.webView?.also { existing ->
                (existing.parent as? ViewGroup)?.removeView(existing)
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    existing.onResume()
                }
            } ?: WebView(context).apply {
                tab.mobileUserAgent = settings.userAgentString
                settings.javaScriptEnabled = browserSettings.javaScriptEnabled
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.useWideViewPort = true
                CookieManager.getInstance().setAcceptCookie(browserSettings.cookiesEnabled)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, browserSettings.cookiesEnabled)
                reporter.updateContext(userAgent = settings.userAgentString)
                addJavascriptInterface(MediaJavascriptBridge(reporter), MEDIA_BRIDGE_NAME)

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        if (!tab.showLanding) tab.progress = newProgress / 100f
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        if (!tab.showLanding && !title.isNullOrBlank()) {
                            tab.pageTitle = title
                            reporter.updateContext(pageTitle = title)
                            view?.url?.takeIf(::isHttpUrl)?.let { url ->
                                viewModel.recordBrowserVisit(url, title)
                            }
                        }
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        if (!tab.showLanding && !url.isNullOrBlank()) {
                            tab.address = url
                            tab.pageTitle = titleFromUrl(url)
                            if (state.currentTab.id == tab.id) viewModel.clearCandidates()
                            reporter.updateContext(pageUrl = url, pageTitle = tab.pageTitle)
                            reporter.resolveKnownPage(url)
                        }
                        updateNavigationState(tab, view)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        updateNavigationState(tab, view)
                        if (tab.showLanding) return
                        tab.progress = 1f
                        reporter.updateContext(pageUrl = url, pageTitle = tab.pageTitle)
                        url?.takeIf(::isHttpUrl)?.let { visitedUrl ->
                            viewModel.recordBrowserVisit(visitedUrl, tab.pageTitle)
                        }
                        if (view != null) installMediaObserver(view)
                    }

                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        super.onPageCommitVisible(view, url)
                        if (!tab.showLanding && view != null) installMediaObserver(view)
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
                applyBrowserSettings(this, tab, browserSettings, reporter)
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onResume()
            }.also { tab.webView = it }
        },
        update = { webView ->
            applyBrowserSettings(webView, tab, browserSettings, reporter)
            tab.pendingUrl?.let { target ->
                tab.pendingUrl = null
                webView.loadUrl(target)
            }
            if (tab.needsMediaRescan) {
                tab.needsMediaRescan = false
                webView.url?.takeIf(::isHttpUrl)?.let { url ->
                    reporter.updateContext(pageUrl = url, pageTitle = tab.pageTitle)
                    reporter.resolveKnownPage(url)
                }
                resetMediaObserver(webView)
            }
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun applyBrowserSettings(
    webView: WebView,
    tab: BrowserTabState,
    browserSettings: BrowserSettings,
    reporter: BrowserMediaReporter,
) {
    val previousJavaScript = tab.appliedJavaScript
    tab.appliedJavaScript = browserSettings.javaScriptEnabled
    webView.settings.javaScriptEnabled = browserSettings.javaScriptEnabled
    CookieManager.getInstance().setAcceptCookie(browserSettings.cookiesEnabled)
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, browserSettings.cookiesEnabled)

    val mobileUserAgent = tab.mobileUserAgent
        ?: WebSettings.getDefaultUserAgent(webView.context).also { tab.mobileUserAgent = it }
    val targetUserAgent = if (browserSettings.desktopModeEnabled) DESKTOP_USER_AGENT else mobileUserAgent
    if (webView.settings.userAgentString != targetUserAgent) {
        webView.settings.userAgentString = targetUserAgent
    }
    webView.settings.loadWithOverviewMode = browserSettings.desktopModeEnabled
    reporter.updateContext(userAgent = targetUserAgent)

    val previousDesktopMode = tab.appliedDesktopMode
    tab.appliedDesktopMode = browserSettings.desktopModeEnabled
    val desktopChanged = previousDesktopMode != null && previousDesktopMode != browserSettings.desktopModeEnabled
    val javaScriptChanged = previousJavaScript != null && previousJavaScript != browserSettings.javaScriptEnabled
    if ((desktopChanged || javaScriptChanged) && webView.url != null) {
        webView.reload()
    }
}

private fun updateNavigationState(tab: BrowserTabState, webView: WebView?) {
    tab.canGoBack = webView?.canGoBack() == true
    tab.canGoForward = webView?.canGoForward() == true
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
            document.querySelectorAll('a[target="_blank"]').forEach(function(anchor) {
              anchor.target = '_self';
            });
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
          window.__brightFetchResetAndScan = function() {
            seen.clear();
            window.__brightFetchScan();
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

private fun resetMediaObserver(webView: WebView) {
    webView.evaluateJavascript(
        "if (window.__brightFetchResetAndScan) { window.__brightFetchResetAndScan(); }",
        null,
    )
}

internal class BrowserMediaReporter(
    private val viewModel: MainViewModel,
    private val isActiveTab: () -> Boolean = { true },
) {
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
            if (!isActiveTab()) return@post
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
        if (!isActiveTab()) return
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
            if (!isActiveTab()) return@post
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
            if (!isActiveTab()) return@post
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

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private fun isHttpUrl(value: String): Boolean = BrowserNavigation.isHttpUrl(value)

private fun titleFromUrl(url: String): String = runCatching {
    java.net.URI(url).host?.removePrefix("www.")
}.getOrNull().orEmpty().ifBlank { "Loading…" }

private fun copyPageUrl(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Page URL", url))
    Toast.makeText(context, "Page URL copied", Toast.LENGTH_SHORT).show()
}

private fun copyDownloadUrl(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Video download URL", url))
    Toast.makeText(context, "Download URL copied", Toast.LENGTH_SHORT).show()
}

private fun sharePage(context: Context, url: String, title: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share page"))
}

@Composable
private fun CandidateRow(candidate: MediaCandidate, onInspect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onInspect)
            .padding(horizontal = 20.dp, vertical = 10.dp),
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
        Button(onClick = onInspect) { Text("Formats") }
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
