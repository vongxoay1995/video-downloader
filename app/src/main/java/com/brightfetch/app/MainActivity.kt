package com.brightfetch.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brightfetch.app.ui.BrowserScreen
import com.brightfetch.app.ui.BrowserEntryPanel
import com.brightfetch.app.ui.ModernDownloadingScreen
import com.brightfetch.app.ui.ModernHomeScreen
import com.brightfetch.app.ui.ModernSettingsScreen
import com.brightfetch.app.ui.VideoPlayerScreen
import com.brightfetch.app.ui.ModernVideosScreen
import com.brightfetch.app.ui.rememberBrowserState
import com.brightfetch.app.ui.theme.BrightFetchTheme
import com.brightfetch.app.ui.theme.Canvas
import com.brightfetch.app.ui.theme.Hairline
import com.brightfetch.app.ui.theme.Ink
import com.brightfetch.app.ui.theme.Muted
import com.brightfetch.app.ui.theme.SoftViolet
import com.brightfetch.app.ui.theme.Violet

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    private val legacyStoragePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        requestLegacyStoragePermissionIfNeeded()
        setContent {
            BrightFetchTheme {
                BrightFetchApp()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestLegacyStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            legacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}

private enum class AppTab(val label: String) {
    HOME("Home"),
    DOWNLOADING("Downloads"),
    VIDEOS("Library"),
}

@Composable
private fun BrightFetchApp(mainViewModel: MainViewModel = viewModel()) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showBrowser by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var browserEntry by remember { mutableStateOf(BrowserEntryPanel.NONE) }
    val playingVideo by mainViewModel.playingVideo.collectAsState()
    val browserState = rememberBrowserState()
    val tabStateHolder = rememberSaveableStateHolder()

    DisposableEffect(browserState) {
        onDispose {
            browserState.dispose()
        }
    }

    val selectedVideo = playingVideo
    if (selectedVideo != null) {
        VideoPlayerScreen(
            video = selectedVideo,
            onBack = mainViewModel::closeVideoPlayer,
            initialPositionMillis = mainViewModel.playbackStartPosition(selectedVideo),
            onSaveProgress = { position, duration -> mainViewModel.savePlaybackProgress(selectedVideo, position, duration) },
        )
        return
    }

    if (showSettings) {
        ModernSettingsScreen(mainViewModel, onBack = { showSettings = false })
        return
    }

    if (showBrowser) {
        BrowserScreen(
            state = browserState,
            viewModel = mainViewModel,
            entryPanel = browserEntry,
            onExit = { showBrowser = false },
            onDownloadStarted = { showBrowser = false; selectedTab = AppTab.DOWNLOADING.ordinal },
        )
        return
    }

    BackHandler(enabled = selectedTab != AppTab.HOME.ordinal) { selectedTab = AppTab.HOME.ordinal }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Canvas,
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().height(66.dp)
                    .padding(horizontal = 22.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppTab.entries.forEachIndexed { index, tab ->
                    val icon = when (tab) {
                        AppTab.HOME -> Icons.Outlined.Home
                        AppTab.DOWNLOADING -> Icons.Outlined.FileDownload
                        AppTab.VIDEOS -> Icons.Outlined.VideoLibrary
                    }
                    val selected = selectedTab == index
                    Column(
                        Modifier.weight(1f).selectable(selected = selected, role = Role.Tab,
                            onClick = { selectedTab = index }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(width = 58.dp, height = 35.dp)
                                .background(if (selected) SoftViolet else Color.Transparent, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, contentDescription = tab.label,
                                tint = if (selected) Violet else Muted, modifier = Modifier.size(23.dp))
                        }
                        Text(tab.label, color = if (selected) Violet else Muted, fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            tabStateHolder.SaveableStateProvider(selectedTab) {
            when (AppTab.entries[selectedTab]) {
                AppTab.HOME -> ModernHomeScreen(
                    viewModel = mainViewModel,
                    onOpenBrowser = { target, entry ->
                        browserEntry = entry
                        if (target != null) {
                            mainViewModel.clearCandidates()
                            browserState.navigate(target, mainViewModel.browserSettings.value.searchEngine)
                        }
                        showBrowser = true
                    },
                    onOpenLibrary = { selectedTab = AppTab.VIDEOS.ordinal },
                    onOpenSettings = { showSettings = true },
                    onPlayVideo = mainViewModel::openVideoPlayer,
                )
                AppTab.DOWNLOADING -> ModernDownloadingScreen(
                    viewModel = mainViewModel,
                    onOpenBrowser = {
                        browserEntry = BrowserEntryPanel.NONE
                        showBrowser = true
                    },
                    onOpenLibrary = { selectedTab = AppTab.VIDEOS.ordinal },
                    onPlayVideo = mainViewModel::openVideoPlayer,
                )
                AppTab.VIDEOS -> ModernVideosScreen(
                    viewModel = mainViewModel,
                    onPlayVideo = mainViewModel::openVideoPlayer,
                    onSaveFirstVideo = { selectedTab = AppTab.HOME.ordinal },
                )
            }
            }
        }
    }
}
