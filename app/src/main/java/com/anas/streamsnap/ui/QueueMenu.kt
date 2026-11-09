package com.anas.streamsnap.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anas.streamsnap.data.DownloadItem
import com.anas.streamsnap.data.SUB_LANGS
import com.anas.streamsnap.data.SavedLinks
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.data.Status
import com.anas.streamsnap.data.subLangNames
import com.anas.streamsnap.data.tr
import com.anas.streamsnap.engine.QueueManager
import com.anas.streamsnap.util.Utils
import java.io.File

/**
 * The round "three dots" button on a queue card (top corner of the thumbnail) and its menu:
 * copy / share the exact pasted link, open it in the app's Browser, move it to the front, copy the error,
 * rename, subtitles, save for later, select several, remove.
 */
@Composable
internal fun QueueMenuButton(item: DownloadItem, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var dlg by remember { mutableStateOf<String?>(null) }

    Box(modifier) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Color(0x99000000)).clickable { menu = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MoreVert, contentDescription = tr("More", "مزید"),
                tint = Color.White, modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(tr("Copy link", "لنک کاپی کریں")) },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                onClick = { menu = false; copyToClipboard(ctx, "link", item.url) },
            )
            DropdownMenuItem(
                text = { Text(tr("Share link", "لنک شیئر کریں")) },
                leadingIcon = { Icon(Icons.Filled.Share, null) },
                onClick = { menu = false; shareText(ctx, item.url) },
            )
            DropdownMenuItem(
                text = { Text(tr("Open in Browser", "براؤزر میں کھولیں")) },
                leadingIcon = { Icon(Icons.Filled.OpenInBrowser, null) },
                onClick = { menu = false; openInBrowserTab(ctx, item.url) },
            )
            if (item.status != Status.DOWNLOADING) {
                DropdownMenuItem(
                    text = { Text(tr("Download next", "اگلی ڈاؤن لوڈ کریں")) },
                    leadingIcon = { Icon(Icons.Filled.SkipNext, null) },
                    onClick = { menu = false; QueueManager.downloadNext(item.id) },
                )
            }
            if (item.error.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text(tr("Copy error", "ایرر کاپی کریں")) },
                    leadingIcon = { Icon(Icons.Filled.Error, null) },
                    onClick = { menu = false; copyToClipboard(ctx, "error", item.error) },
                )
            }
            DropdownMenuItem(
                text = { Text(tr("Rename", "نام بدلیں")) },
                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                onClick = { menu = false; dlg = "rename" },
            )
            DropdownMenuItem(
                text = { Text(tr("Subtitles", "سب ٹائٹلز")) },
                leadingIcon = { Icon(Icons.Filled.Subtitles, null) },
                onClick = { menu = false; dlg = "subs" },
            )
            DropdownMenuItem(
                text = { Text(tr("Save for later", "بعد کے لیے محفوظ")) },
                leadingIcon = { Icon(Icons.Filled.Bookmark, null) },
                onClick = { menu = false; QueueManager.saveItemForLater(item.id) },
            )
            DropdownMenuItem(
                text = { Text(tr("Select", "منتخب کریں")) },
                leadingIcon = { Icon(Icons.Filled.CheckCircle, null) },
                onClick = { menu = false; onSelect() },
            )
            DropdownMenuItem(
                text = { Text(tr("Remove", "ہٹائیں"), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; QueueManager.cancel(item.id) },
            )
        }
    }

    when (dlg) {
        "rename" -> RenameDialog(item) { dlg = null }
        "subs" -> SubtitleDialog(item) { dlg = null }
    }
}

/** Opens a link in the app's own Browser tab. */
internal fun openInBrowserTab(ctx: android.content.Context, url: String) {
    requestBrowserOpen(url)
    val act = ctx.findMainActivity()
    if (act != null) act.navRequest.value = "browser"
    else Toast.makeText(ctx, tr("Could not open the Browser", "براؤزر نہیں کھل سکا"), Toast.LENGTH_SHORT).show()
}

// ------------------------------------------------------------------------------------------ rename
@Composable
internal fun RenameDialog(item: DownloadItem, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val done = item.status == Status.DONE
    var text by remember(item.id) {
        mutableStateOf(
            if (done && item.filePath.isNotBlank()) File(item.filePath).nameWithoutExtension
            else item.customName.ifBlank { item.title }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Rename file", "فائل کا نام بدلیں")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(), maxLines = 3,
                    label = { Text(tr("File name (without extension)", "فائل کا نام (ایکسٹینشن کے بغیر)")) },
                )
                Text(
                    if (done) tr("The file on your phone is renamed.", "فون میں فائل کا نام بدل جائے گا۔")
                    else tr("Used as the file name when the download is saved. Empty = the video's own title.", "ڈاؤن لوڈ محفوظ ہوتے وقت یہی نام ہو گا۔ خالی = ویڈیو کا اپنا ٹائٹل۔"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = QueueManager.rename(item.id, text)
                if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_LONG).show() else onDismiss()
            }) { Text(tr("Save", "محفوظ")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "منسوخ")) } },
    )
}

