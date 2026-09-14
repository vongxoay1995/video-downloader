package com.brightfetch.app.ui

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.brightfetch.app.model.DownloadedVideo
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * Full-screen player for a video stored in MediaStore.
 *
 * [VideoView] is used deliberately so this screen works with the app's current dependency set.
 * It accepts `content://` URIs, owns the underlying MediaPlayer, and releases it from
 * [VideoView.stopPlayback] when the composable leaves the composition.
 */
@Composable
fun VideoPlayerScreen(
    video: DownloadedVideo,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var retryGeneration by remember(video.uri) { mutableIntStateOf(0) }
    var isPrepared by remember(video.uri) { mutableStateOf(false) }
    var isPlaying by remember(video.uri) { mutableStateOf(false) }
    var isBuffering by remember(video.uri) { mutableStateOf(true) }
    var errorMessage by remember(video.uri) { mutableStateOf<String?>(null) }
    var durationMillis by remember(video.uri) {
        mutableLongStateOf(video.durationMillis.coerceAtLeast(0L))
    }
    var positionMillis by remember(video.uri) { mutableLongStateOf(0L) }
    var isSeeking by remember(video.uri) { mutableStateOf(false) }
    var seekFraction by remember(video.uri) { mutableFloatStateOf(0f) }
    var resumeWhenForegrounded by remember(video.uri) { mutableStateOf(false) }
    var resumePositionMillis by remember(video.uri) { mutableLongStateOf(0L) }
    var preparedPlayer by remember(video.uri) { mutableStateOf<MediaPlayer?>(null) }

    val videoView = remember(video.uri) {
        VideoView(context).apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            setAudioFocusRequest(AudioManager.AUDIOFOCUS_GAIN)
            setBackgroundColor(android.graphics.Color.BLACK)
        }
    }

    BackHandler(onBack = onBack)

    DisposableEffect(videoView, video.uri) {
        videoView.setOnPreparedListener { player ->
            preparedPlayer = player
            player.setVolume(1f, 1f)
            durationMillis = player.duration.toLong().coerceAtLeast(0L)
            isPrepared = true
            isBuffering = false
            errorMessage = null
            isPlaying = true
        }
        videoView.setOnInfoListener { player, what, _ ->
            when (what) {
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> isBuffering = true
                MediaPlayer.MEDIA_INFO_BUFFERING_END,
                MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START,
                -> {
                    player.setVolume(1f, 1f)
                    isBuffering = false
                }
            }
            false
        }
        videoView.setOnCompletionListener {
            isPlaying = false
            isBuffering = false
            positionMillis = durationMillis
        }
        videoView.setOnErrorListener { _, what, extra ->
            preparedPlayer = null
            isPrepared = false
            isPlaying = false
            isBuffering = false
            errorMessage = mediaErrorMessage(what, extra)
            true
        }

        onDispose {
            resumeWhenForegrounded = false
            preparedPlayer = null
            videoView.setOnPreparedListener(null)
            videoView.setOnInfoListener(null)
            videoView.setOnCompletionListener(null)
            videoView.setOnErrorListener(null)
            runCatching { videoView.stopPlayback() }
        }
    }

    LaunchedEffect(video.uri, retryGeneration) {
        errorMessage = null
        isPrepared = false
        isPlaying = false
        isBuffering = true
        positionMillis = 0L
        runCatching {
            videoView.stopPlayback()
            videoView.setVideoURI(video.uri)
            videoView.requestFocus()
            videoView.start()
        }.onFailure { error ->
            isBuffering = false
            errorMessage = error.message ?: "Unable to open this video."
        }
    }

    LaunchedEffect(videoView, isPrepared) {
        while (isPrepared) {
            runCatching {
                if (!isSeeking) {
                    positionMillis = videoView.currentPosition.toLong().coerceAtLeast(0L)
                }
                isPlaying = videoView.isPlaying
            }
            delay(350L)
        }
    }

    DisposableEffect(lifecycleOwner, videoView, isPrepared) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                -> if (isPrepared) {
                    // ON_STOP normally follows ON_PAUSE. Preserve the earlier `true` instead of
                    // replacing it after ON_PAUSE has already paused the view.
                    resumeWhenForegrounded = resumeWhenForegrounded ||
                        runCatching { videoView.isPlaying }.getOrDefault(false)
                    resumePositionMillis = runCatching {
                        videoView.currentPosition.toLong()
                    }.getOrDefault(positionMillis)
                    runCatching { videoView.pause() }
                    isPlaying = false
                }

                Lifecycle.Event.ON_RESUME -> if (isPrepared && resumeWhenForegrounded) {
                    runCatching {
                        preparedPlayer?.setVolume(1f, 1f)
                        videoView.seekTo(resumePositionMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                        videoView.start()
                    }.onSuccess {
                        isPlaying = true
                        resumeWhenForegrounded = false
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101010))
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            Text(
                text = video.name,
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Audio enabled",
                tint = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { videoView },
                modifier = Modifier.fillMaxSize(),
            )

            if (isBuffering && errorMessage == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(10.dp))
                    Text("Loading video…", color = Color.White.copy(alpha = 0.86f))
                }
            }

            val currentError = errorMessage
            if (currentError != null) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = currentError,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = { retryGeneration += 1 }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text("Retry", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        if (errorMessage == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF101010))
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Slider(
                    value = if (isSeeking) {
                        seekFraction
                    } else {
                        playbackFraction(positionMillis, durationMillis)
                    },
                    onValueChange = { fraction ->
                        isSeeking = true
                        seekFraction = fraction.coerceIn(0f, 1f)
                    },
                    onValueChangeFinished = {
                        val target = (durationMillis * seekFraction).toLong()
                        positionMillis = target
                        runCatching {
                            videoView.seekTo(target.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                        }
                        isSeeking = false
                    },
                    enabled = isPrepared && durationMillis > 0L,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            if (videoView.isPlaying) {
                                videoView.pause()
                                isPlaying = false
                            } else {
                                preparedPlayer?.setVolume(1f, 1f)
                                if (durationMillis > 0L && positionMillis >= durationMillis - 500L) {
                                    videoView.seekTo(0)
                                    positionMillis = 0L
                                }
                                videoView.start()
                                isPlaying = true
                            }
                        },
                        enabled = isPrepared,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                        )
                    }
                    Text(
                        text = "${formatPlaybackTime(positionMillis)} / ${formatPlaybackTime(durationMillis)}",
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

private fun playbackFraction(positionMillis: Long, durationMillis: Long): Float {
    if (durationMillis <= 0L) return 0f
    return (positionMillis.toDouble() / durationMillis.toDouble()).toFloat().coerceIn(0f, 1f)
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = max(0L, milliseconds) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun mediaErrorMessage(what: Int, extra: Int): String {
    val reason = when (extra) {
        MediaPlayer.MEDIA_ERROR_IO -> "The video could not be read from storage."
        MediaPlayer.MEDIA_ERROR_MALFORMED -> "The video file is malformed."
        MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "This device does not support the video's codec."
        MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "The player timed out while opening the video."
        else -> if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
            "The system media service stopped unexpectedly."
        } else {
            "The video could not be played."
        }
    }
    return "$reason Tap Retry to try again."
}
