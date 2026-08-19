package com.brightfetch.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brightfetch.app.ui.BrowserScreen
import com.brightfetch.app.ui.DownloadingScreen
import com.brightfetch.app.ui.VideosScreen
import com.brightfetch.app.ui.rememberBrowserState
import com.brightfetch.app.ui.theme.BrightFetchTheme

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
    DOWNLOADING("Downloading"),
    VIDEOS("Videos"),
}

@Composable
private fun BrightFetchApp(mainViewModel: MainViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val browserState = rememberBrowserState()

    DisposableEffect(browserState) {
        onDispose {
            browserState.webView?.destroy()
            browserState.webView = null
            browserState.mediaReporter = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFFFFF6CE)) {
                AppTab.entries.forEachIndexed { index, tab ->
                    val icon = when (tab) {
                        AppTab.HOME -> Icons.Default.Home
                        AppTab.DOWNLOADING -> Icons.Default.Download
                        AppTab.VIDEOS -> Icons.Default.Folder
                    }
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (AppTab.entries[selectedTab]) {
                AppTab.HOME -> BrowserScreen(browserState, mainViewModel)
                AppTab.DOWNLOADING -> DownloadingScreen(mainViewModel)
                AppTab.VIDEOS -> VideosScreen(mainViewModel)
            }
        }
    }
}
