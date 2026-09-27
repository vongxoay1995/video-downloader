package com.brightfetch.app.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import com.brightfetch.app.MainViewModel
import com.brightfetch.app.model.DownloadSnapshot
import com.brightfetch.app.model.DownloadedVideo
import com.brightfetch.app.ui.theme.Hairline
import com.brightfetch.app.ui.theme.Ink
import com.brightfetch.app.ui.theme.Muted
import com.brightfetch.app.ui.theme.SoftSuccess
import com.brightfetch.app.ui.theme.SoftViolet
import com.brightfetch.app.ui.theme.Success
import com.brightfetch.app.ui.theme.Violet

@Composable
fun ModernDownloadingScreen(
    viewModel: MainViewModel,
    onOpenBrowser: () -> Unit,
    onOpenLibrary: () -> Unit,
    onPlayVideo: (DownloadedVideo) -> Unit,
) {
    val downloads by viewModel.downloads.collectAsState()
    val videos by viewModel.videos.collectAsState()
    var selectedPage by rememberSaveable { mutableIntStateOf(0) }
    val visible = downloads.filter { if (selectedPage == 0) it.state != WorkInfo.State.SUCCEEDED else it.state == WorkInfo.State.SUCCEEDED }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Downloads", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenBrowser) { Icon(Icons.Default.Add, contentDescription = "Find another video", tint = Ink) }
        }
        Text(if (selectedPage == 0) "Your next watch is on its way." else "Saved and ready for wherever you go.",
            color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 24.dp, top = 3.dp))
        Spacer(Modifier.height(18.dp))
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModernChip("Active", selectedPage == 0) { selectedPage = 0 }
            ModernChip("Completed", selectedPage == 1) { selectedPage = 1 }
        }
        Spacer(Modifier.height(20.dp))
        if (visible.isEmpty()) {
            ModernEmpty(Icons.Default.Download,
                if (selectedPage == 0) "No active downloads" else "Nothing completed yet",
                if (selectedPage == 0) "Find a video in the browser to start saving." else "Finished downloads will show up here and in Library.",
                if (selectedPage == 0) "Find a video" else "Open Library",
                if (selectedPage == 0) onOpenBrowser else onOpenLibrary)
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(if (selectedPage == 0) "IN PROGRESS" else "SAVED TO DEVICE", color = Muted,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                items(visible, key = { it.id }) { snapshot ->
                    val video = videos.firstOrNull { it.uri.toString() == snapshot.outputUri ||
                        it.displayPath == snapshot.outputPath || it.name == snapshot.fileName }
                    DownloadTile(snapshot, video, onCancel = { viewModel.cancel(snapshot.id) },
                        onOpen = { if (video != null) onPlayVideo(video) else onOpenLibrary() },
                        onRecover = onOpenBrowser)
                }
            }
        }
    }
}

