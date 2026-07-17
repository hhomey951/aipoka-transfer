@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.aipoka.transfer.ui.screens

import android.text.InputType
import android.text.format.DateFormat
import android.widget.EditText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.livedata.observeAsState
import com.aipoka.transfer.ManualSendHelper
import com.aipoka.transfer.MediaFolders
import com.aipoka.transfer.Prefs
import com.aipoka.transfer.Scheduler
import com.aipoka.transfer.ui.theme.AipokaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class HomeTab { HOME, HISTORY, SETTINGS }

@Composable
fun HomeScreen(
    darkOverride: Boolean?,
    onDarkOverrideChange: (Boolean?) -> Unit,
    onUnpaired: () -> Unit
) {
    val colors = AipokaTheme.colors
    var tab by remember { mutableStateOf(HomeTab.HOME) }

    Scaffold(
        containerColor = colors.pageBg,
        bottomBar = {
            NavigationBar(
                containerColor = colors.cardBg,
                modifier = Modifier.shadow(elevation = 16.dp, ambientColor = colors.accent.copy(alpha = 0.15f), spotColor = colors.accent.copy(alpha = 0.2f))
            ) {
                NavigationBarItem(
                    selected = tab == HomeTab.HOME,
                    onClick = { tab = HomeTab.HOME },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = colors.accent, indicatorColor = colors.accentSoft)
                )
                NavigationBarItem(
                    selected = tab == HomeTab.HISTORY,
                    onClick = { tab = HomeTab.HISTORY },
                    icon = { Icon(Icons.Filled.History, contentDescription = "History") },
                    label = { Text("History") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = colors.accent, indicatorColor = colors.accentSoft)
                )
                NavigationBarItem(
                    selected = tab == HomeTab.SETTINGS,
                    onClick = { tab = HomeTab.SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = colors.accent, indicatorColor = colors.accentSoft)
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                HomeTab.HOME -> HomeTabContent(
                    darkOverride = darkOverride,
                    onDarkOverrideChange = onDarkOverrideChange,
                    onSeeAllHistory = { tab = HomeTab.HISTORY }
                )
                HomeTab.HISTORY -> HistoryTabContent()
                HomeTab.SETTINGS -> SettingsTabContent(
                    darkOverride = darkOverride,
                    onDarkOverrideChange = onDarkOverrideChange,
                    onUnpaired = onUnpaired
                )
            }
        }
    }
}

