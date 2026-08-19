package com.brightfetch.app.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import com.brightfetch.app.MainViewModel
import com.brightfetch.app.model.DownloadSnapshot
import com.brightfetch.app.model.DownloadedVideo
import com.brightfetch.app.ui.theme.Mint
import com.brightfetch.app.ui.theme.SunnyYellow
import java.text.DateFormat
import java.util.Date
import java.util.Locale

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
fun VideosScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val videos by viewModel.videos.collectAsState()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader("Videos", "Internal storage / Movies / BrightFetch")
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
                        onPlay = { openVideo(context, video) },
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(62.dp).background(Mint, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatBytes(video.size)}  •  ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(video.modifiedAt))}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Text(
                    video.displayPath,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onPlay) { Icon(Icons.Default.FolderOpen, contentDescription = "Open") }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        }
    }
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

private fun openVideo(context: Context, video: DownloadedVideo) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(video.uri, video.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }.onFailure {
        Toast.makeText(context, "No app can open this video", Toast.LENGTH_SHORT).show()
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

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}