@Composable
private fun DownloadTile(
    snapshot: DownloadSnapshot,
    video: DownloadedVideo?,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onRecover: () -> Unit,
) {
    val active = snapshot.state == WorkInfo.State.RUNNING || snapshot.state == WorkInfo.State.ENQUEUED || snapshot.state == WorkInfo.State.BLOCKED
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp))
        .border(1.dp, Hairline, RoundedCornerShape(20.dp)).padding(13.dp)) {
        if (video != null) { VideoArtwork(video, Modifier.fillMaxWidth().height(132.dp).clickable(onClick = onOpen)); Spacer(Modifier.height(12.dp)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (video == null) {
                Box(Modifier.size(56.dp).background(SoftViolet, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Violet)
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(snapshot.fileName, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(downloadStateLabel(snapshot), color = if (snapshot.state == WorkInfo.State.FAILED) Color(0xFFBC4247) else Muted,
                    fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (active) IconButton(onClick = onCancel) { Icon(Icons.Default.Cancel, contentDescription = "Cancel download", tint = Violet) }
            else if (snapshot.state == WorkInfo.State.SUCCEEDED) IconButton(onClick = onOpen) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Open saved video", tint = Ink)
            }
        }
        if (active) {
            Spacer(Modifier.height(13.dp))
            val progress = snapshot.progress.coerceIn(0, 100)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (snapshot.totalBytes > 0L) "${VideoLibraryFormatting.bytes(snapshot.downloadedBytes)} / ${VideoLibraryFormatting.bytes(snapshot.totalBytes)}"
                    else "Downloading in background", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("$progress%", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = Violet, trackColor = SoftViolet)
        }
        if (snapshot.state == WorkInfo.State.SUCCEEDED) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().background(SoftSuccess, RoundedCornerShape(12.dp)).clickable(onClick = onOpen).padding(11.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Success, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text(if (video != null) "Play now" else "Find in Library", color = Success, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        } else if (snapshot.state == WorkInfo.State.FAILED) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().background(SoftViolet, RoundedCornerShape(12.dp))
                .clickable(onClick = onRecover).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Download, contentDescription = null, tint = Violet, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Open browser to retry", color = Violet, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    }
}

private fun downloadStateLabel(snapshot: DownloadSnapshot): String = when (snapshot.state) {
    WorkInfo.State.ENQUEUED -> "Waiting for network"
    WorkInfo.State.RUNNING -> if (snapshot.totalBytes > 0) "Downloading · ${VideoLibraryFormatting.bytes(snapshot.totalBytes)}" else "Downloading"
    WorkInfo.State.SUCCEEDED -> if (snapshot.totalBytes > 0) "Downloaded · ${VideoLibraryFormatting.bytes(snapshot.totalBytes)}" else "Downloaded"
    WorkInfo.State.FAILED -> snapshot.error ?: "Download failed"
    WorkInfo.State.BLOCKED -> "Waiting to start"
    WorkInfo.State.CANCELLED -> "Cancelled"
}

@Composable
fun ModernVideosScreen(viewModel: MainViewModel, onPlayVideo: (DownloadedVideo) -> Unit, onSaveFirstVideo: () -> Unit) {
    val context = LocalContext.current
    val videos by viewModel.videos.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val searchFocus = remember { FocusRequester() }
    BackHandler(enabled = searching) { searching = false; query = "" }
    LaunchedEffect(searching) { if (searching) searchFocus.requestFocus() }
    var deleteTarget by remember { mutableStateOf<DownloadedVideo?>(null) }
    val filtered = videos.filter { video ->
        (query.isBlank() || video.name.contains(query.trim(), ignoreCase = true)) && when (filter) {
            1 -> minOf(video.width, video.height) >= 720
            2 -> video.durationMillis in 1..60_000L
            else -> true
        }
    }
    val continueVideo = videos.filter { playbackProgress[it.uri.toString()]?.canResume == true }
        .maxByOrNull { playbackProgress[it.uri.toString()]?.updatedAt ?: 0L }
    Column(Modifier.fillMaxSize()) {
        if (searching) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { searching = false; query = "" }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search", tint = Ink)
                }
                Row(Modifier.weight(1f).height(50.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(9.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isBlank()) Text("Search your library", color = Muted, fontSize = 14.sp)
                        BasicTextField(query, onValueChange = { query = it }, singleLine = true, textStyle = TextStyle(color = Ink, fontSize = 14.sp),
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus)
                                .semantics { contentDescription = "Search your library" })
                    }
                    if (query.isNotBlank()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Muted)
                    }
                }
            }
            Text("Search your offline collection", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 24.dp, bottom = 12.dp))
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Library", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, contentDescription = "Search Library", tint = Ink) }
            }
            Text("All your favorites. No connection needed.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 24.dp, top = 3.dp))
            Spacer(Modifier.height(15.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(13.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Violet, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(9.dp))
                Text("${VideoLibraryFormatting.bytes(videos.sumOf { it.size.coerceAtLeast(0L) })} saved", color = Ink,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("On this device", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "HD videos", "Short clips").forEachIndexed { index, label -> ModernChip(label, filter == index) { filter = index } }
        }
        Spacer(Modifier.height(20.dp))
        when {
            filtered.isEmpty() && query.isNotBlank() -> ModernEmpty(Icons.Default.Search, "No matches for “$query”",
                "Try a shorter title or another keyword.", "Clear search") { query = "" }
            filtered.isEmpty() -> ModernEmpty(Icons.Default.VideoLibrary,
                if (videos.isEmpty()) "Your offline era starts here" else "No ${if (filter == 2) "short clips" else "HD videos"} yet",
                if (videos.isEmpty()) "Save a video you love, then find it here. Ready whenever you are." else "Your saved media stays available offline.",
                "Save your first video", onSaveFirstVideo)
            else -> {
                LazyVerticalGrid(columns = if (searching) GridCells.Fixed(1) else GridCells.Adaptive(145.dp),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (!searching && filter == 0 && continueVideo != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val progress = playbackProgress.getValue(continueVideo.uri.toString())
                            Column {
                                Text("Continue watching", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                                Box(Modifier.fillMaxWidth().height(195.dp).clip(RoundedCornerShape(18.dp))
                                    .clickable { onPlayVideo(continueVideo) }) {
                                    VideoArtwork(continueVideo, Modifier.fillMaxSize(), showDuration = false)
                                    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .9f))))
                                        .padding(14.dp)) {
                                        Text(continueVideo.name.substringBeforeLast('.'), color = Color.White,
                                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${VideoLibraryFormatting.duration(progress.positionMillis)} / ${VideoLibraryFormatting.duration(progress.durationMillis)}",
                                            color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
                                        Spacer(Modifier.height(8.dp))
                                        LinearProgressIndicator(progress = { (progress.positionMillis.toFloat() / progress.durationMillis).coerceIn(0f, 1f) },
                                            color = Color(0xFFB5A7FF), trackColor = Color.White.copy(alpha = .25f),
                                            modifier = Modifier.fillMaxWidth().height(4.dp))
                                    }
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Continue watching",
                                        tint = Ink, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                                            .background(Color.White, CircleShape).padding(8.dp).size(24.dp))
                                }
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(if (query.isNotBlank()) "${filtered.size} result${if (filtered.size == 1) "" else "s"} for “$query”" else "Recently saved",
                            color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    gridItems(filtered, key = { it.uri.toString() }) { video ->
                        LibraryVideoTile(video, expandedArtwork = searching, onPlay = { onPlayVideo(video) }, onShare = { shareVideo(context, video) },
                            onDelete = { deleteTarget = video })
                    }
                }
            }
        }
    }
    deleteTarget?.let { video ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Delete this video?") },
            text = { Text("${video.name} will be removed from this device.") },
            confirmButton = { TextButton(onClick = {
                viewModel.deleteVideo(video) { deleted ->
                    Toast.makeText(context, if (deleted) "Video deleted" else "Could not delete this video", Toast.LENGTH_SHORT).show()
                }
                deleteTarget = null
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } })
    }
}

@Composable
private fun LibraryVideoTile(video: DownloadedVideo, expandedArtwork: Boolean, onPlay: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Box(Modifier.fillMaxWidth().height(if (expandedArtwork) 200.dp else 115.dp).clickable(onClick = onPlay)) {
            VideoArtwork(video, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).size(38.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play ${video.name}", tint = Ink)
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(video.name.substringBeforeLast('.'), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable(onClick = onPlay))
            Box {
                IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options for ${video.name}", tint = Muted)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = { expanded = false; onShare() })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { expanded = false; onDelete() })
                }
            }
        }
        Text("${VideoLibraryFormatting.quality(video.width, video.height)} · ${VideoLibraryFormatting.bytes(video.size)}",
            color = Muted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun ModernChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) Ink else Color.White)
        .border(1.dp, if (selected) Ink else Hairline, RoundedCornerShape(20.dp))
        .selectable(selected, role = Role.Tab, onClick = onClick).heightIn(min = 44.dp)
        .padding(horizontal = 17.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) Color.White else Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ModernEmpty(icon: ImageVector, title: String, subtitle: String, action: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(118.dp).background(SoftViolet, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Violet, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(title, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = Muted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            BrandAction(action, if (icon == Icons.Default.Search) Icons.Default.Close else Icons.Default.Add, onAction)
        }
    }
}

private fun shareVideo(context: Context, video: DownloadedVideo) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).setType(video.mimeType).putExtra(Intent.EXTRA_STREAM, video.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share video"))
    }.onFailure { Toast.makeText(context, "Unable to share this video", Toast.LENGTH_SHORT).show() }
}