@Composable
private fun HomeTabContent(
    darkOverride: Boolean?,
    onDarkOverrideChange: (Boolean?) -> Unit,
    onSeeAllHistory: () -> Unit
) {
    val colors = AipokaTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val syncRunningLiveData = remember { Scheduler.syncRunning(context) }
    val syncActive by syncRunningLiveData.observeAsState(false)

    var showFolderNameDialog by remember { mutableStateOf<List<android.net.Uri>?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) showFolderNameDialog = uris
    }

    val serverUrl = Prefs.getServerUrl(context)
    val deviceHost = remember(serverUrl) {
        if (serverUrl.isNullOrBlank()) {
            "-"
        } else {
            try {
                android.net.Uri.parse(serverUrl).host ?: serverUrl
            } catch (e: Exception) {
                serverUrl
            }
        }
    }
    val lastSync = Prefs.getLastSync(context)
    var syncFolder by remember { mutableStateOf(Prefs.getSyncFolder(context)) }
    var showFolderSheet by remember { mutableStateOf(false) }
    val name = Prefs.getProfileName(context)?.takeIf { it.isNotBlank() } ?: "there"
    val history = remember { Prefs.getUploadHistory(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBg),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("AIPOKA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.sub)
                    Text("Hi, $name", fontSize = 24.sp, fontWeight = FontWeight.Black, color = colors.ink)
                }
                val isDark = darkOverride ?: androidx.compose.foundation.isSystemInDarkTheme()
                IconButton(
                    onClick = { onDarkOverrideChange(!isDark) },
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = colors.accent.copy(alpha = 0.3f), spotColor = colors.accent.copy(alpha = 0.4f))
                        .background(colors.accentSoft, CircleShape)
                ) {
                    Icon(
                        if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = "Toggle appearance",
                        tint = colors.accentGlyph
                    )
                }
            }
        }

        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip()
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(deviceHost, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.ink)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(colors.good, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paired · $deviceHost", fontSize = 12.sp, color = colors.sub)
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                        if (syncActive) {
                            val transition = rememberInfiniteTransition(label = "pulse")
                            val scale by transition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.4f,
                                animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
                                label = "pulseScale"
                            )
                            val alpha by transition.animateFloat(
                                initialValue = 0.5f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
                                label = "pulseAlpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .scale(scale)
                                    .background(colors.accent.copy(alpha = alpha), CircleShape)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .shadow(elevation = 8.dp, shape = CircleShape, ambientColor = colors.accent.copy(alpha = 0.35f), spotColor = colors.accent.copy(alpha = 0.5f))
                                .background(colors.accentSoft, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = colors.accentGlyph)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (syncActive) "Syncing in background" else if (Prefs.isSyncPaused(context)) "Sync paused" else "Idle",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.ink
                    )
                    val lastSyncText = if (lastSync == 0L) "never" else DateFormat.format("MMM d, h:mm a", lastSync).toString()
                    Text("Last synced $lastSyncText", fontSize = 12.sp, color = colors.sub)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.clickable { showFolderSheet = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Folder: ${syncFolder ?: "All photos"}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    val syncButtonColor = if (syncActive) colors.danger else colors.accent
                    Button(
                        onClick = {
                            if (syncActive) {
                                Scheduler.stopSync(context)
                            } else {
                                Scheduler.triggerNow(context, fromUser = true)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 10.dp, shape = RoundedCornerShape(14.dp), ambientColor = syncButtonColor.copy(alpha = 0.35f), spotColor = syncButtonColor.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = syncButtonColor,
                            contentColor = colors.onAccent
                        )
                    ) {
                        Text(if (syncActive) "Stop Sync" else "Sync Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.clickable {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(icon = Icons.Filled.Photo)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Select photos & send", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.ink)
                        Text("Send a batch straight to a named folder on your PC", fontSize = 12.sp, color = colors.sub)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.sub)
                }
            }
        }

        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("RECENTLY SYNCED", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.sub)
                    Text(
                        "See all",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent,
                        modifier = Modifier.clickable { onSeeAllHistory() }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (history.isEmpty()) {
                    Text("No photos synced yet.", fontSize = 13.sp, color = colors.sub)
                } else {
                    Card {
                        Column {
                            history.take(3).forEachIndexed { index, entry ->
                                HistoryRow(entry.displayName, entry.uploadedAtMillis)
                                if (index < minOf(2, history.size - 1)) Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    showFolderNameDialog?.let { uris ->
        FolderNameDialog(
            photoCount = uris.size,
            onDismiss = { showFolderNameDialog = null },
            onConfirm = { folderName ->
                showFolderNameDialog = null
                scope.launch {
                    val (paths, names) = withContext(Dispatchers.IO) {
                        ManualSendHelper.copyToTempFiles(context, uris)
                    }
                    if (paths.isNotEmpty()) {
                        ManualSendHelper.enqueueSend(context, paths, names, folderName)
                    }
                }
            }
        )
    }

    if (showFolderSheet) {
        FolderPickerSheet(
            currentFolder = syncFolder,
            onDismiss = { showFolderSheet = false },
            onSelect = { newFolder ->
                if (newFolder != Prefs.getSyncFolder(context)) {
                    Prefs.setSyncFolder(context, newFolder)
                    // Old watermark belongs to the previous folder's timeline; reset so
                    // nothing older in the new selection is silently skipped. The PC's
                    // name+size duplicate check makes the resulting re-uploads safe.
                    Prefs.setLastSync(context, 0L)
                }
                Prefs.setSyncPaused(context, false)
                syncFolder = newFolder
                showFolderSheet = false
            }
        )
    }
}

@Composable
private fun HistoryTabContent() {
    val colors = AipokaTheme.colors
    val context = LocalContext.current
    val history = remember { Prefs.getUploadHistory(context) }

    Column(modifier = Modifier.fillMaxSize().background(colors.pageBg).padding(20.dp)) {
        Text("History", fontSize = 26.sp, fontWeight = FontWeight.Black, color = colors.ink)
        Spacer(modifier = Modifier.height(16.dp))
        if (history.isEmpty()) {
            Text("No photos synced yet.", fontSize = 14.sp, color = colors.sub)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(history) { entry ->
                    Card { HistoryRow(entry.displayName, entry.uploadedAtMillis) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(displayName: String, uploadedAtMillis: Long) {
    val colors = AipokaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.good, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(displayName, fontSize = 14.sp, color = colors.ink, maxLines = 1)
            Text(
                "Sent · ${DateFormat.format("MMM d, h:mm a", uploadedAtMillis)}",
                fontSize = 12.sp,
                color = colors.sub
            )
        }
    }
}

@Composable
private fun SettingsTabContent(
    darkOverride: Boolean?,
    onDarkOverrideChange: (Boolean?) -> Unit,
    onUnpaired: () -> Unit
) {
    val colors = AipokaTheme.colors
    val context = LocalContext.current
    var showFolderSheet by remember { mutableStateOf(false) }
    var showSyncModeSheet by remember { mutableStateOf(false) }
    var showSyncTypeSheet by remember { mutableStateOf(false) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showUnpairSheet by remember { mutableStateOf(false) }
    var syncFolder by remember { mutableStateOf(Prefs.getSyncFolder(context)) }
    var syncType by remember { mutableStateOf(Prefs.getSyncType(context)) }

    var showFolderNameDialog by remember { mutableStateOf<List<android.net.Uri>?>(null) }
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) showFolderNameDialog = uris
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBg)
    ) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item { Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Black, color = colors.ink) }
            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                Card {
                    Column {
                        SettingsRow("Device name", android.os.Build.MODEL, onClick = null)
                        Divider()
                        SettingsRow("Sync folder", syncFolder ?: "All photos", onClick = { showFolderSheet = true })
                        Divider()
                        SettingsRow("Sync mode", "All new photos", onClick = { showSyncModeSheet = true })
                        Divider()
                        SettingsRow(
                            "Sync type",
                            when (syncType) {
                                Prefs.SyncType.ALL -> "All photos"
                                Prefs.SyncType.LAST_30_DAYS -> "Last 30 days"
                                Prefs.SyncType.TODAY -> "Today's photos"
                            },
                            onClick = { showSyncTypeSheet = true }
                        )
                        Divider()
                        SettingsRow(
                            "Appearance",
                            when (darkOverride) { true -> "Dark"; false -> "Light"; null -> "System" },
                            onClick = { showAppearanceSheet = true }
                        )
                        Divider()
                        SettingsRow("Paired PC", Prefs.getServerUrl(context) ?: "-", onClick = null)
                        Divider()
                        SettingsRow("Sync interval", "~15 min + instant", onClick = null)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Card {
                    Column {
                        Text("Aipoka for Windows", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.ink)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Get the desktop receiver app for your PC.",
                            fontSize = 12.sp,
                            color = colors.sub
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Download",
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                            // Placeholder — this app has no public download URL yet.
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Card {
                    Column {
                        Text("How it works", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.ink)
                        Spacer(modifier = Modifier.height(10.dp))
                        listOf(
                            "Install Aipoka on your PC and open it — it shows a pairing code and QR.",
                            "Pair this phone once: type the code or scan the QR.",
                            "New photos sync automatically over WiFi in the background.",
                            "Or pick specific photos any time and send them to a named folder."
                        ).forEachIndexed { i, step ->
                            Row(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text("${i + 1}.", fontWeight = FontWeight.Bold, color = colors.accent, modifier = Modifier.width(20.dp))
                                Text(step, fontSize = 13.sp, color = colors.sub)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            item {
                OutlinedButton(
                    onClick = { showUnpairSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.danger)
                ) {
                    Text("Unpair this device", fontWeight = FontWeight.Bold)
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showFolderSheet) {
        FolderPickerSheet(
            currentFolder = syncFolder,
            onDismiss = { showFolderSheet = false },
            onSelect = { newFolder ->
                if (newFolder != Prefs.getSyncFolder(context)) {
                    Prefs.setSyncFolder(context, newFolder)
                    // Old watermark belongs to the previous folder's timeline; reset so
                    // nothing older in the new selection is silently skipped. The PC's
                    // name+size duplicate check makes the resulting re-uploads safe.
                    Prefs.setLastSync(context, 0L)
                }
                Prefs.setSyncPaused(context, false)
                syncFolder = newFolder
                showFolderSheet = false
            }
        )
    }

    if (showSyncTypeSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { showSyncTypeSheet = false }, sheetState = sheetState, containerColor = colors.sheetBg) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Sync type", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colors.ink)
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    Triple("All photos", Prefs.SyncType.ALL, "Sync every photo in the selection"),
                    Triple("Last 30 days", Prefs.SyncType.LAST_30_DAYS, "Only photos added in the last 30 days"),
                    Triple("Today's photos", Prefs.SyncType.TODAY, "Only photos added today")
                ).forEach { (label, value, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (value != Prefs.getSyncType(context)) {
                                    Prefs.setSyncType(context, value)
                                    // Old watermark belongs to the previous time window; reset
                                    // so a narrower->broader switch doesn't silently skip older
                                    // photos. The PC's name+size duplicate check makes the
                                    // resulting re-uploads safe.
                                    Prefs.setLastSync(context, 0L)
                                }
                                syncType = value
                                Prefs.setSyncPaused(context, false)
                                showSyncTypeSheet = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = syncType == value, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(label, color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(desc, color = colors.sub, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showSyncModeSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { showSyncModeSheet = false }, sheetState = sheetState, containerColor = colors.sheetBg) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Sync mode", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colors.ink)
                Spacer(modifier = Modifier.height(12.dp))
                // NOTE: SyncWorker/Prefs only support "all photos" or "one album" (see
                // Prefs.getSyncFolder), not a persistent list of individually-selected
                // photo IDs. "Selected photos" here is a shortcut into the existing
                // one-shot manual-send picker, not a new persistent sync mode — building
                // real per-photo-ID sync state would require SyncWorker changes, which is
                // out of scope for this UI pass.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSyncModeSheet = false }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = true, onClick = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("All new photos", color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Auto-sync in the background (default)", color = colors.sub, fontSize = 12.sp)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showSyncModeSheet = false
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = false, onClick = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Selected photos", color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Pick photos to send once", color = colors.sub, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showAppearanceSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { showAppearanceSheet = false }, sheetState = sheetState, containerColor = colors.sheetBg) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colors.ink)
                Spacer(modifier = Modifier.height(12.dp))
                listOf<Triple<String, Boolean?, String>>(
                    Triple("System", null, "Follow device setting"),
                    Triple("Light", false, "Always use light theme"),
                    Triple("Dark", true, "Always use dark theme")
                ).forEach { (label, value, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDarkOverrideChange(value)
                                showAppearanceSheet = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = darkOverride == value, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(label, color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(desc, color = colors.sub, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showUnpairSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { showUnpairSheet = false }, sheetState = sheetState, containerColor = colors.sheetBg) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Unpair this device?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colors.ink)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "This phone will stop syncing to this PC. You can pair again any time.",
                    fontSize = 13.sp,
                    color = colors.sub
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        Scheduler.stop(context)
                        Prefs.clearPairing(context)
                        showUnpairSheet = false
                        onUnpaired()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.danger, contentColor = Color.White)
                ) {
                    Text("Unpair", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showUnpairSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    }

    showFolderNameDialog?.let { uris ->
        FolderNameDialog(
            photoCount = uris.size,
            onDismiss = { showFolderNameDialog = null },
            onConfirm = { folderName ->
                showFolderNameDialog = null
                scope.launch {
                    val (paths, names) = withContext(Dispatchers.IO) {
                        ManualSendHelper.copyToTempFiles(context, uris)
                    }
                    if (paths.isNotEmpty()) {
                        ManualSendHelper.enqueueSend(context, paths, names, folderName)
                    }
                }
            }
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: (() -> Unit)?) {
    val colors = AipokaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = colors.ink)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontSize = 13.sp, color = colors.sub, maxLines = 1)
            if (onClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.sub, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FolderPickerSheet(currentFolder: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    val colors = AipokaTheme.colors
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.sheetBg) {
        val folders = listOf("All Photos") + MediaFolders.list(context)
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Sync from", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colors.ink)
            Spacer(modifier = Modifier.height(12.dp))
            folders.forEach { folder ->
                val selected = if (folder == "All Photos") currentFolder == null else currentFolder == folder
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(if (folder == "All Photos") null else folder) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(folder, color = colors.ink, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    val colors = AipokaTheme.colors
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.border))
}

@Composable
private fun IconChip(icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.CloudUpload) {
    val colors = AipokaTheme.colors
    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(elevation = 5.dp, shape = CircleShape, ambientColor = colors.accent.copy(alpha = 0.3f), spotColor = colors.accent.copy(alpha = 0.4f))
            .background(colors.accentSoft, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = colors.accentGlyph, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = AipokaTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = shape, ambientColor = colors.accent.copy(alpha = 0.18f), spotColor = colors.accent.copy(alpha = 0.28f))
            .background(colors.cardBg, shape)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun FolderNameDialog(photoCount: Int, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var folderName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Folder name on PC") },
        text = {
            Column {
                Text("$photoCount photo(s) will be saved into this folder.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    placeholder = { Text("e.g. Birthday 2026") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (folderName.isNotBlank()) onConfirm(folderName.trim()) }, enabled = folderName.isNotBlank()) {
                Text("Send")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
