package com.anas.streamsnap.ui

import com.anas.streamsnap.session.Session
import com.anas.streamsnap.core.theme.BannerPalette
import androidx.compose.material.icons.filled.Campaign
import com.anas.streamsnap.data.tr3
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.anas.streamsnap.R
import com.anas.streamsnap.data.DownloadItem
import com.anas.streamsnap.data.SavedLinks
import com.anas.streamsnap.data.QUALITY_OPTIONS
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.data.Status
import com.anas.streamsnap.data.qualityLabel
import com.anas.streamsnap.data.tr
import com.anas.streamsnap.engine.QueueManager
import com.anas.streamsnap.util.Utils

@Composable
fun HomeScreen(activity: MainActivity) {
    val ctx = LocalContext.current
    val all by QueueManager.items.collectAsState()
    val prefs by Settings.state.collectAsState()
    val clip by activity.clipLink.collectAsState()
    val queue = remember(all) { all.filter { it.status != Status.DONE } }
    val done = remember(all) { all.filter { it.status == Status.DONE }.sortedByDescending { it.finishedAt } }
    val saved by SavedLinks.items.collectAsState()

    var input by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf(0) } // 0 = Downloading, 1 = Downloaded
    var confirmDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    // Select several (both sections)
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmBulk by remember { mutableStateOf(false) }
    // Downloaded: search + sort
    var dQuery by rememberSaveable { mutableStateOf("") }
    var dSort by rememberSaveable { mutableStateOf(0) }       // 0 date, 1 size, 2 name
    var dAsc by rememberSaveable { mutableStateOf(false) }
    val doneShown = remember(done, dQuery, dSort, dAsc) {
        val q = dQuery.trim()
        val f = if (q.isEmpty()) done else done.filter { d ->
            d.displayTitle.contains(q, true) || d.site.contains(q, true) || d.uploader.contains(q, true)
        }
        val cmp: Comparator<DownloadItem> = when (dSort) {
            1 -> compareBy<DownloadItem> { it.fileSize }
            2 -> compareBy<DownloadItem> { it.displayTitle.lowercase() }
            else -> compareBy<DownloadItem> { it.finishedAt }
        }
        f.sortedWith(if (dAsc) cmp else cmp.reversed())
    }
    val pool = if (section == 0) queue else doneShown
    val selIds = pool.filter { it.id in selected }.map { it.id }
    fun toggle(id: String) { selected = if (id in selected) selected - id else selected + id }
    fun startSelect(id: String?) { selecting = true; selected = if (id == null) emptySet() else setOf(id) }
    fun endSelect() { selecting = false; selected = emptySet() }
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    fun submit(text: String) {
        val r = QueueManager.addLinks(text)
        if (!r.noLinks) {
            input = ""
            section = 0 // show the new link in Downloading
        }
        Toast.makeText(ctx, addResultMessage(r), Toast.LENGTH_SHORT).show()
    }

    fun reorder() {
        val id = draggingId ?: return
        val infos = listState.layoutInfo.visibleItemsInfo
        val cur = infos.firstOrNull { it.key == id } ?: return
        val center = cur.offset + dragOffset + cur.size / 2f
        val target = infos.firstOrNull { x ->
            val k = x.key
            k != id && k is String && QueueManager.get(k) != null &&
                center > x.offset && center < x.offset + x.size
        } ?: return
        val delta = (target.offset - cur.offset).toFloat()
        QueueManager.swap(id, target.key as String)
        dragOffset -= delta
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AnnouncementBanner()
        HomeHeader()
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "add") {
                AddCard(
                    input = input,
                    onSaveLater = {
                        val r = QueueManager.saveLinksForLater(input)
                        if (!r.noLinks) input = ""
                        Toast.makeText(
                            ctx,
                            when {
                                r.noLinks -> tr("No link found", "کوئی لنک نہیں ملا")
                                r.added == 0 -> tr("Already saved", "پہلے سے محفوظ ہے")
                                else -> tr("Saved for later: ${r.added}", "بعد کے لیے محفوظ: ${r.added}")
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onInput = { new ->
                        val grew = new.length - input.length
                        input = new
                        // A paste (many characters at once) adds the links straight away in both modes.
                        if (grew >= 10 && Utils.extractUrls(new).isNotEmpty()) submit(new)
                    },
                    onAdd = { submit(input) },
                    onPaste = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = try {
                            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                        } catch (_: Throwable) { "" }
                        if (text.isBlank()) Toast.makeText(ctx, tr("Clipboard is empty", "کلپ بورڈ خالی ہے"), Toast.LENGTH_SHORT).show()
                        else submit(text)
                    },
                )
            }

            val c = clip
            if (c != null) {
                item(key = "clip") {
                    ClipboardCard(
                        text = c,
                        modifier = Modifier.animateItem(),
                        onAdd = {
                            val r = QueueManager.addLinks(c)
                            section = 0
                            activity.dismissClip()
                            Toast.makeText(ctx, addResultMessage(r), Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { activity.dismissClip() },
                    )
                }
            }

            if (saved.isNotEmpty()) {
                item(key = "saved") {
                    AssistChip(
                        onClick = { showSaved = true },
                        label = { Text(tr("Saved for later (${saved.size})", "بعد کے لیے محفوظ (${saved.size})")) },
                        leadingIcon = { Icon(Icons.Filled.Bookmark, null, Modifier.size(18.dp)) },
                    )
                }
            }

            item(key = "sections") {
                SectionSwitch(
                    selected = section,
                    left = tr("Downloading (${queue.size})", "ڈاؤن لوڈ ہو رہی (${queue.size})"),
                    right = tr("Downloaded (${done.size})", "ڈاؤن لوڈ شدہ (${done.size})"),
                    onSelect = { section = it; endSelect() },
                )
            }

            if (section == 0) {
                if (queue.isNotEmpty()) {
                    item(key = "controls") {
                        ControlsRow(
                            queue.map { it.status },
                            onClear = { confirmClear = true },
                            onCopyAll = {
                                copyToClipboard(ctx, "links", queue.joinToString("\n") { it.url })
                            },
                            onSelect = { startSelect(null) },
                        )
                    }
                }

                if (queue.isEmpty()) {
                    item(key = "empty") { EmptyState(prefs.autoStart) }
                }

                items(queue, key = { it.id }) { item ->
                    val dragging = draggingId == item.id
                    val handle = Modifier.pointerInput(item.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggingId = item.id; dragOffset = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                reorder()
                            },
                            onDragEnd = { draggingId = null; dragOffset = 0f },
                            onDragCancel = { draggingId = null; dragOffset = 0f },
                        )
                    }
                    SwipeQueueCard(
                        item = item,
                        prefs = prefs,
                        dragging = dragging,
                        handle = handle,
                        modifier = if (dragging) Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset }
                        else Modifier.animateItem(),
                        selecting = selecting,
                        selected = item.id in selected,
                        onToggle = { toggle(item.id) },
                        onSelect = { startSelect(item.id) },
                    )
                }
            } else {
                if (done.isEmpty()) {
                    item(key = "doneEmpty") { DownloadedEmpty() }
                } else {
                    item(key = "doneTools") {
                        DownloadedTools(
                            query = dQuery,
                            onQuery = { dQuery = it },
                            sort = dSort,
                            asc = dAsc,
                            onSort = { k -> if (dSort == k) dAsc = !dAsc else { dSort = k; dAsc = (k == 2) } },
                            onSelect = { startSelect(null) },
                            info = tr(
                                "${doneShown.size} of ${done.size} downloads · ${Utils.fmtBytes(doneShown.sumOf { it.fileSize })}",
                                "${done.size} میں سے ${doneShown.size} ڈاؤن لوڈ · ${Utils.fmtBytes(doneShown.sumOf { it.fileSize })}",
                            ),
                        )
                    }
                    if (doneShown.isEmpty()) {
                        item(key = "doneNoMatch") {
                            Text(
                                tr("Nothing matches your search", "تلاش سے کچھ نہیں ملا"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                    items(doneShown, key = { it.id }) { d ->
                        DownloadedCard(
                            item = d,
                            all = doneShown,
                            onAskDelete = { confirmDelete = it },
                            onAgain = { section = 0 },
                            selectMode = selecting,
                            selected = d.id in selected,
                            onToggle = { toggle(d.id) },
                            onSelectStart = { startSelect(d.id) },
                        )
                    }
                }
            }
        }
        if (selecting) {
            val chosen = pool.filter { it.id in selected }
            SelectionBar(
                count = selIds.size,
                total = pool.size,
                onAll = { selected = if (selIds.size == pool.size) emptySet() else pool.map { it.id }.toSet() },
                onClose = { endSelect() },
            ) {
                if (section == 0) {
                    FilledTonalButton(enabled = selIds.isNotEmpty(), onClick = { QueueManager.pauseMany(selIds) }) { Text(tr("Pause", "روکیں")) }
                    FilledTonalButton(enabled = selIds.isNotEmpty(), onClick = { QueueManager.resumeMany(selIds) }) { Text(tr("Resume", "شروع")) }
                    OutlinedButton(
                        enabled = selIds.isNotEmpty(),
                        onClick = { copyToClipboard(ctx, "links", chosen.joinToString("\n") { it.url }) },
                    ) { Text(tr("Copy links", "لنک کاپی")) }
                } else {
                    FilledTonalButton(enabled = selIds.isNotEmpty(), onClick = { shareFiles(ctx, chosen) }) { Text(tr("Share", "شیئر")) }
                    OutlinedButton(
                        enabled = selIds.isNotEmpty(),
                        onClick = { copyToClipboard(ctx, "links", chosen.joinToString("\n") { it.url }) },
                    ) { Text(tr("Copy links", "لنک کاپی")) }
                }
                OutlinedButton(enabled = selIds.isNotEmpty(), onClick = { confirmBulk = true }) {
                    Text(
                        if (section == 0) tr("Remove", "ہٹائیں") else tr("Delete files", "فائلیں حذف کریں"),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showSaved) SavedLinksDialog { showSaved = false }

    if (confirmBulk) {
        AlertDialog(
            onDismissRequest = { confirmBulk = false },
            title = {
                Text(
                    if (section == 0) tr("Remove ${selIds.size} item(s)?", "${selIds.size} آئٹم ہٹائیں؟")
                    else tr("Delete ${selIds.size} file(s)?", "${selIds.size} فائلیں حذف کریں؟"),
                )
            },
            text = {
                Text(
                    if (section == 0) tr("They are removed from the queue and running downloads stop.", "یہ قطار سے ہٹ جائیں گی اور چلتی ڈاؤن لوڈز رک جائیں گی۔")
                    else tr("The files are deleted from your phone.", "فائلیں آپ کے فون سے حذف ہو جائیں گی۔"),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (section == 0) QueueManager.cancelMany(selIds) else QueueManager.deleteManyWithFiles(selIds)
                    confirmBulk = false
                    endSelect()
                }) { Text(if (section == 0) tr("Remove", "ہٹائیں") else tr("Delete", "حذف کریں")) }
            },
            dismissButton = { TextButton(onClick = { confirmBulk = false }) { Text(tr("Cancel", "منسوخ")) } },
        )
    }

    DeleteFileDialog(confirmDelete) { confirmDelete = null }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(tr("Remove everything?", "سب کچھ ہٹائیں؟")) },
            text = { Text(tr("All items in the queue will be removed and running downloads stopped.", "قطار کی تمام آئٹمز ہٹ جائیں گی اور چلتی ہوئی ڈاؤن لوڈز رک جائیں گی۔")) },
            confirmButton = { TextButton(onClick = { QueueManager.cancelAll(); confirmClear = false }) { Text(tr("Remove all", "سب ہٹائیں")) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr("Cancel", "منسوخ")) } },
        )
    }
}

// ----------------------------------------------------------------------------------- header
/** Admin announcement at the top of the Queue screen, in the colour the admin chose. Can be dismissed. */
@Composable
private fun AnnouncementBanner() {
    val current by Session.banner.collectAsState()
    val banner = current ?: return
    val (fill, textColor) = remember(banner.color) { BannerPalette.fillAndText(BannerPalette.entry(banner.color).color) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp)
            .clip(MaterialTheme.shapes.medium).background(fill)
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Campaign, null, tint = textColor)
        Text(banner.text, modifier = Modifier.weight(1f), color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        IconButton(onClick = { Session.dismissBanner() }) {
            Icon(Icons.Filled.Close, contentDescription = tr3("Dismiss", "Hataein", "ہٹائیں"), tint = textColor)
        }
    }
}

