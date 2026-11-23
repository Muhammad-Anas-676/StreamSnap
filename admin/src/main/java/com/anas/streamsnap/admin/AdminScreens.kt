package com.anas.streamsnap.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anas.streamsnap.core.cloud.AppConfig
import com.anas.streamsnap.core.cloud.AuthRepo
import com.anas.streamsnap.core.cloud.Cloud
import com.anas.streamsnap.core.cloud.CloudUser
import com.anas.streamsnap.core.cloud.LinkEvent
import com.anas.streamsnap.core.cloud.ScreenEvent
import com.anas.streamsnap.core.ui.MadeByAnasSignature
import com.anas.streamsnap.core.ui.ThemeAppearanceCard
import kotlinx.coroutines.launch

private val RANGES = listOf(7, 14, 30)

@Composable
private fun Header(title: String, loading: Boolean, onRefresh: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
        if (onRefresh != null) {
            IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun RangeChips(days: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RANGES.forEach { r ->
            FilterChip(selected = days == r, onClick = { AdminStore.setDays(r) }, label = { Text("Last $r days") })
        }
    }
}

@Composable
private fun ErrorLine(msg: String) {
    if (msg.isNotBlank()) {
        Text(
            msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
        )
    }
}

// ============================================================================ dashboard
@Composable
fun DashboardScreen() {
    val d by AdminStore.data.collectAsState()
    val m = remember(d) { compute(d) }
    Column(Modifier.fillMaxSize()) {
        Header("Dashboard", d.loading) { AdminStore.refresh() }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RangeChips(d.days)
            ErrorLine(d.error)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Total users", "${d.users.size}", Modifier.weight(1f))
                StatCard("New in period", "${m.newUsers}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Active in period", "${m.active}", Modifier.weight(1f))
                StatCard("Downloads done", "${m.done}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Failed", "${m.failed}", Modifier.weight(1f))
                StatCard("Success rate", m.rate, Modifier.weight(1f))
            }
            ChartCard("Sign-ups per day") { LineChart(m.signups, Fmt.shortDate(m.days.first()), Fmt.shortDate(m.days.last())) }
            ChartCard("Downloads finished per day") {
                BarChart(m.downloads, listOf(Fmt.shortDate(m.days.first()), Fmt.shortDate(m.days.last())))
            }
            ChartCard("Success vs failed") { DonutChart(listOf("Done" to m.done.toFloat(), "Failed" to m.failed.toFloat()).filter { it.second > 0 }) }
            ChartCard("Top sites", subtitle = "Links added in the period") { HBarChart(m.sites) }
            ChartCard("Quality chosen") { HBarChart(m.qualities) }
            ChartCard("Busiest hours", subtitle = "Screen visits by hour of day") { BarChart(m.hours, listOf("00:00", "12:00", "23:00")) }
            ChartCard("App versions in use") { HBarChart(m.versions) }
            Text(
                if (d.loadedAt > 0) "Updated ${Fmt.dateTime(d.loadedAt)}" else "Not loaded yet",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private class Metrics(
    val days: List<Long>, val newUsers: Int, val active: Int, val done: Int, val failed: Int, val rate: String,
    val signups: List<Float>, val downloads: List<Float>, val sites: List<Pair<String, Float>>,
    val qualities: List<Pair<String, Float>>, val hours: List<Float>, val versions: List<Pair<String, Float>>,
)

private fun compute(d: AdminData): Metrics {
    val days = lastDays(d.days)
    val since = days.first()
    val done = d.links.filter { it.status == "done" }
    val failed = d.links.filter { it.status == "failed" }
    val hours = FloatArray(24)
    d.screens.forEach { if (it.startAt > 0) hours[hourOf(it.startAt)] += 1f }
    val rate = if (done.size + failed.size > 0) "${done.size * 100 / (done.size + failed.size)}%" else "-"
    return Metrics(
        days = days,
        newUsers = d.users.count { it.createdAt >= since },
        active = d.users.count { it.lastActive >= since },
        done = done.size, failed = failed.size, rate = rate,
        signups = countPerDay(d.users.map { it.createdAt }, days),
        downloads = countPerDay(done.map { if (it.finishedAt > 0) it.finishedAt else it.addedAt }, days),
        sites = topCounts(d.links, { it.site.ifBlank { "Unknown" } }, 6),
        qualities = topCounts(d.links, { it.quality.ifBlank { "best" } }, 6),
        hours = hours.toList(),
        versions = topCounts(d.users, { it.appVersion.ifBlank { "Unknown" } }, 6),
    )
}

// ============================================================================ users
@Composable
fun UsersScreen(onOpen: (CloudUser) -> Unit) {
    val d by AdminStore.data.collectAsState()
    var q by remember { mutableStateOf("") }
    val list = remember(d.users, q) {
        d.users.filter {
            q.isBlank() || it.name.contains(q, true) || it.email.contains(q, true) || it.phone.contains(q, true) || it.country.contains(q, true)
        }.sortedByDescending { it.lastActive }
    }
    Column(Modifier.fillMaxSize()) {
        Header("Users (${d.users.size})", d.loading) { AdminStore.refresh() }
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true, label = { Text("Search name, email, phone, country") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ErrorLine(d.error)
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.uid }) { u ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(u) },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(u.name.ifBlank { "(no name)" }, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                            if (u.banned) {
                                Text(
                                    "BANNED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Last active ${Fmt.ago(u.lastActive)}  |  ${u.totalDownloads} downloads  |  ${u.country.ifBlank { "-" }}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserDetailScreen(user: CloudUser, onBack: () -> Unit) {
    val d by AdminStore.data.collectAsState()
    val u = d.users.firstOrNull { it.uid == user.uid } ?: user
    var tab by remember { mutableStateOf(0) }
    var links by remember { mutableStateOf<List<LinkEvent>>(emptyList()) }
    var screens by remember { mutableStateOf<List<ScreenEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var confirmBan by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(u.uid) {
        loading = true
        try {
            links = Cloud.fetchUserLinks(u.uid)
            screens = Cloud.fetchUserScreens(u.uid)
        } catch (e: Throwable) {
            error = e.message ?: "Could not load"
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground) }
            Column(Modifier.weight(1f)) {
                Text(u.name.ifBlank { "(no name)" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Overview", "Links (${links.size})", "Screens (${screens.size})").forEachIndexed { i, label ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(label) })
            }
        }
        ErrorLine(error)
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                0 -> {
                    item {
                        Card(
                            Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                val rows = listOf(
                                    "Status" to (if (u.banned) "Banned" else "Active"),
                                    "Phone" to u.phone.ifBlank { "-" }, "Country" to u.country.ifBlank { "-" },
                                    "Sign-in" to u.provider.ifBlank { "-" },
                                    "Joined" to Fmt.dateTime(u.createdAt), "Last active" to Fmt.dateTime(u.lastActive),
                                    "Downloads" to "${u.totalDownloads}", "Device" to u.device.ifBlank { "-" },
                                    "Android" to u.androidVersion.ifBlank { "-" }, "App version" to u.appVersion.ifBlank { "-" },
                                    "Accepted policy" to (if (u.consentVersion > 0) "v${u.consentVersion} on ${Fmt.dateTime(u.consentAt)}" else "Not yet"),
                                    "User ID" to u.uid,
                                )
                                rows.forEachIndexed { i, (k, v) ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(k, modifier = Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(v, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        if (u.banned) {
                            Button(onClick = { busy = true; AdminStore.setBanned(u.uid, false) { busy = false } }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Unban this user") }
                        } else {
                            OutlinedButton(
                                onClick = { confirmBan = true }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(50.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            ) { Text("Ban this user", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                1 -> {
                    if (links.isEmpty()) item { Text("No links yet", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(links, key = { it.id }) { LinkRow(it) }
                }
                else -> {
                    if (screens.isEmpty()) item { Text("No screen visits yet", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(screens, key = { it.id }) { VisitRow(it, null) }
                }
            }
        }
    }

    if (confirmBan) {
        AlertDialog(
            onDismissRequest = { confirmBan = false },
            title = { Text("Ban this user?") },
            text = { Text("The user will be blocked the next time the app opens and checks its status.") },
            confirmButton = { TextButton(onClick = { confirmBan = false; busy = true; AdminStore.setBanned(u.uid, true) { busy = false } }) { Text("Ban", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmBan = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LinkRow(l: LinkEvent) {
    Card(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(l.url, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            val statusColor = when (l.status) {
                "done" -> MaterialTheme.colorScheme.primary
                "failed" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(l.status.uppercase(), color = statusColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("${l.site.ifBlank { "Unknown" }} | ${l.quality} | ${Fmt.dateTime(l.addedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (l.error.isNotBlank()) Text(l.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun VisitRow(v: ScreenEvent, who: String?) {
    Card(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(v.screen, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (who != null) Text(who, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Started ${Fmt.dateTime(v.startAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(Fmt.duration(v.durationMs), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ============================================================================ screen time
@Composable
fun ScreenTimeScreen() {
    val d by AdminStore.data.collectAsState()
    val names = remember(d.users) { d.users.associate { it.uid to it.name.ifBlank { it.email.ifBlank { it.uid.take(8) } } } }
    val totals = remember(d.screens) {
        d.screens.groupBy { it.screen }.map { (k, v) -> Triple(k, v.sumOf { it.durationMs }, v.size) }.sortedByDescending { it.second }
    }
    val longest = remember(d.screens) { d.screens.sortedByDescending { it.durationMs }.take(40) }
    Column(Modifier.fillMaxSize()) {
        Header("Screen time", d.loading) { AdminStore.refresh() }
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { RangeChips(d.days) }
            item { ErrorLine(d.error) }
            item {
                ChartCard("Total time per screen", subtitle = "All users, ${d.screens.size} visits") {
                    HBarChart(totals.map { it.first to it.second.toFloat() }) { Fmt.duration(it.toLong()) }
                }
            }
            item {
                ChartCard("Average stay per visit") {
                    HBarChart(totals.map { it.first to (it.second / it.third.coerceAtLeast(1)).toFloat() }) { Fmt.duration(it.toLong()) }
                }
            }
            item { Text("Longest visits", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
            if (longest.isEmpty()) item { Text("No visits in this period", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(longest, key = { it.id }) { VisitRow(it, names[it.uid] ?: it.uid) }
        }
    }
}

// ============================================================================ more (control, appearance, about)
@Composable
fun MoreScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cfg by remember { mutableStateOf<AppConfig?>(null) }
    var maintenance by remember { mutableStateOf(false) }
    var announcement by remember { mutableStateOf("") }
    var minVer by remember { mutableStateOf("0") }
    var msg by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val c = AdminStore.loadConfig()
        cfg = c; maintenance = c.maintenance; announcement = c.announcement; minVer = c.minVersionCode.toString()
    }

    Column(Modifier.fillMaxSize()) {
        Header("More", false, null)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChartCard("Control the user app", subtitle = "Read by the user app every time it opens") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Maintenance mode", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            Text("Blocks the app and shows the message below", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = maintenance, onCheckedChange = { maintenance = it })
                    }
                    OutlinedTextField(
                        value = announcement, onValueChange = { announcement = it }, label = { Text("Announcement / maintenance message") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2,
                    )
                    OutlinedTextField(
                        value = minVer, onValueChange = { minVer = it.filter { c -> c.isDigit() } }, singleLine = true,
                        label = { Text("Minimum app version code (0 = no limit)") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Button(
                        onClick = {
                            saving = true; msg = ""
                            scope.launch {
                                try {
                                    val old = cfg ?: AppConfig()
                                    val changed = announcement.trim() != old.announcement
                                    Cloud.saveConfig(
                                        AppConfig(
                                            maintenance = maintenance, announcement = announcement.trim(),
                                            announcementId = if (changed && announcement.isNotBlank()) System.currentTimeMillis() else old.announcementId,
                                            minVersionCode = minVer.toLongOrNull() ?: 0,
                                        ),
                                    )
                                    cfg = AdminStore.loadConfig()
                                    msg = "Saved"
                                } catch (e: Throwable) { msg = e.message ?: "Could not save" }
                                saving = false
                            }
                        },
                        enabled = !saving, modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) { Text("Save") }
                    if (msg.isNotBlank()) Text(msg, color = if (msg == "Saved") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
            Text("Appearance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            ThemeAppearanceCard()
            Text("About", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Card(
                Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("StreamSnap Admin  v1.0.0", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Private tool for the StreamSnap developer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    MadeByAnasSignature()
                }
            }
            OutlinedButton(onClick = { scope.launch { AuthRepo.signOut(ctx) } }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Sign out") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
