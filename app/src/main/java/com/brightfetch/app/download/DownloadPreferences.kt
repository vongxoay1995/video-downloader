package com.brightfetch.app.download

import android.content.Context

/** A local download preference applied when a new WorkManager request is scheduled. */
object DownloadPreferences {
    private const val FILE = "brightfetch_download_preferences"
    private const val WIFI_ONLY = "wifi_only"

    fun wifiOnly(context: Context): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        .getBoolean(WIFI_ONLY, false)

    fun setWifiOnly(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(WIFI_ONLY, enabled).apply()
    }
}
