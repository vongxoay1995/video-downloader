package com.brightfetch.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.brightfetch.app.download.DownloadContract
import com.brightfetch.app.download.DownloadScheduler
import com.brightfetch.app.browser.TikTokPageResolver
import com.brightfetch.app.browser.BrowserBookmark
import com.brightfetch.app.browser.BrowserHistoryEntry
import com.brightfetch.app.browser.BrowserRepository
import com.brightfetch.app.browser.BrowserSearchEngine
import com.brightfetch.app.browser.BrowserSettings
import com.brightfetch.app.browser.MediaUrlClassifier
import com.brightfetch.app.browser.MediaFormatResolver
import com.brightfetch.app.browser.News24hPageResolver
import com.brightfetch.app.browser.Kenh14PageResolver
import com.brightfetch.app.browser.MediaPageIdentity
import com.brightfetch.app.model.DownloadSnapshot
import com.brightfetch.app.model.DownloadedVideo
import com.brightfetch.app.model.MediaCandidate
import com.brightfetch.app.model.MediaDownloadOption
import com.brightfetch.app.model.MediaFormatInspectionState
import com.brightfetch.app.storage.PublicVideoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val workManager = WorkManager.getInstance(application)
    private val browserRepository = BrowserRepository(application)
    private val mediaFormatResolver = MediaFormatResolver()

    val browserHistory: StateFlow<List<BrowserHistoryEntry>> = browserRepository.history
    val browserBookmarks: StateFlow<List<BrowserBookmark>> = browserRepository.bookmarks
    val browserSettings: StateFlow<BrowserSettings> = browserRepository.settings

    private val _candidates = MutableStateFlow<List<MediaCandidate>>(emptyList())
    val candidates: StateFlow<List<MediaCandidate>> = _candidates.asStateFlow()

    private val _isResolvingPage = MutableStateFlow(false)
    val isResolvingPage: StateFlow<Boolean> = _isResolvingPage.asStateFlow()

    private val _mediaFormatInspection = MutableStateFlow<MediaFormatInspectionState>(
        MediaFormatInspectionState.Hidden,
    )
    val mediaFormatInspection: StateFlow<MediaFormatInspectionState> =
        _mediaFormatInspection.asStateFlow()

    private var pageResolverJob: Job? = null
    private var resolvingPageUrl: String? = null
    private var resolverGeneration = 0L
    private var resolvedTikTokVideoId: String? = null
    private var resolvedPreferredPageKey: String? = null
    private var mediaFormatJob: Job? = null
    private var mediaFormatGeneration = 0L

    private val _downloads = MutableStateFlow<List<DownloadSnapshot>>(emptyList())
    val downloads: StateFlow<List<DownloadSnapshot>> = _downloads.asStateFlow()

    private val _videos = MutableStateFlow<List<DownloadedVideo>>(emptyList())
    val videos: StateFlow<List<DownloadedVideo>> = _videos.asStateFlow()

    private val _playingVideo = MutableStateFlow<DownloadedVideo?>(null)
    val playingVideo: StateFlow<DownloadedVideo?> = _playingVideo.asStateFlow()

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                PublicVideoStore.migrateLegacyFiles(application)
            }
            while (isActive) {
                refreshDownloads()
                refreshVideos()
                delay(750)
            }
        }
    }

    fun addCandidate(candidate: MediaCandidate) {
        if (MediaUrlClassifier.isLikelyAudio(candidate.url)) return
        if (BuildConfig.DEBUG) {
            Log.d(
                MEDIA_LOG_TAG,
                "Detected video URL: ${candidate.url} | page=${candidate.pageUrl} | mime=${candidate.mimeType}",
            )
        }
        _candidates.update { current ->
            val exactIndex = current.indexOfFirst { it.url == candidate.url }
            if (exactIndex >= 0) {
                val merged = mergeCandidate(current[exactIndex], candidate)
                if (merged == current[exactIndex]) return@update current
                return@update current.toMutableList().apply { this[exactIndex] = merged }
            }

            val candidatePageKey = MediaPageIdentity.key(candidate.pageUrl)
            if (candidatePageKey != null && candidatePageKey == resolvedPreferredPageKey) {
                return@update current
            }

            if (candidate.isHls && candidatePageKey != null) {
                val existingIndex = current.indexOfFirst { existing ->
                    existing.isHls && MediaPageIdentity.key(existing.pageUrl) == candidatePageKey
                }
                if (existingIndex >= 0) {
                    val existing = current[existingIndex]
                    // A WebView requests the master and then one or more child playlists. Keep a
                    // single entry for the page, preferring a clearly named master; qualities are
                    // expanded later by MediaFormatResolver instead of appearing as fake videos.
                    if (hlsCandidateScore(candidate) <= hlsCandidateScore(existing)) {
                        return@update current
                    }
                    return@update current.toMutableList().apply { this[existingIndex] = candidate }
                }
            }

            val videoId = TikTokPageResolver.videoPageId(candidate.pageUrl)
            if (videoId != null && videoId == resolvedTikTokVideoId) {
                // The native page resolver has already selected this post's canonical playAddr.
                return@update current
            }

            if (videoId != null) {
                val existingIndex = current.indexOfFirst {
                    TikTokPageResolver.videoPageId(it.pageUrl) == videoId
                }
                if (existingIndex >= 0) {
                    val existing = current[existingIndex]
                    if (tiktokCandidateScore(candidate) <= tiktokCandidateScore(existing)) {
                        return@update current
                    }
                    return@update current.toMutableList().apply { this[existingIndex] = candidate }
                }
            }
            (listOf(candidate) + current).take(20)
        }
    }

    fun clearCandidates() {
        dismissMediaFormats()
        resolverGeneration += 1
        pageResolverJob?.cancel()
        pageResolverJob = null
        resolvingPageUrl = null
        resolvedTikTokVideoId = null
        resolvedPreferredPageKey = null
        _isResolvingPage.value = false
        _candidates.value = emptyList()
    }

    fun resolveKnownPage(
        pageUrl: String,
        pageTitle: String,
        userAgent: String?,
        cookie: String?,
    ) {
        val isTikTokPage = TikTokPageResolver.supports(pageUrl)
        val isNews24hPage = News24hPageResolver.supports(pageUrl)
        val isKenh14Page = Kenh14PageResolver.supports(pageUrl)
        if (!isTikTokPage && !isNews24hPage && !isKenh14Page) return
        if (pageResolverJob?.isActive == true && resolvingPageUrl == pageUrl) return

        pageResolverJob?.cancel()
        val generation = ++resolverGeneration
        resolvingPageUrl = pageUrl
        _isResolvingPage.value = true
        pageResolverJob = viewModelScope.launch {
            try {
                val resolvedCandidate = withContext(Dispatchers.IO) {
                    when {
                        isTikTokPage -> TikTokPageResolver.resolve(pageUrl, userAgent, cookie)?.let { resolved ->
                            ResolvedCandidate(
                                candidate = MediaCandidate(
                                    url = resolved.mediaUrl,
                                    title = resolvedTitle(pageTitle, resolved.pageUrl),
                                    pageUrl = resolved.pageUrl,
                                    mimeType = resolved.mimeType ?: "video/mp4",
                                    userAgent = userAgent,
                                    cookie = resolved.cookie,
                                    width = resolved.width,
                                    height = resolved.height,
                                    durationSeconds = resolved.durationSeconds,
                                    contentLengthBytes = resolved.contentLengthBytes,
                                    preferredFileName = resolvedTitle(pageTitle, resolved.pageUrl),
                                ),
                                preferredTikTokVideoId = TikTokPageResolver.videoPageId(resolved.pageUrl),
                            )
                        }
                        isNews24hPage -> News24hPageResolver.resolve(pageUrl, userAgent, cookie)?.let { resolved ->
                            ResolvedCandidate(
                                candidate = MediaCandidate(
                                    url = resolved.mediaUrl,
                                    title = resolved.title ?: pageTitle,
                                    pageUrl = resolved.pageUrl,
                                    mimeType = resolved.mimeType,
                                    userAgent = userAgent,
                                    cookie = resolved.cookie,
                                    preferredFileName = resolved.title ?: pageTitle,
                                ),
                            )
                        }
                        else -> Kenh14PageResolver.resolve(pageUrl, userAgent, cookie)?.let { resolved ->
                            ResolvedCandidate(
                                candidate = MediaCandidate(
                                    url = resolved.mediaUrl,
                                    title = resolved.title,
                                    pageUrl = resolved.pageUrl,
                                    mimeType = resolved.mimeType,
                                    userAgent = userAgent,
                                    cookie = resolved.cookie,
                                    contentLengthBytes = resolved.contentLengthBytes,
                                    preferredFileName = resolved.title,
                                ),
                            )
                        }
                    }
                }
                resolvedCandidate?.let { resolved ->
                    currentCoroutineContext().ensureActive()
                    if (resolverGeneration != generation) return@let
                    if (resolved.preferredTikTokVideoId != null) {
                        addResolvedTikTokCandidate(resolved.candidate, resolved.preferredTikTokVideoId)
                    } else {
                        addResolvedPageCandidate(resolved.candidate)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (BuildConfig.DEBUG) Log.w(MEDIA_LOG_TAG, "Page video extraction failed: $pageUrl", error)
            } finally {
                if (resolverGeneration == generation) {
                    _isResolvingPage.value = false
                    resolvingPageUrl = null
                    pageResolverJob = null
                }
            }
        }
    }

    private data class ResolvedCandidate(
        val candidate: MediaCandidate,
        val preferredTikTokVideoId: String? = null,
    )

    private fun addResolvedTikTokCandidate(candidate: MediaCandidate, videoId: String?) {
        if (videoId == null) {
            addCandidate(candidate)
            return
        }
        resolvedTikTokVideoId = videoId
        if (BuildConfig.DEBUG) {
            Log.d(MEDIA_LOG_TAG, "Selected TikTok playAddr: ${candidate.url} | video=$videoId")
        }
        addResolvedPageCandidate(candidate) { existing ->
            TikTokPageResolver.videoPageId(existing.pageUrl) == videoId
        }
    }

    private fun addResolvedPageCandidate(
        candidate: MediaCandidate,
        additionalMatch: (MediaCandidate) -> Boolean = { false },
    ) {
        val key = MediaPageIdentity.key(candidate.pageUrl)
        resolvedPreferredPageKey = key
        _candidates.update { current ->
            listOf(candidate) + current.filterNot { existing ->
                existing.url == candidate.url ||
                    (key != null && MediaPageIdentity.key(existing.pageUrl) == key) ||
                    additionalMatch(existing)
            }
        }
    }

    private fun tiktokCandidateScore(candidate: MediaCandidate): Int {
        if (MediaUrlClassifier.isLikelyAudio(candidate.url)) return Int.MIN_VALUE
        val normalized = candidate.url.lowercase()
        var score = 0
        if ("/video/tos/" in normalized) score += 20
        if ("mime_type=video" in normalized || candidate.mimeType?.startsWith("video/") == true) score += 20
        if ("webapp-prime" in normalized) score += 20
        else if ("webapp" in normalized) score += 10
        return score
    }

    private fun hlsCandidateScore(candidate: MediaCandidate): Int {
        val path = runCatching { java.net.URI(candidate.url).path.lowercase() }.getOrDefault("")
        val fileName = path.substringAfterLast('/')
        return when {
            "master" in fileName -> 100
            "master" in path -> 80
            "playlist" in fileName -> 40
            else -> 0
        }
    }

    private fun mergeCandidate(existing: MediaCandidate, incoming: MediaCandidate): MediaCandidate {
        val preferredTitle = if (candidateTitleScore(incoming.title) > candidateTitleScore(existing.title)) {
            incoming.title
        } else {
            existing.title
        }
        return existing.copy(
            title = preferredTitle,
            pageUrl = incoming.pageUrl?.takeIf(String::isNotBlank) ?: existing.pageUrl,
            mimeType = incoming.mimeType?.takeIf(String::isNotBlank) ?: existing.mimeType,
            userAgent = incoming.userAgent?.takeIf(String::isNotBlank) ?: existing.userAgent,
            cookie = incoming.cookie?.takeIf(String::isNotBlank) ?: existing.cookie,
            width = incoming.width?.takeIf { it > 0 } ?: existing.width,
            height = incoming.height?.takeIf { it > 0 } ?: existing.height,
            durationSeconds = incoming.durationSeconds?.takeIf { it >= 0L } ?: existing.durationSeconds,
            contentLengthBytes = incoming.contentLengthBytes?.takeIf { it > 0L }
                ?: existing.contentLengthBytes,
            preferredFileName = incoming.preferredFileName?.takeIf(String::isNotBlank)
                ?: existing.preferredFileName,
        )
    }

    private fun candidateTitleScore(title: String): Int {
        val normalized = title.trim().lowercase()
        if (normalized.isBlank()) return 0
        if (normalized in setOf("brightfetch", "detected video", "error response", "webpage not available")) {
            return 1
        }
        return 10 + title.length.coerceAtMost(100)
    }

    private fun resolvedTitle(pageTitle: String, pageUrl: String): String {
        val usefulPageTitle = pageTitle.takeIf {
            it.isNotBlank() &&
                !it.equals("BrightFetch", ignoreCase = true) &&
                !it.equals("Detected video", ignoreCase = true)
        }
        if (usefulPageTitle != null) return usefulPageTitle
        val videoId = Regex("/video/(\\d+)").find(pageUrl)?.groupValues?.getOrNull(1)
        return videoId?.let { "TikTok_$it" } ?: "TikTok video"
    }

    fun enqueue(candidate: MediaCandidate) {
        DownloadScheduler.enqueue(getApplication(), candidate)
    }

    fun inspectMediaFormats(candidate: MediaCandidate) {
        mediaFormatJob?.cancel()
        val generation = ++mediaFormatGeneration
        _mediaFormatInspection.value = MediaFormatInspectionState.Loading(candidate)
        mediaFormatJob = viewModelScope.launch {
            try {
                val options = mediaFormatResolver.resolve(candidate)
                currentCoroutineContext().ensureActive()
                if (mediaFormatGeneration != generation) return@launch
                if (options.isEmpty()) {
                    _mediaFormatInspection.value = MediaFormatInspectionState.Failed(
                        candidate,
                        "No downloadable video format was found.",
                    )
                } else {
                    if (BuildConfig.DEBUG) {
                        options.forEach { option ->
                            Log.d(
                                MEDIA_LOG_TAG,
                                "Download option: ${option.label} | url=${option.downloadUrl} | " +
                                    "size=${option.estimatedSizeBytes} exact=${option.sizeIsExact}",
                            )
                        }
                    }
                    _mediaFormatInspection.value = MediaFormatInspectionState.Ready(candidate, options)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (mediaFormatGeneration != generation) return@launch
                if (BuildConfig.DEBUG) {
                    Log.w(MEDIA_LOG_TAG, "Cannot inspect video formats: ${candidate.url}", error)
                }
                _mediaFormatInspection.value = MediaFormatInspectionState.Failed(
                    candidate,
                    error.message?.take(240)?.takeIf(String::isNotBlank)
                        ?: "Could not inspect this video. The link may have expired.",
                )
            } finally {
                if (mediaFormatGeneration == generation) mediaFormatJob = null
            }
        }
    }

    fun retryMediaFormats() {
        val candidate = when (val state = _mediaFormatInspection.value) {
            is MediaFormatInspectionState.Failed -> state.candidate
            is MediaFormatInspectionState.Ready -> state.candidate
            is MediaFormatInspectionState.Loading -> state.candidate
            MediaFormatInspectionState.Hidden -> null
        }
        candidate?.let(::inspectMediaFormats)
    }

    fun enqueue(candidate: MediaCandidate, option: MediaDownloadOption) {
        if (!option.isAvailable) return
        val selected = option.toCandidate(candidate)
        Log.i(
            MEDIA_LOG_TAG,
            "Selected download URL: ${selected.url} | format=${option.outputExtension} | " +
                "quality=${option.label} | estimatedBytes=${option.estimatedSizeBytes}",
        )
        DownloadScheduler.enqueue(getApplication(), selected)
        dismissMediaFormats()
    }

    fun dismissMediaFormats() {
        mediaFormatGeneration += 1
        mediaFormatJob?.cancel()
        mediaFormatJob = null
        _mediaFormatInspection.value = MediaFormatInspectionState.Hidden
    }

    fun recordBrowserVisit(url: String, title: String) {
        browserRepository.recordVisit(url, title)
    }

    fun toggleBrowserBookmark(url: String, title: String): Boolean =
        browserRepository.toggleBookmark(url, title)

    fun removeBrowserBookmark(url: String) {
        browserRepository.removeBookmark(url)
    }

    fun isBrowserBookmarked(url: String): Boolean = browserRepository.isBookmarked(url)

    fun clearBrowserHistory() {
        browserRepository.clearHistory()
    }

    fun clearBrowserBookmarks() {
        browserRepository.clearBookmarks()
    }

    fun setBrowserSearchEngine(searchEngine: BrowserSearchEngine) {
        browserRepository.updateSettings { it.copy(searchEngine = searchEngine) }
    }

    fun setBrowserJavaScriptEnabled(enabled: Boolean) {
        browserRepository.updateSettings { it.copy(javaScriptEnabled = enabled) }
    }

    fun setBrowserCookiesEnabled(enabled: Boolean) {
        browserRepository.updateSettings { it.copy(cookiesEnabled = enabled) }
    }

    fun setBrowserDesktopModeEnabled(enabled: Boolean) {
        browserRepository.updateSettings { it.copy(desktopModeEnabled = enabled) }
    }

    fun resetBrowserSettings() {
        browserRepository.resetSettings()
    }

    fun cancel(id: java.util.UUID) {
        workManager.cancelWorkById(id)
    }

    fun deleteVideo(video: DownloadedVideo) {
        viewModelScope.launch(Dispatchers.IO) {
            PublicVideoStore.delete(getApplication(), video)
            refreshVideos()
        }
    }

    fun openVideoPlayer(video: DownloadedVideo) {
        _playingVideo.value = video
    }

    fun closeVideoPlayer() {
        _playingVideo.value = null
    }

    private suspend fun refreshDownloads() = withContext(Dispatchers.IO) {
        val infos = runCatching {
            workManager.getWorkInfosByTag(DownloadContract.TAG).get()
        }.getOrDefault(emptyList())
        _downloads.value = infos
            .sortedBy { info -> if (info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED) 0 else 1 }
            .map { info ->
                DownloadSnapshot(
                    id = info.id,
                    fileName = info.tags.firstOrNull { it.startsWith(DownloadContract.TAG_NAME_PREFIX) }
                        ?.removePrefix(DownloadContract.TAG_NAME_PREFIX)
                        .orEmpty(),
                    url = "",
                    state = info.state,
                    progress = info.progress.getInt(DownloadContract.KEY_PROGRESS, if (info.state == WorkInfo.State.SUCCEEDED) 100 else 0),
                    downloadedBytes = info.progress.getLong(DownloadContract.KEY_DOWNLOADED, 0L),
                    totalBytes = info.progress.getLong(DownloadContract.KEY_TOTAL, 0L),
                    error = info.outputData.getString(DownloadContract.KEY_ERROR),
                    outputPath = info.outputData.getString(DownloadContract.KEY_OUTPUT_PATH),
                )
            }
    }

    private suspend fun refreshVideos() = withContext(Dispatchers.IO) {
        _videos.value = PublicVideoStore.query(getApplication())
    }

    private companion object {
        const val MEDIA_LOG_TAG = "BrightFetchMedia"
    }
}
