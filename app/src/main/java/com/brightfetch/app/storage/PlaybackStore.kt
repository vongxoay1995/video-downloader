package com.brightfetch.app.storage

import android.content.Context

data class PlaybackProgress(val positionMillis: Long, val durationMillis: Long, val updatedAt: Long) {
    val canResume: Boolean get() = positionMillis >= 1_000L && durationMillis - positionMillis > 2_000L
}

/** Device-local watch positions. Media files themselves are never modified. */
object PlaybackStore {
    private fun preferences(context: Context) =
        context.getSharedPreferences("brightfetch_playback", Context.MODE_PRIVATE)

    fun resumeEnabled(context: Context): Boolean = preferences(context).getBoolean("resume_enabled", true)

    fun setResumeEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean("resume_enabled", enabled).apply()
    }

    fun read(context: Context): Map<String, PlaybackProgress> {
        val prefs = preferences(context)
        return prefs.all.keys.filter { it.startsWith("position:") }.associate { key ->
            val uri = key.removePrefix("position:")
            uri to PlaybackProgress(prefs.getLong(key, 0L), prefs.getLong("duration:$uri", 0L),
                prefs.getLong("updated:$uri", 0L))
        }
    }

    fun save(context: Context, uri: String, position: Long, duration: Long) {
        if (duration <= 0L) return
        val safePosition = position.coerceIn(0L, duration)
        preferences(context).edit()
            .putLong("position:$uri", if (duration - safePosition <= 2_000L) 0L else safePosition)
            .putLong("duration:$uri", duration)
            .putLong("updated:$uri", System.currentTimeMillis()).apply()
    }

    fun remove(context: Context, uri: String) {
        preferences(context).edit().remove("position:$uri").remove("duration:$uri")
            .remove("updated:$uri").apply()
    }
}
