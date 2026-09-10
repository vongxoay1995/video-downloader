package com.brightfetch.app.model

import androidx.work.WorkInfo
import android.net.Uri
import java.util.UUID

data class DownloadSnapshot(
    val id: UUID,
    val fileName: String,
    val url: String,
    val state: WorkInfo.State,
    val progress: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val error: String? = null,
    val outputPath: String? = null,
)

data class DownloadedVideo(
    val uri: Uri,
    val name: String,
    val size: Long,
    val modifiedAt: Long,
    val mimeType: String,
    val displayPath: String,
    val downloadedAt: Long = modifiedAt,
    val durationMillis: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
)
