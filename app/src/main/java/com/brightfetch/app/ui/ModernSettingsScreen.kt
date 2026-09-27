package com.brightfetch.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brightfetch.app.MainViewModel
import com.brightfetch.app.browser.BrowserSearchEngine
import com.brightfetch.app.download.DownloadPreferences
import com.brightfetch.app.storage.PlaybackStore
import com.brightfetch.app.BuildConfig
import com.brightfetch.app.ui.theme.Canvas
import com.brightfetch.app.ui.theme.Hairline
import com.brightfetch.app.ui.theme.Ink
import com.brightfetch.app.ui.theme.Muted
import com.brightfetch.app.ui.theme.SoftViolet
import com.brightfetch.app.ui.theme.Violet

@Composable
fun ModernSettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val browserSettings by viewModel.browserSettings.collectAsState()
    var wifiOnly by remember { mutableStateOf(DownloadPreferences.wifiOnly(context)) }
    var resumePlayback by remember { mutableStateOf(PlaybackStore.resumeEnabled(context)) }
    var engineMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Canvas).statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(20.dp))
        IconButton(onClick = onBack, modifier = Modifier.size(46.dp).background(Color.White, RoundedCornerShape(15.dp))) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
        }
        Spacer(Modifier.height(16.dp))
        Text("Settings", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text("Make every download feel effortless.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))
        SectionLabel("DOWNLOADS")
        Spacer(Modifier.height(9.dp))
        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(horizontal = 15.dp)) {
            SettingsSwitch(Icons.Default.Wifi, "Wi-Fi only", "Wait for Wi-Fi before starting", wifiOnly) {
                wifiOnly = it
                DownloadPreferences.setWifiOnly(context, it)
            }
            HorizontalDivider(color = Hairline)
            SettingsInfo(Icons.Default.Download, "Default quality", "Ask each time")
            HorizontalDivider(color = Hairline)
            SettingsInfo(Icons.Default.FolderOpen, "Save location", "Movies / BrightFetch")
        }
        Spacer(Modifier.height(28.dp))
        SectionLabel("PLAYBACK")
        Spacer(Modifier.height(9.dp))
        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(horizontal = 15.dp)) {
            SettingsSwitch(Icons.Default.PlayArrow, "Resume playback", "Continue where you left off", resumePlayback) {
                resumePlayback = it
                PlaybackStore.setResumeEnabled(context, it)
            }
        }
        Spacer(Modifier.height(28.dp))
        SectionLabel("BROWSER")
        Spacer(Modifier.height(9.dp))
        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(horizontal = 15.dp)) {
            Box {
                SettingsInfo(Icons.Default.Language, "Search engine", browserSettings.searchEngine.displayName,
                    onClick = { engineMenu = true })
                DropdownMenu(expanded = engineMenu, onDismissRequest = { engineMenu = false }) {
                    BrowserSearchEngine.entries.forEach { engine ->
                        DropdownMenuItem(text = { Text(engine.displayName) }, onClick = {
                            viewModel.setBrowserSearchEngine(engine)
                            engineMenu = false
                        })
                    }
                }
            }
            SettingsSwitch(Icons.Default.Tune, "Desktop sites", "Request desktop versions of pages",
                browserSettings.desktopModeEnabled, viewModel::setBrowserDesktopModeEnabled)
            SettingsSwitch(Icons.Default.Security, "Cookies", "Keep sign-ins between visits",
                browserSettings.cookiesEnabled, viewModel::setBrowserCookiesEnabled)
        }
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth().background(SoftViolet, RoundedCornerShape(17.dp)).padding(15.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = Violet)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Your media stays on your device.", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("No account needed.", color = Muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("BrightFetch · Version ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun SettingsInfo(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 12.dp)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Violet, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(15.dp))
        Column {
            Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsSwitch(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Violet, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 14.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
