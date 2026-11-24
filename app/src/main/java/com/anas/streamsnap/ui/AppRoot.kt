package com.anas.streamsnap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.anas.streamsnap.core.cloud.ScreenTracker
import com.anas.streamsnap.data.Tr
import com.anas.streamsnap.data.tr
import com.anas.streamsnap.data.tr3
import com.anas.streamsnap.engine.PendingPlaylist
import com.anas.streamsnap.engine.QueueManager
import com.anas.streamsnap.session.Session
import com.anas.streamsnap.util.Utils
import kotlinx.coroutines.delay


@Composable
fun AppRoot(activity: MainActivity) {
    val locked by activity.locked.collectAsState()

    val user by Session.user.collectAsState()
    val consented by Session.consented.collectAsState()
    val gate by Session.gate.collectAsState()
    val notices by Session.notices.collectAsState()
    val permsDone by Session.permissionsDone.collectAsState()
    val cloud = Session.cloudOn

    // 2 lock, 4 sign in, 5 consent, 6 blocked, 7 permissions, 3 the app
    // There is no onboarding or splash stage: the first screen is the lock screen (if on) or sign-in.
    // Permissions are asked once, after sign-in and consent.
    val stage = when {
        locked -> 2
        cloud && user == null -> 4
        cloud && !consented -> 5
        cloud && gate != null -> 6
        !permsDone -> 7
        else -> 3
    }

    // The clipboard may be scanned only on the real app screens (past lock, sign-in, consent, gate and permissions).
    LaunchedEffect(stage) { activity.setMainReady(stage == 3) }
    DisposableEffect(Unit) { onDispose { activity.setMainReady(false) } }

    // Once signed in and consented: tell the cloud we are here, check ban / maintenance / update, flush on leaving.
    LaunchedEffect(user?.uid, consented) {
        if (cloud && user != null && consented) {
            Session.touch()
            Session.startLive()
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides (if (Tr.isUrdu) LayoutDirection.Rtl else LayoutDirection.Ltr)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            notices.firstOrNull()?.let { n ->
                AlertDialog(
                    onDismissRequest = { Session.dismissNotice(n.id) },
                    title = {
                        Column {
                            Text(
                                "Message from StreamSnap Team", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(n.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = { Text(n.message) },
                    confirmButton = { TextButton(onClick = { Session.dismissNotice(n.id) }) { Text(tr3("OK", "Theek hai", "ٹھیک ہے")) } },
                )
            }
            Crossfade(targetState = stage, animationSpec = tween(350), label = "stage") { s ->
                when (s) {
                    2 -> LockScreen(activity) { activity.locked.value = false }
                    4 -> AuthScreen(activity)
                    5 -> ConsentScreen()
                    6 -> gate?.let { GateScreen(it) }
                    7 -> PermissionsScreen { Session.completePermissions() }
                    else -> MainScaffold(activity)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------- main
private data class TabDef(val icon: ImageVector, val en: String, val ur: String)

private val tabs = listOf(
    TabDef(Icons.Filled.Download, "Queue", "قطار"),
    TabDef(Icons.Filled.History, "History", "ہسٹری"),
    TabDef(Icons.Filled.Language, "Browser", "براؤزر"),
    TabDef(Icons.Filled.Person, "Profile", "پروفائل"),
)

@Composable
fun MainScaffold(activity: MainActivity) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val nav by activity.navRequest.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val pending by QueueManager.pendingPlaylists.collectAsState()

    LaunchedEffect(nav) {
        nav?.let {
            when (it) {
                "history" -> tab = 1
                "browser" -> tab = 2
                "profile" -> tab = 3
                "settings" -> { tab = 3; showSettings = true }
                else -> tab = 0
            }
            activity.navRequest.value = null
        }
    }
    LaunchedEffect(Unit) {
        QueueManager.events.collect { snackbar.showSnackbar(it) }
    }

    // The keyboard must open only when a text field is tapped. A screen that is just opened must not
    // grab the focus (and with it the keyboard), so for a moment after each tab change the focus is cleared.
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(tab) {
        repeat(10) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            withFrameNanos { }
        }
        delay(250)
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    // Screen time for the admin: each tab is a screen (Settings and Legal report themselves).
    LaunchedEffect(tab, showSettings) {
        if (!showSettings) ScreenTracker.enter(when (tab) { 0 -> "Queue"; 1 -> "History"; 2 -> "Browser"; else -> "Profile" })
    }
    BackHandler(enabled = showSettings) { showSettings = false }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i; showSettings = false },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(tr(t.en, t.ur)) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AnimatedContent(
                targetState = tab to showSettings,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                label = "tabs",
            ) { (t, settingsOpen) ->
                if (settingsOpen) {
                    SettingsScreen(activity, onBack = { showSettings = false })
                } else when (t) {
                    0 -> HomeScreen(activity)
                    1 -> HistoryScreen()
                    2 -> BrowserScreen()
                    else -> ProfileScreen(onOpenSettings = { showSettings = true })
                }
            }
        }
    }

    pending.firstOrNull()?.let { PlaylistDialog(it) }
}

/** Checklist of the videos in a pasted playlist: tick the ones to download. */
@Composable
private fun PlaylistDialog(pp: PendingPlaylist) {
    var picked by remember(pp.id) { mutableStateOf(pp.entries.indices.toSet()) }
    AlertDialog(
        onDismissRequest = { QueueManager.confirmPlaylist(pp, emptyList()) },
        title = { Text(tr("Choose videos", "ویڈیوز چنیں")) },
        text = {
            Column {
                Text(
                    "\"${pp.title}\" · ${pp.entries.size} " + tr("videos", "ویڈیوز"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                Row {
                    TextButton(onClick = { picked = pp.entries.indices.toSet() }) { Text(tr("All", "سب")) }
                    TextButton(onClick = { picked = emptySet() }) { Text(tr("None", "کوئی نہیں")) }
                }
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    itemsIndexed(pp.entries) { i, m ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { picked = if (i in picked) picked - i else picked + i }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(checked = i in picked, onCheckedChange = null)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    m.title.ifBlank { m.url },
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (m.durationSec > 0) {
                                    Text(
                                        Utils.fmtDuration(m.durationSec),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = picked.isNotEmpty(),
                onClick = { QueueManager.confirmPlaylist(pp, pp.entries.filterIndexed { i, _ -> i in picked }) },
            ) { Text(tr("Add (${picked.size})", "شامل کریں (${picked.size})")) }
        },
        dismissButton = {
            TextButton(onClick = { QueueManager.confirmPlaylist(pp, emptyList()) }) { Text(tr("Cancel", "منسوخ")) }
        },
    )
}
