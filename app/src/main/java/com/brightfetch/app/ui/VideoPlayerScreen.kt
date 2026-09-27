package com.brightfetch.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.brightfetch.app.model.DownloadedVideo
import com.brightfetch.app.ui.theme.Violet
import kotlinx.coroutines.delay
import kotlin.math.max

/** Portrait listening controls and immersive landscape share a single player/video surface. */
@Composable
fun VideoPlayerScreen(
    video: DownloadedVideo,
    onBack: () -> Unit,
    initialPositionMillis: Long = 0L,
    onSaveProgress: (Long, Long) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostView = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val originalOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    var retryGeneration by remember(video.uri) { mutableIntStateOf(0) }
    var isPrepared by remember(video.uri) { mutableStateOf(false) }
    var isPlaying by remember(video.uri) { mutableStateOf(false) }
    var isBuffering by remember(video.uri) { mutableStateOf(true) }
    var hasRenderedFirstFrame by remember(video.uri) { mutableStateOf(false) }
    var errorMessage by remember(video.uri) { mutableStateOf<String?>(null) }
    var durationMillis by remember(video.uri) {
        mutableLongStateOf(video.durationMillis.coerceAtLeast(0L))
    }
    var videoAspectRatio by remember(video.uri) {
        mutableFloatStateOf(
            if (video.width > 0 && video.height > 0) {
                video.width.toFloat() / video.height.toFloat()
            } else {
                16f / 9f
            },
        )
    }
    var positionMillis by remember(video.uri) { mutableLongStateOf(initialPositionMillis) }
    var isSeeking by remember(video.uri) { mutableStateOf(false) }
    var seekFraction by remember(video.uri) { mutableFloatStateOf(0f) }
    var resumeWhenForegrounded by remember(video.uri) { mutableStateOf(false) }
    var resumePositionMillis by remember(video.uri) { mutableLongStateOf(0L) }

    var controlsVisible by remember(video.uri) { mutableStateOf(true) }
    var controlInteraction by remember(video.uri) { mutableIntStateOf(0) }
    var controlsLocked by remember(video.uri) { mutableStateOf(false) }
    var isMuted by remember(video.uri) { mutableStateOf(false) }
    var playbackSpeed by remember(video.uri) { mutableFloatStateOf(1f) }

    val textureView = remember(video.uri) {
        TextureView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            keepScreenOn = true
        }
    }
    val player = remember(video.uri) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
        }
    }

    fun markControlInteraction() {
        controlInteraction += 1
    }

    fun seekTo(targetMillis: Long) {
        val upperBound = if (durationMillis > 0L) durationMillis else Long.MAX_VALUE
        val target = targetMillis.coerceIn(0L, upperBound)
        positionMillis = target
        runCatching { player.seekTo(target) }
        markControlInteraction()
    }

    fun applyAudioState(muted: Boolean) {
        player.volume = if (muted) 0f else 1f
    }

    fun applySpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    BackHandler(onBack = onBack)

    DisposableEffect(activity, hostView) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, hostView) }
        if (window != null) WindowCompat.setDecorFitsSystemWindows(window, false)
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            controller?.isAppearanceLightStatusBars = true
            controller?.isAppearanceLightNavigationBars = true
            if (window != null) WindowCompat.setDecorFitsSystemWindows(window, false)
            activity?.requestedOrientation = originalOrientation
        }
    }

    LaunchedEffect(activity, hostView, controlsVisible, errorMessage, isLandscape) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, hostView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        if (!isLandscape || controlsVisible || errorMessage != null) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, isSeeking, controlsLocked, controlInteraction, isLandscape) {
        if (isLandscape && controlsVisible && isPlaying && !isSeeking && errorMessage == null) {
            delay(if (controlsLocked) 1_800L else 3_200L)
            controlsVisible = false
        }
    }

    DisposableEffect(player, textureView, video.uri) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> isBuffering = true
                    Player.STATE_READY -> {
                        val playerDuration = player.duration
                        if (playerDuration != C.TIME_UNSET && playerDuration > 0L) {
                            durationMillis = playerDuration
                        }
                        isPrepared = true
                        isBuffering = !hasRenderedFirstFrame
                        errorMessage = null
                    }
                    Player.STATE_ENDED -> {
                        isPlaying = false
                        isBuffering = false
                        positionMillis = durationMillis
                        controlsVisible = true
                        markControlInteraction()
                    }
                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing && hasRenderedFirstFrame) isBuffering = false
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                isBuffering = false
                Log.i(PLAYER_LOG_TAG, "Rendered first video frame: ${video.displayPath}")
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspectRatio =
                        videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isPrepared = false
                isPlaying = false
                isBuffering = false
                hasRenderedFirstFrame = false
                Log.e(
                    PLAYER_LOG_TAG,
                    "Video playback failed: uri=${video.uri} path=${video.displayPath}",
                    error,
                )
                errorMessage = "The file may be incomplete or use a format this player doesn’t support."
            }
        }
        player.addListener(listener)
        player.setVideoTextureView(textureView)
        onDispose {
            onSaveProgress(runCatching { player.currentPosition }.getOrDefault(positionMillis), durationMillis)
            resumeWhenForegrounded = false
            textureView.keepScreenOn = false
            player.clearVideoTextureView(textureView)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, video.uri, retryGeneration) {
        errorMessage = null
        isPrepared = false
        isPlaying = false
        isBuffering = true
        hasRenderedFirstFrame = false
        val startPosition = if (retryGeneration == 0) initialPositionMillis else positionMillis
        runCatching {
            player.stop()
            player.clearMediaItems()
            applyAudioState(isMuted)
            applySpeed(playbackSpeed)
            player.setMediaItem(MediaItem.fromUri(video.uri), startPosition)
            player.prepare()
            player.playWhenReady = true
        }.onFailure { error ->
            isBuffering = false
            errorMessage = error.message ?: "Unable to open this video."
        }
    }

    // STATE_READY can be reached from the audio track alone. Do not silently accept an
    // audio-only black screen as successful video playback.
    LaunchedEffect(isPrepared, hasRenderedFirstFrame, errorMessage, retryGeneration) {
        if (isPrepared && !hasRenderedFirstFrame && errorMessage == null) {
            delay(10_000L)
            if (isPrepared && !hasRenderedFirstFrame && errorMessage == null) {
                runCatching { player.pause() }
                isPlaying = false
                isBuffering = false
                Log.e(
                    PLAYER_LOG_TAG,
                    "No video frame rendered: uri=${video.uri} path=${video.displayPath}",
                )
                errorMessage =
                    "This file’s video format couldn’t be displayed. Try opening it again."
            }
        }
    }

    // A corrupt or unreadable local item must not leave the UI spinning forever.
    LaunchedEffect(isBuffering, errorMessage, retryGeneration) {
        if (!isPrepared && isBuffering && errorMessage == null) {
            delay(15_000L)
            if (!isPrepared && isBuffering && !isPlaying && errorMessage == null) {
                runCatching { player.stop() }
                isPrepared = false
                isBuffering = false
                errorMessage = "This video took too long to open. Try again or choose another file."
            }
        }
    }

    LaunchedEffect(player, isPrepared) {
        while (isPrepared) {
            runCatching {
                if (!isSeeking) positionMillis = player.currentPosition.coerceAtLeast(0L)
                val playerDuration = player.duration
                if (playerDuration != C.TIME_UNSET && playerDuration > 0L) {
                    durationMillis = playerDuration
                }
                isPlaying = player.isPlaying
            }
            delay(300L)
        }
    }

    DisposableEffect(lifecycleOwner, player, isPrepared) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                -> if (isPrepared) {
                    resumeWhenForegrounded = resumeWhenForegrounded ||
                        runCatching { player.isPlaying }.getOrDefault(false)
                    resumePositionMillis = runCatching { player.currentPosition }
                        .getOrDefault(positionMillis)
                    onSaveProgress(resumePositionMillis, durationMillis)
                    runCatching { player.pause() }
                    isPlaying = false
                }
                Lifecycle.Event.ON_RESUME -> if (isPrepared && resumeWhenForegrounded) {
                    runCatching {
                        applyAudioState(isMuted)
                        applySpeed(playbackSpeed)
                        player.seekTo(resumePositionMillis)
                        player.play()
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111722)),
        contentAlignment = Alignment.Center,
    ) {
        val portraitMediaHeight = (maxHeight * .34f).coerceIn(150.dp, 320.dp)
        val mediaTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 80.dp
        Box(
            modifier = (if (isLandscape) Modifier.fillMaxSize() else Modifier.align(Alignment.TopCenter)
                .offset(y = mediaTop).padding(horizontal = 24.dp).fillMaxWidth().height(portraitMediaHeight)
                .clip(RoundedCornerShape(22.dp)))
                .background(Color.Black)
                .pointerInput(controlsLocked, errorMessage, isLandscape) {
                    detectTapGestures(onTap = {
                        if (errorMessage == null && isLandscape) {
                            controlsVisible = !controlsVisible
                            markControlInteraction()
                        }
                    }, onLongPress = {
                        if (controlsLocked) { controlsLocked = false; controlsVisible = true }
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(factory = { textureView }, modifier = Modifier.aspectRatio(videoAspectRatio).fillMaxSize())
            if (!isLandscape && !controlsLocked && errorMessage == null) {
                Text("OFFLINE", color = Color(0xFF192136), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                        .background(Color.White, RoundedCornerShape(14.dp)).padding(horizontal = 11.dp, vertical = 5.dp))
            }
            if (isBuffering && errorMessage == null) CircularProgressIndicator(color = Violet)
        }

        val currentError = errorMessage
        if (currentError != null) {
            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth()
                    .background(Color(0xFF111722)).statusBarsPadding().navigationBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VideoArtwork(video, Modifier.fillMaxWidth().height(190.dp))
                Spacer(Modifier.height(8.dp))
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFD994),
                    modifier = Modifier.size(42.dp))
                Text("Can’t play this file", color = Color.White, fontSize = 23.sp,
                    fontWeight = FontWeight.Bold)
                Text(currentError, color = Color(0xFFAAB2C3), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { retryGeneration += 1 }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Try again", modifier = Modifier.padding(start = 8.dp))
                }
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202A39))) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Text("Open another file", modifier = Modifier.padding(start = 8.dp))
                }
                Text("Your saved file won’t be removed.", color = Color(0xFFAAB2C3), fontSize = 12.sp)
            }
        }

        if ((controlsVisible || !isLandscape) && errorMessage == null) {
            if (controlsLocked) {
                RoundIconButton(
                    icon = Icons.Default.LockOpen,
                    contentDescription = "Unlock controls",
                    onClick = {
                        controlsLocked = false
                        markControlInteraction()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(24.dp),
                )
            } else {
                PlayerControls(
                    portrait = !isLandscape,
                    portraitMediaHeight = portraitMediaHeight,
                    subtitle = "${VideoLibraryFormatting.quality(video.width, video.height)} · Saved on this device",
                    videoName = video.name,
                    isMuted = isMuted,
                    speed = playbackSpeed,
                    isPlaying = isPlaying,
                    isPrepared = isPrepared,
                    isSeeking = isSeeking,
                    seekFraction = seekFraction,
                    positionMillis = positionMillis,
                    durationMillis = durationMillis,
                    onBack = onBack,
                    onMute = {
                        isMuted = !isMuted
                        applyAudioState(isMuted)
                        markControlInteraction()
                    },
                    onRotate = {
                        activity?.requestedOrientation =
                            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                            ) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                        markControlInteraction()
                    },
                    onSpeed = {
                        playbackSpeed = nextPlaybackSpeed(playbackSpeed)
                        applySpeed(playbackSpeed)
                        markControlInteraction()
                    },
                    onSeekChanged = { fraction ->
                        isSeeking = true
                        seekFraction = fraction.coerceIn(0f, 1f)
                        markControlInteraction()
                    },
                    onSeekFinished = {
                        seekTo((durationMillis * seekFraction).toLong())
                        isSeeking = false
                    },
                    onRewind = { seekTo(positionMillis - 10_000L) },
                    onForward = { seekTo(positionMillis + 10_000L) },
                    onPlayPause = {
                        if (player.isPlaying) {
                            player.pause()
                            isPlaying = false
                        } else {
                            applyAudioState(isMuted)
                            applySpeed(playbackSpeed)
                            if (durationMillis > 0L && positionMillis >= durationMillis - 500L) {
                                seekTo(0L)
                            }
                            player.play()
                            isPlaying = true
                        }
                        markControlInteraction()
                    },
                    onLock = {
                        controlsLocked = true
                        controlsVisible = false
                        markControlInteraction()
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayerControls(
    portrait: Boolean,
    portraitMediaHeight: Dp,
    subtitle: String,
    videoName: String,
    isMuted: Boolean,
    speed: Float,
    isPlaying: Boolean,
    isPrepared: Boolean,
    isSeeking: Boolean,
    seekFraction: Float,
    positionMillis: Long,
    durationMillis: Long,
    onBack: () -> Unit,
    onMute: () -> Unit,
    onRotate: () -> Unit,
    onSpeed: () -> Unit,
    onSeekChanged: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onPlayPause: () -> Unit,
    onLock: () -> Unit,
) {
    if (portrait) {
        PortraitPlayerControls(
            videoName, subtitle, portraitMediaHeight, isMuted, speed, isPlaying, isPrepared,
            if (isSeeking) seekFraction else playbackFraction(positionMillis, durationMillis),
            positionMillis, durationMillis, onBack, onMute, onRotate, onSpeed, onSeekChanged,
            onSeekFinished, onRewind, onForward, onPlayPause, onLock,
        )
        return
    }
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .65f), Color.Transparent)))
                .statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(videoName.substringBeforeLast('.'), color = Color.White, fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onLock) {
                Icon(Icons.Default.Lock, contentDescription = "Lock controls", tint = Color.White)
            }
        }
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(30.dp),
            verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Default.Replay10, "Rewind 10 seconds", onRewind, enabled = isPrepared)
            RoundIconButton(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (isPlaying) "Pause" else "Play", onPlayPause, enabled = isPrepared, emphasized = true)
            RoundIconButton(Icons.Default.Forward10, "Forward 10 seconds", onForward, enabled = isPrepared)
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .8f))))
            .navigationBarsPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatPlaybackTime(positionMillis), color = Color.White, fontSize = 11.sp)
                Text(formatPlaybackTime(durationMillis), color = Color.White, fontSize = 11.sp)
            }
            PlayerSeekSlider(if (isSeeking) seekFraction else playbackFraction(positionMillis, durationMillis),
                isPrepared && durationMillis > 0L, onSeekChanged, onSeekFinished)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMute) {
                    Icon(if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute", tint = Color.White)
                }
                androidx.compose.material3.TextButton(onClick = onSpeed) {
                    Text("${formatSpeed(speed)}×", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRotate) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = "Exit full screen", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PortraitPlayerControls(
    title: String,
    subtitle: String,
    mediaHeight: Dp,
    muted: Boolean,
    speed: Float,
    playing: Boolean,
    prepared: Boolean,
    fraction: Float,
    position: Long,
    duration: Long,
    onBack: () -> Unit,
    onMute: () -> Unit,
    onRotate: () -> Unit,
    onSpeed: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onPlayPause: () -> Unit,
    onLock: () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.background(Color(0xFF202A39), RoundedCornerShape(15.dp))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Library", tint = Color.White)
            }
            Text("NOW PLAYING", color = Color(0xFFABB4C4), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.height(16.dp + mediaHeight + 20.dp))
        Text(title.substringBeforeLast('.'), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold,
            lineHeight = 29.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = Color(0xFFABB4C4), fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        PlayerSeekSlider(fraction, prepared && duration > 0L, onSeek, onSeekFinished)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatPlaybackTime(position), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(formatPlaybackTime(duration), color = Color(0xFFABB4C4), fontSize = 11.sp)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally)) {
            RoundIconButton(Icons.Default.Replay10, "Rewind 10 seconds", onRewind, enabled = prepared)
            RoundIconButton(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (playing) "Pause" else "Play", onPlayPause, enabled = prepared, emphasized = true)
            RoundIconButton(Icons.Default.Forward10, "Forward 10 seconds", onForward, enabled = prepared)
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth().background(Color(0xFF202A39), RoundedCornerShape(20.dp)).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            PlayerTool("Speed", onSpeed) { Text("${formatSpeed(speed)}×", color = Color.White, fontWeight = FontWeight.Bold) }
            PlayerTool(if (muted) "Unmute" else "Mute", onMute) {
                Icon(if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            PlayerTool("Lock", onLock) { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            PlayerTool("Full screen", onRotate) {
                Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Saved on this device", color = Color(0xFFABB4C4), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 18.dp))
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun PlayerSeekSlider(value: Float, enabled: Boolean, onChange: (Float) -> Unit, onFinished: () -> Unit) {
    Slider(value = value, onValueChange = onChange, onValueChangeFinished = onFinished, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(32.dp),
        thumb = { Box(Modifier.size(12.dp).background(Color.White, CircleShape)) },
        track = {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color(0xFF343E50))) {
                Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(4.dp).background(Color(0xFFB5A7FF)))
            }
        })
}

@Composable
private fun PlayerTool(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.width(68.dp).height(52.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(Modifier.height(27.dp), contentAlignment = Alignment.Center) { content() }
        Text(label, color = Color(0xFFABB4C4), fontSize = 10.sp)
    }
}

@Composable
private fun LabeledControl(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.68f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { content() }
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    small: Boolean = false,
) {
    val buttonSize = when {
        small -> 44.dp
        emphasized -> 66.dp
        else -> 52.dp
    }
    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(if (emphasized) Violet else Color.Black.copy(alpha = 0.54f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (emphasized) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Violet, CircleShape),
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.42f),
            modifier = Modifier.size(if (emphasized) 42.dp else 30.dp),
        )
    }
}

private fun playbackFraction(positionMillis: Long, durationMillis: Long): Float {
    if (durationMillis <= 0L) return 0f
    return (positionMillis.toDouble() / durationMillis.toDouble()).toFloat().coerceIn(0f, 1f)
}

private fun nextPlaybackSpeed(current: Float): Float = when {
    current < 1.25f -> 1.25f
    current < 1.5f -> 1.5f
    current < 2f -> 2f
    else -> 1f
}

private fun formatSpeed(speed: Float): String = if (speed == 1f || speed == 2f) {
    "%.1f".format(speed)
} else {
    "%.2f".format(speed)
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = max(0L, milliseconds) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val PLAYER_LOG_TAG = "BrightFetchPlayer"