@Composable
private fun HomeHeader() {
    val prefs by Settings.state.collectAsState()
    var qMenu by remember { mutableStateOf(false) }
    val ask = prefs.qualityMode == "ask"
    val custom = prefs.cvOn

    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(
                painterResource(R.drawable.streamsnap_logo), null,
                Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)),
            )
            Text(
                "StreamSnap", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            // Mode pill: tap to switch Automatic / Manual
            Surface(
                onClick = {
                    Settings.update { it.copy(autoStart = !it.autoStart) }
                    QueueManager.recheck()
                },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (prefs.autoStart) Icons.Filled.FlashOn else Icons.Filled.TouchApp, null,
                        Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        if (prefs.autoStart) tr3("Auto", "Auto", "خودکار") else tr3("Manual", "Manual", "دستی"),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StorageMeter(Modifier.weight(1f))
            androidx.compose.foundation.layout.Box {
                Surface(
                    onClick = { qMenu = true },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        when {
                            custom -> tr3("Custom", "Custom", "کسٹم")
                            ask -> tr3("Each video", "Har video", "ہر ویڈیو")
                            else -> qualityLabel(prefs.defaultQuality)
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    )
                }
                DropdownMenu(expanded = qMenu, onDismissRequest = { qMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(tr3("Pick for each video", "Har video ki alag chunein", "ہر ویڈیو کی الگ چنیں")) },
                        onClick = { Settings.update { it.copy(qualityMode = "ask", cvOn = false) }; qMenu = false },
                    )
                    QUALITY_OPTIONS.forEach { (k, label) ->
                        DropdownMenuItem(
                            text = { Text(tr3("All videos: ", "Tamam videos: ", "تمام ویڈیوز: ") + label) },
                            onClick = {
                                Settings.update { it.copy(qualityMode = "fixed", defaultQuality = k, cvOn = false) }
                                qMenu = false
                            },
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------------- cards
@Composable
private fun AddCard(input: String, onInput: (String) -> Unit, onAdd: () -> Unit, onPaste: () -> Unit, onSaveLater: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val firstUrl = remember(input) { Utils.extractUrls(input).firstOrNull() }
            if (firstUrl != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlatformTag(Utils.siteName(firstUrl), firstUrl)
                    Text(
                        Utils.hostOf(firstUrl), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
            }
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.fillMaxWidth().tapToType(),
                minLines = 1,
                maxLines = 3,
                placeholder = { Text(tr("Paste video links here (one or many)", "ویڈیو لنک یہاں پیسٹ کریں (ایک یا کئی)")) },
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onPaste, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(tr("Paste", "پیسٹ"))
                }
                Button(onClick = onAdd, enabled = input.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(tr("Add", "شامل کریں"))
                }
                IconButton(onClick = onSaveLater, enabled = input.isNotBlank()) {
                    Icon(Icons.Filled.Bookmark, contentDescription = tr("Save for later", "بعد کے لیے محفوظ"))
                }
            }
        }
    }
}

@Composable
private fun ClipboardCard(text: String, modifier: Modifier, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val urls = Utils.extractUrls(text)
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.ContentPaste, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Column(Modifier.weight(1f)) {
                Text(tr("Link found in clipboard", "کلپ بورڈ میں لنک ملا"), style = MaterialTheme.typography.titleSmall)
                Text(
                    (urls.firstOrNull()?.let { Utils.hostOf(it) } ?: "") +
                        if (urls.size > 1) " +${urls.size - 1}" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onDismiss) { Text(tr("No", "نہیں")) }
            Button(onClick = onAdd) { Text(tr("Add", "شامل")) }
        }
    }
}

@Composable
private fun ControlsRow(statuses: List<Status>, onClear: () -> Unit, onCopyAll: () -> Unit, onSelect: () -> Unit) {
    val prefs by Settings.state.collectAsState()
    val anyRunning = statuses.any { it == Status.DOWNLOADING || it == Status.QUEUED }
    val anyPaused = statuses.any { it == Status.PAUSED || it == Status.WAITING }
    val anyReady = statuses.any { it == Status.READY }
    val anyScheduled = statuses.any { it == Status.SCHEDULED }

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (anyReady && !prefs.autoStart) {
            Button(onClick = { QueueManager.startAllReady() }) { Text(tr("Download all", "سب ڈاؤن لوڈ کریں")) }
        }
        if (anyScheduled) {
            FilledTonalButton(onClick = { QueueManager.releaseScheduled() }) { Text(tr("Start now", "ابھی شروع کریں")) }
        }
        if (anyRunning) {
            FilledTonalButton(onClick = { QueueManager.pauseAll() }) { Text(tr("Pause all", "سب روکیں")) }
        }
        if (anyPaused) {
            FilledTonalButton(onClick = { QueueManager.resumeAll() }) { Text(tr("Resume all", "سب شروع کریں")) }
        }
        OutlinedButton(onClick = onSelect) { Text(tr("Select", "منتخب کریں")) }
        OutlinedButton(onClick = onCopyAll) { Text(tr("Copy all links", "تمام لنک کاپی")) }
        OutlinedButton(onClick = onClear) { Text(tr("Clear all", "سب ہٹائیں")) }
    }
}

