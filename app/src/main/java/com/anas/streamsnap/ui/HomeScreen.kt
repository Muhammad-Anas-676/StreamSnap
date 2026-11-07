package com.anas.streamsnap.ui

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
import androidx.compose.material.icons.filled.FlashOn
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

    var input by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf(0) } // 0 = Downloading, 1 = Downloaded
    var confirmDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
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

            item(key = "sections") {
                SectionSwitch(
                    selected = section,
                    left = tr("Downloading (${queue.size})", "ڈاؤن لوڈ ہو رہی (${queue.size})"),
                    right = tr("Downloaded (${done.size})", "ڈاؤن لوڈ شدہ (${done.size})"),
                    onSelect = { section = it },
                )
            }

            if (section == 0) {
                if (queue.isNotEmpty()) {
                    item(key = "controls") { ControlsRow(queue.map { it.status }, onClear = { confirmClear = true }) }
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
                        onOpenLog = { activity.navRequest.value = "logs" },
                    )
                }
            } else {
                if (done.isEmpty()) {
                    item(key = "doneEmpty") { DownloadedEmpty() }
                } else {
                    item(key = "doneInfo") {
                        Text(
                            tr(
                                "${done.size} downloads · ${Utils.fmtBytes(done.sumOf { it.fileSize })}",
                                "${done.size} ڈاؤن لوڈ · ${Utils.fmtBytes(done.sumOf { it.fileSize })}",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(done, key = { it.id }) { d ->
                        DownloadedCard(
                            item = d,
                            all = done,
                            onAskDelete = { confirmDelete = it },
                            onAgain = { section = 0 },
                        )
                    }
                }
            }
        }
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
@Composable
private fun HomeHeader() {
    val prefs by Settings.state.collectAsState()
    var qMenu by remember { mutableStateOf(false) }
    val ask = prefs.qualityMode == "ask"

    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(
                painterResource(R.drawable.streamsnap_logo), null,
                Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)),
            )
            Column {
                Text("StreamSnap", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    tr("Paste links, get videos", "لنک پیسٹ کریں، ویڈیوز پائیں"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = true,
                onClick = {
                    Settings.update { it.copy(autoStart = !it.autoStart) }
                    QueueManager.recheck()
                },
                label = { Text(if (prefs.autoStart) tr("Automatic", "خودکار") else tr("Manual", "دستی")) },
                leadingIcon = {
                    Icon(if (prefs.autoStart) Icons.Filled.FlashOn else Icons.Filled.TouchApp, null, Modifier.size(18.dp))
                },
            )
            androidx.compose.foundation.layout.Box {
                AssistChip(
                    onClick = { qMenu = true },
                    label = {
                        Text(
                            if (ask) tr("Quality: each video", "کوالٹی: ہر ویڈیو")
                            else tr("Quality: ", "کوالٹی: ") + qualityLabel(prefs.defaultQuality)
                        )
                    },
                )
                DropdownMenu(expanded = qMenu, onDismissRequest = { qMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("Pick for each video", "ہر ویڈیو کی الگ چنیں")) },
                        onClick = { Settings.update { it.copy(qualityMode = "ask") }; qMenu = false },
                    )
                    QUALITY_OPTIONS.forEach { (k, label) ->
                        DropdownMenuItem(
                            text = { Text(tr("All videos: ", "تمام ویڈیوز: ") + label) },
                            onClick = {
                                Settings.update { it.copy(qualityMode = "fixed", defaultQuality = k) }
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
private fun AddCard(input: String, onInput: (String) -> Unit, onAdd: () -> Unit, onPaste: () -> Unit) {
    val prefs by Settings.state.collectAsState()
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.fillMaxWidth().tapToType(),
                minLines = 2,
                maxLines = 5,
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
            }
            Text(
                if (prefs.autoStart) tr("Automatic: downloads start as soon as a link is pasted.", "خودکار: لنک پیسٹ کرتے ہی ڈاؤن لوڈ شروع ہو جاتی ہے۔")
                else tr("Manual: tap the download icon on a card to start.", "دستی: شروع کرنے کے لیے کارڈ پر ڈاؤن لوڈ آئیکن دبائیں۔"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun ControlsRow(statuses: List<Status>, onClear: () -> Unit) {
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
        OutlinedButton(onClick = onClear) { Text(tr("Clear all", "سب ہٹائیں")) }
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
