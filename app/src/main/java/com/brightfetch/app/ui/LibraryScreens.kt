package com.brightfetch.app.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import com.brightfetch.app.MainViewModel
import com.brightfetch.app.model.DownloadSnapshot
import com.brightfetch.app.model.DownloadedVideo
import com.brightfetch.app.storage.VideoThumbnailLoader
import com.brightfetch.app.ui.theme.Mint
import com.brightfetch.app.ui.theme.SunnyYellow

@Composable
fun DownloadingScreen(viewModel: MainViewModel) {
    val downloads by viewModel.downloads.collectAsState()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader("Downloading", "Background downloads and recent activity")
        if (downloads.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Download,
                title = "No downloads yet",
                subtitle = "Open Home, visit a page with a public video, then tap the detected-video button.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(downloads, key = { it.id }) { snapshot ->
                    DownloadCard(snapshot, onCancel = { viewModel.cancel(snapshot.id) })
                }
            }
        }
    }
}

@Composable
fun VideosScreen(
    viewModel: MainViewModel,
    onPlayVideo: (DownloadedVideo) -> Unit,
) {
    val context = LocalContext.current
    val videos by viewModel.videos.collectAsState()
    val totalBytes = videos.sumOf { it.size.coerceAtLeast(0L) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(
            "Videos",
            "${videos.size} ${if (videos.size == 1) "video" else "videos"}  •  " +
                "${VideoLibraryFormatting.bytes(totalBytes)}  •  Movies/BrightFetch",
        )
        if (videos.isEmpty()) {
            EmptyState(
                icon = Icons.Default.VideoLibrary,
                title = "Your library is empty",
                subtitle = "Finished videos will appear here and can be played or shared.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(videos, key = { it.uri.toString() }) { video ->
                    VideoCard(
                        video = video,
                        onPlay = { onPlayVideo(video) },
                        onShare = { shareVideo(context, video) },
                        onDelete = {
                            viewModel.deleteVideo(video)
                            Toast.makeText(context, "Video deleted", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun DownloadCard(snapshot: DownloadSnapshot, onCancel: () -> Unit) {
    val isActive = snapshot.state == WorkInfo.State.RUNNING ||
        snapshot.state == WorkInfo.State.ENQUEUED ||
        snapshot.state == WorkInfo.State.BLOCKED
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).background(
                        if (snapshot.state == WorkInfo.State.SUCCEEDED) Mint else Color(0xFFFFF0B8),
                        RoundedCornerShape(13.dp),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(snapshot.fileName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        downloadStatus(snapshot),
                        color = statusColor(snapshot.state),
                        fontSize = 12.sp,
                        maxLines = 2,
                    )
                }
                if (isActive) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Cancel, contentDescription = "Cancel download")
                    }
                }
            }
            if (isActive || snapshot.state == WorkInfo.State.SUCCEEDED) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { snapshot.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                    color = SunnyYellow,
                    trackColor = Color(0xFFF1EFE8),
                )
                Spacer(Modifier.height(7.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("${snapshot.progress.coerceIn(0, 100)}%", fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    if (snapshot.totalBytes > 1024L) {
                        Text(
                            "${formatBytes(snapshot.downloadedBytes)} / ${formatBytes(snapshot.totalBytes)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun downloadStatus(snapshot: DownloadSnapshot): String = when (snapshot.state) {
    WorkInfo.State.ENQUEUED -> "Waiting for network"
    WorkInfo.State.RUNNING -> "Downloading"
    WorkInfo.State.SUCCEEDED -> snapshot.outputPath?.let { "Completed • $it" } ?: "Completed"
    WorkInfo.State.FAILED -> snapshot.error ?: "Download failed"
    WorkInfo.State.BLOCKED -> "Waiting"
    WorkInfo.State.CANCELLED -> "Cancelled"
}

private fun statusColor(state: WorkInfo.State): Color = when (state) {
    WorkInfo.State.SUCCEEDED -> Color(0xFF18794E)
    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> Color(0xFFB3261E)
    else -> Color(0xFF705D00)
}

@Composable
private fun VideoCard(video: DownloadedVideo, onPlay: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                VideoThumbnail(video = video, onPlay = onPlay)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        video.name,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MetadataBadge(
                            text = VideoLibraryFormatting.extension(video.name, video.mimeType),
                            background = Color(0xFF9B2FC9),
                        )
                        MetadataBadge(
                            text = VideoLibraryFormatting.quality(video.width, video.height),
                            background = Color(0xFF456AA3),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${VideoLibraryFormatting.resolution(video.width, video.height)}  •  " +
                            VideoLibraryFormatting.bytes(video.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Downloaded ${VideoLibraryFormatting.downloadedDate(video.downloadedAt)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        video.displayPath,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPlay, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play video")
                }
                IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Share video")
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete video")
                }
            }
        }
    }
}

@Composable
private fun VideoThumbnail(video: DownloadedVideo, onPlay: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val cacheKey = "${video.uri}|${video.size}|${video.modifiedAt}"
    var thumbnail by remember(cacheKey) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(cacheKey) {
        thumbnail = VideoThumbnailLoader.load(context, video)
    }

    Box(
        modifier = Modifier
            .width(122.dp)
            .height(82.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF263238))
            .clickable(onClick = onPlay),
        contentAlignment = Alignment.Center,
    ) {
        val loadedThumbnail = thumbnail
        if (loadedThumbnail != null && !loadedThumbnail.isRecycled) {
            Image(
                bitmap = loadedThumbnail.asImageBitmap(),
                contentDescription = "Thumbnail for ${video.name}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.size(38.dp).background(Color.White.copy(alpha = 0.88f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF263238),
                    modifier = Modifier.size(27.dp),
                )
            }
        }

        val quality = VideoLibraryFormatting.quality(video.width, video.height)
        if (quality != "Unknown quality") {
            Text(
                text = quality,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }

        VideoLibraryFormatting.duration(video.durationMillis)?.let { duration ->
            Text(
                text = duration,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun MetadataBadge(text: String, background: Color) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .background(background, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(76.dp).background(Mint, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun shareVideo(context: Context, video: DownloadedVideo) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND)
            .setType(video.mimeType)
            .putExtra(Intent.EXTRA_STREAM, video.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share video"))
    }.onFailure {
        Toast.makeText(context, "Unable to share this video", Toast.LENGTH_SHORT).show()
    }
}

private fun formatBytes(bytes: Long): String = VideoLibraryFormatting.bytes(bytes)
