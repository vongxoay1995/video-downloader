package com.brightfetch.app.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import com.brightfetch.app.model.DownloadedVideo
import com.brightfetch.app.model.VideoFileNames
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

object PublicVideoStore {
    const val RELATIVE_DIRECTORY = "Movies/BrightFetch/"
    private const val TAG = "BrightFetchDownload"

    data class PublishedVideo(
        val uri: Uri,
        val name: String,
        val mimeType: String,
        val size: Long,
        val displayPath: String,
    )

    suspend fun publish(
        context: Context,
        source: File,
        requestedName: String,
        mimeType: String?,
    ): PublishedVideo {
        require(source.isFile && source.length() > 0L) { "Downloaded video is empty" }
        val normalizedName = VideoFileNames.normalize(requestedName, mimeType)
        require(VideoFileNames.hasSupportedExtension(normalizedName)) { "Unsupported video file extension" }
        val resolvedMimeType = VideoFileNames.mimeTypeFor(normalizedName, mimeType)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishWithMediaStore(context, source, normalizedName, resolvedMimeType)
        } else {
            publishLegacy(context, source, normalizedName, resolvedMimeType)
        }
    }

    fun query(context: Context): List<DownloadedVideo> {
        val resolver = context.contentResolver
        val collection = videoCollection()
        val isModernStorage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.MIME_TYPE)
            if (isModernStorage) add(MediaStore.Video.Media.RELATIVE_PATH)
            else add(MediaStore.Video.Media.DATA)
        }.toTypedArray()
        val selection: String
        val args: Array<String>
        if (isModernStorage) {
            selection = "${MediaStore.Video.Media.RELATIVE_PATH}=?"
            args = arrayOf(RELATIVE_DIRECTORY)
        } else {
            @Suppress("DEPRECATION")
            val legacyPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                .resolve("BrightFetch").absolutePath + File.separator + "%"
            selection = "${MediaStore.Video.Media.DATA} LIKE ?"
            args = arrayOf(legacyPath)
        }
        return runCatching {
            resolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val addedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val widthColumn = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightColumn = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val relativePathColumn = if (isModernStorage) {
                    cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
                } else {
                    -1
                }
                val legacyPathColumn = if (isModernStorage) {
                    -1
                } else {
                    cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                }
                buildList {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameColumn).orEmpty()
                        if (!VideoFileNames.hasSupportedExtension(name)) continue
                        val id = cursor.getLong(idColumn)
                        val uri = ContentUris.withAppendedId(collection, id)
                        val size = cursor.getLong(sizeColumn)
                        val modifiedAt = cursor.getLong(modifiedColumn).coerceAtLeast(0L) * 1_000L
                        val downloadedAt = cursor.longOrZero(addedColumn)
                            .coerceAtLeast(0L)
                            .times(1_000L)
                            .takeIf { it > 0L }
                            ?: modifiedAt
                        val storedDuration = cursor.longOrZero(durationColumn).coerceAtLeast(0L)
                        val storedWidth = cursor.intOrZero(widthColumn).coerceAtLeast(0)
                        val storedHeight = cursor.intOrZero(heightColumn).coerceAtLeast(0)
                        val extracted = if (storedDuration <= 0L || storedWidth <= 0 || storedHeight <= 0) {
                            readVideoMetadata(context, uri, size, modifiedAt)
                        } else {
                            VideoMetadata(storedDuration, storedWidth, storedHeight)
                        }
                        val displayPath = if (isModernStorage) {
                            val relativePath = cursor.getString(relativePathColumn)
                                .orEmpty()
                                .ifBlank { RELATIVE_DIRECTORY }
                                .trimStart('/')
                            "/storage/emulated/0/$relativePath$name"
                        } else {
                            cursor.getString(legacyPathColumn).orEmpty()
                        }
                        add(
                            DownloadedVideo(
                                uri = uri,
                                name = name,
                                size = size,
                                modifiedAt = modifiedAt,
                                mimeType = cursor.getString(mimeColumn)
                                    ?: VideoFileNames.mimeTypeFor(name),
                                displayPath = displayPath.ifBlank {
                                    "/storage/emulated/0/$RELATIVE_DIRECTORY$name"
                                },
                                downloadedAt = downloadedAt,
                                durationMillis = storedDuration.takeIf { it > 0L } ?: extracted.durationMillis,
                                width = storedWidth.takeIf { it > 0 } ?: extracted.width,
                                height = storedHeight.takeIf { it > 0 } ?: extracted.height,
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrElse { error ->
            Log.e(TAG, "Cannot query public videos", error)
            emptyList()
        }
    }

    fun delete(context: Context, video: DownloadedVideo): Boolean = runCatching {
        val deleted = context.contentResolver.delete(video.uri, null, null) > 0
        if (deleted) VideoThumbnailLoader.remove(video)
        deleted
    }.getOrElse { error ->
        Log.e(TAG, "Cannot delete uri=${video.uri} path=${video.displayPath}", error)
        false
    }

    suspend fun migrateLegacyFiles(context: Context) {
        val legacyFolder = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?.resolve("BrightFetch") ?: return
        val existing = query(context).associateBy { it.name to it.size }
        legacyFolder.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && VideoFileNames.hasSupportedExtension(it.name) }
            ?.forEach { file ->
                if (existing.containsKey(file.name to file.length())) {
                    file.delete()
                    return@forEach
                }
                runCatching {
                    publish(context, file, file.name, VideoFileNames.mimeTypeFor(file.name))
                    file.delete()
                }.onFailure { error ->
                    Log.e(TAG, "Legacy migration failed for ${file.absolutePath}", error)
                }
            }
    }

    private suspend fun publishWithMediaStore(
        context: Context,
        source: File,
        requestedName: String,
        mimeType: String,
    ): PublishedVideo {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val finalName = uniqueMediaStoreName(context, requestedName)
        val nowMillis = System.currentTimeMillis()
        val nowSeconds = nowMillis / 1_000L
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, finalName)
            put(MediaStore.Video.Media.MIME_TYPE, mimeType)
            put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_DIRECTORY)
            put(MediaStore.Video.Media.DATE_ADDED, nowSeconds)
            put(MediaStore.Video.Media.DATE_MODIFIED, nowSeconds)
            put(MediaStore.Video.Media.DATE_TAKEN, nowMillis)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore could not create the video")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                copyInterruptibly(source, output)
            } ?: throw IllegalStateException("MediaStore could not open the video")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
        val actual = readPublishedMetadata(context, uri, finalName, mimeType, source.length())
        Log.i(
            TAG,
            "Saved public video: uri=$uri path=${actual.displayPath} size=${actual.size} mime=${actual.mimeType}",
        )
        return actual
    }

    @Suppress("DEPRECATION")
    private suspend fun publishLegacy(
        context: Context,
        source: File,
        requestedName: String,
        mimeType: String,
    ): PublishedVideo {
        val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            .resolve("BrightFetch")
        check((folder.exists() || folder.mkdirs()) && folder.isDirectory) {
            "Cannot create public Movies/BrightFetch"
        }
        val destination = uniqueFile(folder, requestedName)
        FileOutputStream(destination).use { output -> copyInterruptibly(source, output) }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(destination.absolutePath),
            arrayOf(mimeType),
            null,
        )
        val uri = Uri.fromFile(destination)
        Log.i(
            TAG,
            "Saved public video: uri=$uri path=${destination.absolutePath} size=${destination.length()} mime=$mimeType",
        )
        return PublishedVideo(uri, destination.name, mimeType, destination.length(), destination.absolutePath)
    }

    private fun uniqueMediaStoreName(context: Context, requestedName: String): String {
        val existingNames = query(context).mapTo(HashSet(), DownloadedVideo::name)
        if (requestedName !in existingNames) return requestedName
        val stem = requestedName.substringBeforeLast('.', requestedName)
        val extension = requestedName.substringAfterLast('.', "mp4")
        var index = 1
        while ("$stem ($index).$extension" in existingNames) index++
        return "$stem ($index).$extension"
    }

    private fun readPublishedMetadata(
        context: Context,
        uri: Uri,
        fallbackName: String,
        fallbackMimeType: String,
        fallbackSize: Long,
    ): PublishedVideo {
        val isModernStorage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = if (isModernStorage) {
            arrayOf(
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.RELATIVE_PATH,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE,
            )
        } else {
            arrayOf(
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE,
            )
        }
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME))
                    .orEmpty().ifBlank { fallbackName }
                val path = if (isModernStorage) {
                    val relativePath = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH),
                    ).orEmpty().ifBlank { RELATIVE_DIRECTORY }.trimStart('/')
                    "/storage/emulated/0/$relativePath$name"
                } else {
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))
                        .orEmpty().ifBlank { "/storage/emulated/0/$RELATIVE_DIRECTORY$name" }
                }
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))
                    .takeIf { it > 0L } ?: fallbackSize
                val storedMime = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE))
                    .orEmpty().ifBlank { fallbackMimeType }
                PublishedVideo(uri, name, storedMime, size, path)
            }
        }.getOrNull() ?: PublishedVideo(
            uri = uri,
            name = fallbackName,
            mimeType = fallbackMimeType,
            size = fallbackSize,
            displayPath = "/storage/emulated/0/$RELATIVE_DIRECTORY$fallbackName",
        )
    }

    private fun uniqueFile(folder: File, requestedName: String): File {
        val preferred = folder.resolve(requestedName)
        if (!preferred.exists()) return preferred
        val stem = requestedName.substringBeforeLast('.', requestedName)
        val extension = requestedName.substringAfterLast('.', "mp4")
        var index = 1
        while (folder.resolve("$stem ($index).$extension").exists()) index++
        return folder.resolve("$stem ($index).$extension")
    }

    private fun readVideoMetadata(
        context: Context,
        uri: Uri,
        size: Long,
        modifiedAt: Long,
    ): VideoMetadata {
        val cacheKey = "$uri|$size|$modifiedAt"
        metadataCache.get(cacheKey)?.let { return it }
        val metadata = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                var width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                var height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
                if (rotation == 90 || rotation == 270) {
                    val originalWidth = width
                    width = height
                    height = originalWidth
                }
                VideoMetadata(duration, width, height)
            } finally {
                retriever.release()
            }
        }.getOrElse { error ->
            Log.w(TAG, "Cannot read video metadata for uri=$uri", error)
            VideoMetadata()
        }
        metadataCache.put(cacheKey, metadata)
        return metadata
    }

    private suspend fun copyInterruptibly(source: File, output: OutputStream) {
        FileInputStream(source).buffered(BUFFER_SIZE).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
            }
        }
    }

    private const val BUFFER_SIZE = 64 * 1024

    private data class VideoMetadata(
        val durationMillis: Long = 0L,
        val width: Int = 0,
        val height: Int = 0,
    )

    private val metadataCache = LruCache<String, VideoMetadata>(200)

    private fun videoCollection(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
}

private fun android.database.Cursor.longOrZero(columnIndex: Int): Long =
    if (columnIndex >= 0 && !isNull(columnIndex)) getLong(columnIndex) else 0L

private fun android.database.Cursor.intOrZero(columnIndex: Int): Int =
    if (columnIndex >= 0 && !isNull(columnIndex)) getInt(columnIndex) else 0
