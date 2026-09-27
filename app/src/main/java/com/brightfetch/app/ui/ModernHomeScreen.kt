package com.brightfetch.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brightfetch.app.MainViewModel
import com.brightfetch.app.model.DownloadedVideo
import com.brightfetch.app.storage.VideoThumbnailLoader
import com.brightfetch.app.ui.theme.Hairline
import com.brightfetch.app.ui.theme.Ink
import com.brightfetch.app.ui.theme.Muted
import com.brightfetch.app.ui.theme.SoftViolet
import com.brightfetch.app.ui.theme.Violet

@Composable
fun ModernHomeScreen(
    viewModel: MainViewModel,
    onOpenBrowser: (String?, BrowserEntryPanel) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayVideo: (DownloadedVideo) -> Unit,
) {
    val context = LocalContext.current
    val videos by viewModel.videos.collectAsState()
    val bookmarks by viewModel.browserBookmarks.collectAsState()
    val focusManager = LocalFocusManager.current
    var pastedLink by rememberSaveable { mutableStateOf("") }
    var showAddSite by rememberSaveable { mutableStateOf(false) }
    var siteAddress by rememberSaveable { mutableStateOf("") }
    var siteName by rememberSaveable { mutableStateOf("") }
    var siteError by remember { mutableStateOf<String?>(null) }
    fun submitLink() {
        val link = pastedLink.trim().ifBlank {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                ?.coerceToText(context)?.toString()?.trim().orEmpty()
        }
        if (link.isBlank()) {
            Toast.makeText(context, "Copy a video link first", Toast.LENGTH_SHORT).show()
        } else {
            focusManager.clearFocus()
            pastedLink = link
            onOpenBrowser(link, BrowserEntryPanel.NONE)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Spacer(Modifier.width(10.dp))
            Text("BrightFetch", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(46.dp).background(Color.White, RoundedCornerShape(15.dp)),
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Ink)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Save a video.", color = Ink, fontSize = 31.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
        Spacer(Modifier.height(5.dp))
        Text("Paste a link. Keep it for later.", color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp)
                .background(Color.White, RoundedCornerShape(17.dp))
                .border(1.dp, Hairline, RoundedCornerShape(17.dp)).padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Link, contentDescription = null, tint = Muted)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (pastedLink.isBlank()) Text("Paste a video link", color = Muted, fontSize = 15.sp)
                BasicTextField(
                    value = pastedLink,
                    onValueChange = { pastedLink = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 15.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submitLink() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Video link" },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        BrandAction(
            label = if (pastedLink.isBlank()) "Paste link" else "Find video",
            icon = Icons.Default.ContentPaste,
            onClick = ::submitLink,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(50.dp)
                .background(SoftViolet, RoundedCornerShape(17.dp))
                .clickable { onOpenBrowser(null, BrowserEntryPanel.NONE) }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Language, contentDescription = null, tint = Violet)
            Spacer(Modifier.width(12.dp))
            Text("Or explore in browser", color = Violet, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Violet)
        }
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your shortcuts", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onOpenBrowser(null, BrowserEntryPanel.BOOKMARKS) }) { Text("Edit", fontSize = 13.sp) }
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Shortcut(Icons.Outlined.Language, "Explore") { onOpenBrowser(null, BrowserEntryPanel.NONE) } }
            item { Shortcut(Icons.Default.BookmarkBorder, "Favorites") { onOpenBrowser(null, BrowserEntryPanel.BOOKMARKS) } }
            item { Shortcut(Icons.Default.History, "Recent") { onOpenBrowser(null, BrowserEntryPanel.HISTORY) } }
            item { Shortcut(Icons.Default.Add, "Add site") { showAddSite = true } }
            items(bookmarks.take(8), key = { it.url }) { bookmark ->
                Shortcut(Icons.Outlined.Language, bookmark.title) { onOpenBrowser(bookmark.url, BrowserEntryPanel.NONE) }
            }
        }
        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recently saved", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenLibrary) { Text("See all", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(14.dp))
        if (videos.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(18.dp)).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = Violet)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Your saved videos live here", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("Add your first link to get started.", color = Muted, fontSize = 12.sp)
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(videos.take(8), key = { it.uri.toString() }) { video ->
                    Column(Modifier.width(168.dp).clickable { onPlayVideo(video) }) {
                        VideoArtwork(video, Modifier.fillMaxWidth().height(112.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(video.name.substringBeforeLast('.'), color = Ink, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${VideoLibraryFormatting.quality(video.width, video.height)} · ${VideoLibraryFormatting.bytes(video.size)}",
                            color = Muted, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (showAddSite) {
        AlertDialog(onDismissRequest = { showAddSite = false }, title = { Text("Add a shortcut") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(siteName, { siteName = it }, label = { Text("Site name") }, singleLine = true)
                OutlinedTextField(siteAddress, { siteAddress = it; siteError = null }, label = { Text("Website URL") },
                    singleLine = true, isError = siteError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                siteError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        }, confirmButton = {
            TextButton(onClick = {
                val raw = siteAddress.trim()
                val url = if (raw.contains("://")) raw else "https://$raw"
                val uri = Uri.parse(url)
                if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank() || raw.contains(' ')) {
                    siteError = "Enter a valid website address."
                } else {
                    if (!viewModel.isBrowserBookmarked(url)) viewModel.toggleBrowserBookmark(url, siteName.ifBlank { uri.host.orEmpty() })
                    showAddSite = false; siteName = ""; siteAddress = ""
                }
            }) { Text("Add site") }
        }, dismissButton = { TextButton(onClick = { showAddSite = false }) { Text("Cancel") } })
    }
}

@Composable
internal fun BrandMark() {
    Box(Modifier.size(38.dp).background(Violet, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
    }
}

@Composable
internal fun BrandAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(17.dp)) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.width(21.dp))
    }
}

@Composable
private fun Shortcut(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(74.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(62.dp).background(Color.White, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Ink)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun VideoArtwork(video: DownloadedVideo, modifier: Modifier = Modifier, showDuration: Boolean = true) {
    val context = LocalContext.current.applicationContext
    val cacheKey = "${video.uri}|${video.size}|${video.modifiedAt}"
    var thumbnail by remember(cacheKey) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(cacheKey) { thumbnail = VideoThumbnailLoader.load(context, video) }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Ink), contentAlignment = Alignment.Center) {
        val image = thumbnail
        if (image != null && !image.isRecycled) {
            Image(image.asImageBitmap(), contentDescription = "Thumbnail for ${video.name}",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White,
                modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = .16f), CircleShape))
        }
        if (showDuration) VideoLibraryFormatting.duration(video.durationMillis)?.let { duration ->
            Text(duration, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .background(Ink.copy(alpha = .82f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp))
        }
    }
}
