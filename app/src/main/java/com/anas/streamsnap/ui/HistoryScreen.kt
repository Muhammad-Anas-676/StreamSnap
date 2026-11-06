package com.anas.streamsnap.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.anas.streamsnap.data.DownloadItem
import com.anas.streamsnap.data.Status
import com.anas.streamsnap.data.qualityLabel
import com.anas.streamsnap.data.tr
import com.anas.streamsnap.engine.QueueManager
import com.anas.streamsnap.player.PlayerActivity
import com.anas.streamsnap.util.Utils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

private fun shareFile(ctx: Context, item: DownloadItem) {
    try {
        val f = File(item.filePath)
        if (!f.exists()) {
            Toast.makeText(ctx, tr("File not found", "فائل نہیں ملی"), Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = Utils.mimeOf(item.filePath)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, tr("Share", "شیئر")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (t: Throwable) {
        Toast.makeText(ctx, tr("Could not share this file", "یہ فائل شیئر نہیں ہو سکی"), Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val all by QueueManager.items.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") } // all | video | audio
    var confirmClear by remember { mutableStateOf(false) }
    var deleteFilesToo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<DownloadItem?>(null) }

    val done = remember(all) { all.filter { it.status == Status.DONE }.sortedByDescending { it.finishedAt } }
    val shown = remember(done, query, filter) {
        val q = query.trim()
        done.filter { d ->
            val okType = when (filter) {
                "video" -> !d.isAudio
                "audio" -> d.isAudio
                else -> true
            }
            okType && (q.isEmpty() || d.displayTitle.contains(q, true) || d.site.contains(q, true) || d.uploader.contains(q, true))
        }
    }

    fun play(item: DownloadItem) {
        val f = File(item.filePath)
        if (item.filePath.isBlank() || !f.exists()) {
            Toast.makeText(ctx, tr("File is missing. Use Download again.", "فائل موجود نہیں۔ دوبارہ ڈاؤن لوڈ کریں۔"), Toast.LENGTH_SHORT).show()
            return
        }
        val playlist = ArrayList(shown.filter { it.filePath.isNotBlank() && File(it.filePath).exists() }.map { it.filePath })
        ctx.startActivity(PlayerActivity.intent(ctx, item.filePath, playlist))
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("History", "ہسٹری"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val total = done.sumOf { it.fileSize }
                Text(
                    tr("${done.size} downloads · ${Utils.fmtBytes(total)}", "${done.size} ڈاؤن لوڈ · ${Utils.fmtBytes(total)}"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (done.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = tr("Clear history", "ہسٹری صاف کریں"))
                }
            }
        }

        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text(tr("Search downloads", "ڈاؤن لوڈز تلاش کریں")) },
            shape = MaterialTheme.shapes.extraLarge,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(filter == "all", { filter = "all" }, label = { Text(tr("All", "سب")) })
            FilterChip(filter == "video", { filter = "video" }, label = { Text(tr("Video", "ویڈیو")) })
            FilterChip(filter == "audio", { filter = "audio" }, label = { Text(tr("Audio", "آڈیو")) })
        }

        if (shown.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.History, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (done.isEmpty()) tr("Finished downloads will appear here", "مکمل ڈاؤن لوڈز یہاں نظر آئیں گی")
                    else tr("Nothing matches", "کچھ نہیں ملا"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.id }) { item ->
                    HistoryCard(
                        item,
                        onPlay = { play(item) },
                        onShare = { shareFile(ctx, item) },
                        onDeleteFile = { confirmDelete = item },
                        onRemove = { QueueManager.removeFromHistory(item.id) },
                        onAgain = {
                            QueueManager.redownload(item.id)
                            Toast.makeText(ctx, tr("Added to queue", "قطار میں شامل"), Toast.LENGTH_SHORT).show()
                        },
                        onCopyLink = { copyToClipboard(ctx, "link", item.url) },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(tr("Clear history?", "ہسٹری صاف کریں؟")) },
            text = {
                Column {
                    Text(tr("Finished downloads will be removed from this list.", "مکمل ڈاؤن لوڈز اس فہرست سے ہٹ جائیں گی۔"))
                    Row(
                        Modifier.fillMaxWidth().clickable { deleteFilesToo = !deleteFilesToo }.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = deleteFilesToo, onCheckedChange = { deleteFilesToo = it })
                        Text(tr("Also delete the files from my phone", "فائلیں فون سے بھی حذف کریں"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { QueueManager.clearHistory(deleteFilesToo); confirmClear = false; deleteFilesToo = false }) {
                    Text(tr("Clear", "صاف کریں"))
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr("Cancel", "منسوخ")) } },
        )
    }

    confirmDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(tr("Delete file?", "فائل حذف کریں؟")) },
            text = { Text(d.displayTitle, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = { QueueManager.deleteWithFile(d.id); confirmDelete = null }) { Text(tr("Delete", "حذف کریں")) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(tr("Cancel", "منسوخ")) } },
        )
    }
}

@Composable
private fun HistoryCard(
    item: DownloadItem,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDeleteFile: () -> Unit,
    onRemove: () -> Unit,
    onAgain: () -> Unit,
    onCopyLink: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val exists = remember(item.filePath) { item.filePath.isNotBlank() && File(item.filePath).exists() }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay),
    ) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(112.dp).height(64.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (item.thumbnail.isNotBlank()) {
                    AsyncImage(
                        model = item.thumbnail, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        if (item.isAudio) Icons.Filled.AudioFile else Icons.Filled.VideoFile, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp),
                    )
                }
                if (exists) {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.PlayArrow, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val size = if (item.fileSize > 0) Utils.fmtBytes(item.fileSize) else ""
                val meta = listOf(
                    item.site.ifBlank { Utils.siteName(item.url) },
                    qualityLabel(item.quality),
                    size,
                ).filter { it.isNotBlank() }.joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (exists) dateFmt.format(Date(item.finishedAt.takeIf { it > 0 } ?: item.addedAt))
                    else tr("File missing", "فائل موجود نہیں"),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (exists) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (exists) {
                        DropdownMenuItem(text = { Text(tr("Share", "شیئر")) }, onClick = { menu = false; onShare() })
                    }
                    DropdownMenuItem(text = { Text(tr("Download again", "دوبارہ ڈاؤن لوڈ")) }, onClick = { menu = false; onAgain() })
                    DropdownMenuItem(text = { Text(tr("Copy link", "لنک کاپی")) }, onClick = { menu = false; onCopyLink() })
                    DropdownMenuItem(text = { Text(tr("Remove from list", "فہرست سے ہٹائیں")) }, onClick = { menu = false; onRemove() })
                    if (exists) {
                        DropdownMenuItem(
                            text = { Text(tr("Delete file", "فائل حذف کریں"), color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDeleteFile() },
                        )
                    }
                }
            }
        }
    }
}