/** Bottom bar while several cards are being selected. [actions] are the buttons for the current section. */
@Composable
private fun SelectionBar(count: Int, total: Int, onAll: () -> Unit, onClose: () -> Unit, actions: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("$count selected", "$count منتخب"),
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onAll) {
                    Text(if (total > 0 && count == total) tr("Select none", "کوئی نہیں") else tr("Select all", "سب چنیں"))
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = tr("Done", "مکمل")) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions()
            }
        }
    }
}

/** Search box, sort chips and "Select" above the finished downloads. */
@Composable
private fun DownloadedTools(
    query: String,
    onQuery: (String) -> Unit,
    sort: Int,
    asc: Boolean,
    onSort: (Int) -> Unit,
    onSelect: () -> Unit,
    info: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().tapToType(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, contentDescription = tr("Clear", "صاف کریں")) } }
            } else null,
            placeholder = { Text(tr("Search downloads", "ڈاؤن لوڈز تلاش کریں")) },
            shape = MaterialTheme.shapes.extraLarge,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(0 to tr("Date", "تاریخ"), 1 to tr("Size", "سائز"), 2 to tr("Name", "نام")).forEach { (k, label) ->
                FilterChip(
                    selected = sort == k,
                    onClick = { onSort(k) },
                    label = { Text(label) },
                    trailingIcon = if (sort == k) {
                        { Icon(if (asc) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, null, Modifier.size(16.dp)) }
                    } else null,
                )
            }
            AssistChip(
                onClick = onSelect,
                label = { Text(tr("Select", "منتخب کریں")) },
                leadingIcon = { Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp)) },
            )
        }
        Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Two-part switch under the link box: Downloading (queue) and Downloaded (finished, playable). */
