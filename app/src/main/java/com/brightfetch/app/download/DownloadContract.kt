package com.brightfetch.app.download

object DownloadContract {
    const val TAG = "brightfetch_download"
    const val TAG_NAME_PREFIX = "brightfetch_name:"
    const val CHANNEL_ID = "video_downloads"
    const val NOTIFICATION_NAME = "Video downloads"

    const val KEY_URL = "url"
    const val KEY_FILE_NAME = "file_name"
    const val KEY_USER_AGENT = "user_agent"
    const val KEY_COOKIE = "cookie"
    const val KEY_REFERER = "referer"
    const val KEY_IS_HLS = "is_hls"
    const val KEY_MIME_TYPE = "mime_type"

    const val KEY_PROGRESS = "progress"
    const val KEY_DOWNLOADED = "downloaded"
    const val KEY_TOTAL = "total"
    const val KEY_ERROR = "error"
    const val KEY_OUTPUT_PATH = "output_path"
    const val KEY_OUTPUT_URI = "output_uri"
}
