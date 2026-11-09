package com.anas.streamsnap.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.style.TextOverflow
import com.anas.streamsnap.util.Utils
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anas.streamsnap.R
import com.anas.streamsnap.data.PLAYLIST_CONFIRM_THRESHOLD
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.data.Tr
import com.anas.streamsnap.data.tr
import com.anas.streamsnap.engine.PendingPlaylist
import com.anas.streamsnap.engine.QueueManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppRoot(activity: MainActivity) {
    val prefs by Settings.state.collectAsState()
    var splashDone by rememberSaveable { mutableStateOf(false) }
    val locked by activity.locked.collectAsState()

    val stage = when {
        !splashDone -> 0
        !prefs.onboarded -> 1
        locked -> 2
        else -> 3
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides (if (Tr.isUrdu) LayoutDirection.Rtl else LayoutDirection.Ltr)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Crossfade(targetState = stage, animationSpec = tween(350), label = "stage") { s ->
                when (s) {
                    0 -> SplashScreen { splashDone = true }
                    1 -> OnboardingScreen()
                    2 -> LockScreen(activity) { activity.locked.value = false }
                    else -> MainScaffold(activity)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------- splash
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val scale = remember { Animatable(0.55f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(550)) }
        launch { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        delay(1600)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0F1A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.streamsnap_logo), contentDescription = null,
                modifier = Modifier.size(124.dp).scale(scale.value).alpha(alpha.value).clip(RoundedCornerShape(30.dp)),
            )
            Spacer(Modifier.height(20.dp))
            Text("StreamSnap", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(alpha.value))
            Spacer(Modifier.height(6.dp))
            Text("Capture any stream, instantly.", color = Color(0xFFB8C4DA), fontSize = 14.sp, modifier = Modifier.alpha(alpha.value))
        }
    }
}

// ---------------------------------------------------------------------------------- onboarding
private data class OnbPage(val icon: ImageVector, val title: String, val body: String)

@Composable
fun OnboardingScreen() {
    val pages = listOf(
        OnbPage(
            Icons.Filled.ContentPaste,
            tr("Paste. That's it.", "بس پیسٹ کریں"),
            tr(
                "Paste one link or many. StreamSnap reads each link, shows a preview and downloads them one by one.",
                "ایک یا کئی لنک پیسٹ کریں۔ StreamSnap ہر لنک کا پری ویو دکھاتا ہے اور ایک ایک کر کے ڈاؤن لوڈ کرتا ہے۔",
            ),
        ),
        OnbPage(
            Icons.Filled.Tune,
            tr("Your rules", "آپ کے اصول"),
            tr(
                "Automatic or manual start, one quality for all videos or pick for each, Wi-Fi only, schedule, speed limit.",
                "خودکار یا دستی ڈاؤن لوڈ، سب کے لیے ایک کوالٹی یا ہر ویڈیو کی الگ، صرف وائی فائی، شیڈول، اسپیڈ لمٹ۔",
            ),
        ),
        OnbPage(
            Icons.Filled.PlayCircle,
            tr("Play and stay private", "دیکھیں اور محفوظ رہیں"),
            tr(
                "Built-in player with gestures, app lock, hidden folder and a full activity log you can copy.",
                "جیسچرز والا بلٹ ان پلیئر، ایپ لاک، چھپا ہوا فولڈر اور مکمل ایکٹیویٹی لاگ جسے کاپی کیا جا سکتا ہے۔",
            ),
        ),
    )
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.size - 1

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { Settings.update { it.copy(onboarded = true) } }) { Text(tr("Skip", "چھوڑیں")) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
            val pg = pages[i]
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(132.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(pg.icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(28.dp))
                Text(pg.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    pg.body, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
                )
                if (i == pages.size - 1) {
                    Spacer(Modifier.height(24.dp))
                    PermissionsBlock()
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val w by animateDpAsState(if (pager.currentPage == i) 26.dp else 8.dp, tween(250), label = "dot")
                Box(
                    Modifier.padding(horizontal = 4.dp).height(8.dp).size(width = w, height = 8.dp).clip(CircleShape).background(
                        if (pager.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
        Button(
            onClick = {
                if (last) Settings.update { it.copy(onboarded = true) }
                else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (last) tr("Get started", "شروع کریں") else tr("Next", "آگے")) }
    }
}

// ---------------------------------------------------------------------------------- main
private data class TabDef(val icon: ImageVector, val en: String, val ur: String)

private val tabs = listOf(
    TabDef(Icons.Filled.Download, "Queue", "قطار"),
    TabDef(Icons.Filled.History, "History", "ہسٹری"),
    TabDef(Icons.Filled.Language, "Browser", "براؤزر"),
    TabDef(Icons.Filled.BugReport, "Log", "لاگ"),
    TabDef(Icons.Filled.SettingsIcon, "Settings", "سیٹنگز"),
)

@Composable
fun MainScaffold(activity: MainActivity) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val nav by activity.navRequest.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val pending by QueueManager.pendingPlaylists.collectAsState()

    LaunchedEffect(nav) {
        nav?.let {
            tab = when (it) { "history" -> 1; "browser" -> 2; "logs" -> 3; "settings" -> 4; else -> 0 }
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
        if (Settings.value.autoKeyboard) return@LaunchedEffect
        repeat(10) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            withFrameNanos { }
        }
        delay(250)
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(tr(t.en, t.ur)) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                label = "tabs",
            ) { t ->
                when (t) {
                    0 -> HomeScreen(activity)
                    1 -> HistoryScreen()
                    2 -> BrowserScreen()
                    3 -> LogScreen()
                    else -> SettingsScreen(activity)
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