@Composable
private fun SectionSwitch(selected: Int, left: String, right: String, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(left, right).forEachIndexed { i, label ->
            val on = selected == i
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DownloadedEmpty() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(R.drawable.streamsnap_logo), null,
            Modifier.size(84.dp).clip(RoundedCornerShape(22.dp)),
        )
        Spacer(Modifier.height(16.dp))
        Text(tr("No finished downloads yet", "ابھی کوئی مکمل ڈاؤن لوڈ نہیں"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            tr("Finished videos appear here. Tap one to play it.", "مکمل ویڈیوز یہاں نظر آئیں گی۔ چلانے کے لیے ٹیپ کریں۔"),
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
        )
    }
}

@Composable
private fun EmptyState(auto: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(R.drawable.streamsnap_logo), null,
            Modifier.size(84.dp).clip(RoundedCornerShape(22.dp)),
        )
        Spacer(Modifier.height(16.dp))
        Text(tr("Nothing in the queue", "قطار خالی ہے"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (auto) tr("Paste a link above and it starts by itself.", "اوپر لنک پیسٹ کریں، ڈاؤن لوڈ خود شروع ہو جائے گی۔")
            else tr("Paste links above, then tap the download icon on each card.", "اوپر لنک پیسٹ کریں، پھر ہر کارڈ پر ڈاؤن لوڈ آئیکن دبائیں۔"),
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
        )
    }
}