// ------------------------------------------------------------------------------------------ subtitles
@Composable
private fun RadioRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One checkbox per subtitle language. Used by the per-video dialog and by Settings. */
@Composable
internal fun SubLangChecklist(selected: Set<String>, onChange: (Set<String>) -> Unit) {
    Column {
        SUB_LANGS.forEach { (code, name) ->
            val on = code in selected
            Row(
                Modifier.fillMaxWidth().clickable { onChange(if (on) selected - code else selected + code) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Checkbox(checked = on, onCheckedChange = null)
                Text("$name ($code)", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun SubtitleDialog(item: DownloadItem, onDismiss: () -> Unit) {
    val prefs by Settings.state.collectAsState()
    var mode by remember(item.id) { mutableStateOf(if (item.subLangs.isBlank()) 0 else if (item.subLangs == "none") 1 else 2) }
    var sel by remember(item.id) {
        mutableStateOf(item.subLangs.split(',').map { it.trim() }.filter { it.isNotEmpty() && it != "none" }.toSet())
    }
    val settingsText = if (prefs.saveSubs) subLangNames(prefs.subLangs) else tr("off", "بند")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Subtitles for this video", "اس ویڈیو کے سب ٹائٹلز")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                RadioRow(mode == 0, tr("Use Settings ($settingsText)", "سیٹنگز کے مطابق ($settingsText)")) { mode = 0 }
                RadioRow(mode == 1, tr("No subtitles", "سب ٹائٹلز نہیں")) { mode = 1 }
                RadioRow(mode == 2, tr("Choose languages", "زبانیں چنیں")) { mode = 2 }
                if (mode == 2) {
                    Spacer(Modifier.size(4.dp))
                    SubLangChecklist(sel) { sel = it }
                    Text(
                        tr("Only subtitles the site really has are saved (as .srt).", "صرف وہی سب ٹائٹلز محفوظ ہوتے ہیں جو سائٹ پر موجود ہوں (.srt)۔"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = when (mode) {
                    0 -> ""
                    1 -> "none"
                    else -> sel.joinToString(",").ifEmpty { "none" }
                }
                QueueManager.setSubs(item.id, v)
                onDismiss()
            }) { Text(tr("Save", "محفوظ")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "منسوخ")) } },
    )
}

// ------------------------------------------------------------------------------------------ saved for later
@Composable
internal fun SavedLinksDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val saved by SavedLinks.items.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Saved for later (${saved.size})", "بعد کے لیے محفوظ (${saved.size})")) },
        text = {
            if (saved.isEmpty()) {
                Text(
                    tr("Nothing saved. Paste a link and tap the bookmark button to keep it here.", "کچھ محفوظ نہیں۔ لنک پیسٹ کر کے بک مارک بٹن دبائیں۔"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(saved, key = { it.url }) { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.title.ifBlank { s.url }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (s.title.isNotBlank()) {
                                    Text(
                                        Utils.hostOf(s.url), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(onClick = { copyToClipboard(ctx, "link", s.url) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.ContentCopy, tr("Copy link", "لنک کاپی کریں"), Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = { Toast.makeText(ctx, addResultMessage(QueueManager.downloadSaved(listOf(s.url))), Toast.LENGTH_SHORT).show() },
                                modifier = Modifier.size(36.dp),
                            ) { Icon(Icons.Filled.Download, tr("Download", "ڈاؤن لوڈ"), Modifier.size(20.dp)) }
                            IconButton(onClick = { SavedLinks.remove(listOf(s.url)) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Delete, tr("Remove", "ہٹائیں"), Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (saved.isNotEmpty()) {
                TextButton(onClick = {
                    val r = QueueManager.downloadSaved(saved.map { it.url })
                    Toast.makeText(ctx, addResultMessage(r), Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) { Text(tr("Download all", "سب ڈاؤن لوڈ کریں")) }
            }
        },
        dismissButton = {
            Row {
                if (saved.isNotEmpty()) {
                    TextButton(onClick = { SavedLinks.clear() }) { Text(tr("Clear", "صاف")) }
                }
                TextButton(onClick = onDismiss) { Text(tr("Close", "بند کریں")) }
            }
        },
    )
}
