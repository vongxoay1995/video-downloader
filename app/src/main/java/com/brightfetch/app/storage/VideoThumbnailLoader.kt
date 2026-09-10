package com.brightfetch.app.storage

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import com.brightfetch.app.model.DownloadedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads real frames only for visible library rows and keeps a bounded in-memory cache. */
object VideoThumbnailLoader {
    private const val TARGET_WIDTH = 360
    private const val TARGET_HEIGHT = 240
    private const val CACHE_BYTES = 24 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    suspend fun load(context: Context, video: DownloadedVideo): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${video.uri}|${video.size}|${video.modifiedAt}"
        cache.get(key)?.takeUnless(Bitmap::isRecycled)?.let { return@withContext it }
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(
                    video.uri,
                    Size(TARGET_WIDTH, TARGET_HEIGHT),
                    null,
                )
            } else {
                frameWithRetriever(context, video)
            }
        }.recoverCatching {
            frameWithRetriever(context, video)
        }.getOrNull() ?: return@withContext null

        cache.put(key, bitmap)
        bitmap
    }

    fun remove(video: DownloadedVideo) {
        cache.remove("${video.uri}|${video.size}|${video.modifiedAt}")
    }

    private fun frameWithRetriever(context: Context, video: DownloadedVideo): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, video.uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    0L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    TARGET_WIDTH,
                    TARGET_HEIGHT,
                )
            } else {
                @Suppress("DEPRECATION")
                retriever.frameAtTime
            }
        } finally {
            retriever.release()
        }
    }
}
