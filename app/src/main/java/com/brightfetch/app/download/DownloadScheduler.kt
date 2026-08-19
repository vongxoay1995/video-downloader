package com.brightfetch.app.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.brightfetch.app.model.MediaCandidate

object DownloadScheduler {
    fun enqueue(context: Context, candidate: MediaCandidate) {
        val input = Data.Builder()
            .putString(DownloadContract.KEY_URL, candidate.url)
            .putString(DownloadContract.KEY_FILE_NAME, candidate.suggestedFileName)
            .putString(DownloadContract.KEY_USER_AGENT, candidate.userAgent?.take(512))
            .putString(DownloadContract.KEY_COOKIE, candidate.cookie?.take(4_096))
            .putString(DownloadContract.KEY_REFERER, candidate.pageUrl?.take(2_048))
            .putString(DownloadContract.KEY_MIME_TYPE, candidate.mimeType?.take(128))
            .putBoolean(DownloadContract.KEY_IS_HLS, candidate.isHls)
            .build()
        val request = OneTimeWorkRequestBuilder<VideoDownloadWorker>()
            .setInputData(input)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(DownloadContract.TAG)
            .addTag(DownloadContract.TAG_NAME_PREFIX + candidate.suggestedFileName.take(120))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
