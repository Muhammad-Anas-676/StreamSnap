package com.anas.streamsnap.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.anas.streamsnap.data.tr
import com.anas.streamsnap.util.LogEntry
import com.anas.streamsnap.util.LogLevel
import com.anas.streamsnap.util.LogStore
import java.io.File

fun copyToClipboard(ctx: Context, label: String, text: String) {
    try {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(ctx, tr("Copied", "کاپی ہو گیا"), Toast.LENGTH_SHORT).show()
    } catch (t: Throwable) {
        Toast.makeText(ctx, tr("Could not copy", "کاپی نہیں ہو سکا"), Toast.LENGTH_SHORT).show()
    }
}

private fun shareLog(ctx: Context, text: String) {
    try {
        val dir = File(ctx.cacheDir, "logs").apply { mkdirs() }
        val f = File(dir, "streamsnap-log.txt")
        f.writeText(text)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "StreamSnap log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, tr("Share log", "لاگ شیئر کریں")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (t: Throwable) {
        Toast.makeText(ctx, tr("Could not share", "شیئر نہیں ہو سکا"), Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun LogScreen() {
    val ctx = LocalContext.current
    val all by LogStore.entries.collectAsState()
    var filter by rememberSaveable { mutableStateOf("all") } // all | error | warn | info
    var query by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    val shown = remember(all, filter, query) {
        val q = query.trim()
        all.asReversed().filter { e ->
            val okLevel = when (filter) {
                "error" -> e.level == LogLevel.ERROR
                "warn" -> e.level == LogLevel.WARN
                "info" -> e.level == LogLevel.INFO
                else -> true
            }
            okLevel && (q.isEmpty() ||
                e.message.contains(q, true) || e.detail.contains(q, true) ||
                e.url.contains(q, true) || e.tag.contains(q, true))
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("Activity Log", "ایکٹیویٹی لاگ"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    tr("Exact errors. Copy and send them for a fix.", "درست ایرر یہاں آتے ہیں۔ کاپی کر کے بھیجیں۔"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { copyToClipboard(ctx, "StreamSnap log", LogStore.formatAll(shown.asReversed())) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = tr("Copy all", "سب کاپی کریں"))
            }
            IconButton(onClick = { shareLog(ctx, LogStore.formatAll(shown.asReversed())) }) {
                Icon(Icons.Filled.Share, contentDescription = tr("Share as .txt", "txt کے طور پر شیئر"))
            }
            IconButton(onClick = { confirmClear = true }) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = tr("Clear", "صاف کریں"))
            }
        }

        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text(tr("Search the log", "لاگ میں تلاش کریں")) },
            shape = MaterialTheme.shapes.extraLarge,
        )

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val counts = remember(all) {
                Triple(all.count { it.level == LogLevel.ERROR }, all.count { it.level == LogLevel.WARN }, all.size)
            }
            FilterChip(filter == "all", { filter = "all" }, label = { Text(tr("All", "سب") + " ${counts.third}") })
            FilterChip(filter == "error", { filter = "error" }, label = { Text(tr("Errors", "ایرر") + " ${counts.first}") })
            FilterChip(filter == "warn", { filter = "warn" }, label = { Text(tr("Warnings", "وارننگ") + " ${counts.second}") })
            FilterChip(filter == "info", { filter = "info" }, label = { Text(tr("Info", "معلومات")) })
        }

        if (shown.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Info, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (all.isEmpty()) tr("No log yet. Errors will show up here.", "ابھی لاگ خالی ہے۔ ایرر یہاں نظر آئیں گے۔")
                    else tr("Nothing matches", "کچھ نہیں ملا"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown) { e ->
                    LogCard(e) { copyToClipboard(ctx, "StreamSnap log entry", LogStore.formatOne(e)) }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(tr("Clear the log?", "لاگ صاف کریں؟")) },
            text = { Text(tr("All log entries will be removed.", "لاگ کی تمام انٹریز ہٹ جائیں گی۔")) },
            confirmButton = { TextButton(onClick = { LogStore.clear(); confirmClear = false }) { Text(tr("Clear", "صاف کریں")) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr("Cancel", "منسوخ")) } },
        )
    }
}

@Composable
private fun LogCard(e: LogEntry, onCopy: () -> Unit) {
    var open by rememberSaveable(e.time, e.message.hashCode()) { mutableStateOf(e.level == LogLevel.ERROR) }
    val color = when (e.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> Color(0xFFE6A100)
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
    }
    val icon = when (e.level) {
        LogLevel.ERROR -> Icons.Filled.Error
        LogLevel.WARN -> Icons.Filled.Warning
        LogLevel.INFO -> Icons.Filled.Info
    }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.clickable { open = !open }.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                Text(e.tag.uppercase(), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    LogStore.fmtTime(e.time), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = tr("Copy", "کاپی"), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            if (open) {
                SelectionContainer {
                    Column {
                        Text(e.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        if (e.url.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text("URL: ${e.url}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (e.detail.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                e.detail.trimEnd(),
                                fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            } else {
                Text(e.message, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                if (e.detail.isNotBlank()) {
                    Text(
                        tr("Tap to see exact error", "درست ایرر دیکھنے کے لیے ٹیپ کریں"),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
