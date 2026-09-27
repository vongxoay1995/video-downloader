package com.brightfetch.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brightfetch.app.model.MediaCandidate
import com.brightfetch.app.ui.theme.Hairline
import com.brightfetch.app.ui.theme.Ink
import com.brightfetch.app.ui.theme.Muted
import com.brightfetch.app.ui.theme.SoftViolet
import com.brightfetch.app.ui.theme.Violet

@Composable
internal fun ModernBrowserLanding(
    state: BrowserState,
    onNavigate: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(44.dp))
        Box(Modifier.size(62.dp).background(SoftViolet, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Violet, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("Explore & save.", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Search above or visit a page with a public video. We'll let you know when there's something you can save.",
            color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
        if (state.hasHiddenPage) {
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SoftViolet)
                .clickable { state.restoreHiddenPage() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Violet)
                Spacer(Modifier.width(10.dp))
                Text("Return to your page", color = Violet, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(30.dp))
        Text("Quick access", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrowserShortcut("Google", "G", Color(0xFF4285F4), Modifier.weight(1f)) { onNavigate("https://www.google.com") }
            BrowserShortcut("DuckDuckGo", "D", Color(0xFFE46E45), Modifier.weight(1f)) { onNavigate("https://duckduckgo.com") }
            BrowserShortcut("Bing", "B", Color(0xFF087E72), Modifier.weight(1f)) { onNavigate("https://www.bing.com") }
        }
        Spacer(Modifier.height(30.dp))
        Text("Your browsing", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(12.dp))
        BrowserMenuRow(Icons.Default.BookmarkBorder, "Saved sites", "Jump back to your favorites", onOpenBookmarks)
        Spacer(Modifier.height(9.dp))
        BrowserMenuRow(Icons.Default.History, "Recent pages", "Pick up where you left off", onOpenHistory)
        Spacer(Modifier.height(9.dp))
        BrowserMenuRow(Icons.Default.Settings, "Browser settings", "Search, cookies and privacy", onOpenSettings)
        Spacer(Modifier.height(28.dp))
        Text("Only download content you own or have permission to save. Protected, paywalled and YouTube content isn't supported.",
            color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BrowserShortcut(label: String, mark: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.background(Color.White, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(36.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Text(mark, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Ink, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun BrowserMenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(SoftViolet, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Violet, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetectedVideoPicker(
    candidates: List<MediaCandidate>,
    onDismiss: () -> Unit,
    onChoose: (MediaCandidate) -> Unit,
) {
    var selected by remember(candidates) { mutableIntStateOf(0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFFF8F9FC)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Text("Pick your video", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("${candidates.size} video${if (candidates.size == 1) "" else "s"} found on this page",
                color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Column(Modifier.height(320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                candidates.forEachIndexed { index, candidate ->
                    val active = selected == index
                    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp))
                        .border(1.dp, if (active) Violet else Hairline, RoundedCornerShape(18.dp))
                        .clickable { selected = index }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(68.dp).background(SoftViolet, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Violet, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(candidate.title.ifBlank { candidate.suggestedFileName }, color = Ink,
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val host = runCatching { Uri.parse(candidate.pageUrl ?: candidate.url).host }.getOrNull()?.removePrefix("www.")
                            Text(listOfNotNull(host, candidate.height?.let { "${it}p" },
                                candidate.contentLengthBytes?.let(VideoLibraryFormatting::bytes)).joinToString(" · "),
                                color = Muted, fontSize = 11.sp, maxLines = 1)
                        }
                        Box(Modifier.size(22.dp).border(1.dp, if (active) Violet else Hairline, CircleShape)
                            .background(if (active) Violet else Color.White, CircleShape), contentAlignment = Alignment.Center) {
                            if (active) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            BrandAction("Continue · 1 video", Icons.AutoMirrored.Filled.ArrowForward) {
                candidates.getOrNull(selected)?.let(onChoose)
            }
            Spacer(Modifier.height(10.dp))
            Text("Next, choose your download quality.", color = Muted, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(18.dp))
        }
    }
}
