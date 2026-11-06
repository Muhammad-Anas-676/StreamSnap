@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.anas.streamsnap.ui

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anas.streamsnap.data.Accents
import com.anas.streamsnap.data.DownloadItem
import com.anas.streamsnap.data.Prefs
import com.anas.streamsnap.data.QUALITY_OPTIONS
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.data.Status
import com.anas.streamsnap.data.qualityLabel
import com.anas.streamsnap.data.tr
import com.anas.streamsnap.engine.Engine
import com.anas.streamsnap.engine.QueueManager
import com.anas.streamsnap.service.Notifier
import com.anas.streamsnap.service.ScheduleReceiver
import com.anas.streamsnap.util.BackupManager
import com.anas.streamsnap.util.LogStore
import com.anas.streamsnap.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================================================================ screen
@Composable
fun SettingsScreen(activity: MainActivity) {
    val prefs by Settings.state.collectAsState()
    val items by QueueManager.items.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            tr("Settings", "سیٹنگز"),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp),
        )
        HowItWorksCard(prefs)
        DownloadModeSection(prefs)
        QualitySection(prefs)
        DownloadOptionsSection(prefs, hasScheduled = items.any { it.status == Status.SCHEDULED })
        AdvancedSection(prefs)
        AppearanceSection(prefs)
        NotificationSection(prefs)
        PrivacySection(prefs)
        StorageSection(prefs, items)
        EngineSection(prefs, activity)
        BackupSection()
        AboutSection(activity)
    }
}

// ============================================================================ small building blocks
@Composable
private fun SCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(vertical = 4.dp), content = content) }
}

@Composable
private fun ChoiceCard(selected: Boolean, icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        tween(220), label = "choice",
    )
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RowScope.StatTile(value: String, label: String) {
    Column(
        Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun fmtClock(h: Int, m: Int): String {
    val hh = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(hh, m, if (h < 12) "AM" else "PM")
}

private fun fmtSpeed(kb: Int): String = when {
    kb <= 0 -> tr("Unlimited", "لا محدود")
    kb < 1024 -> "$kb KB/s"
    else -> "%.1f MB/s".format(kb / 1024.0)
}

private fun cleanFolder(s: String): String {
    val parts = s.replace('\\', '/').replace(Regex("[:*?\"<>|]"), "_").split('/')
        .map { it.trim() }.filter { it.isNotEmpty() && it != "." && it != ".." }
    return parts.joinToString("/").ifEmpty { "StreamSnap/Download" }
}

private fun qualityHint(q: String): String = when (q) {
    "best" -> tr(
        "Highest quality that plays smoothly on phones (H.264, usually up to 1080p).",
        "فونز پر آسانی سے چلنے والی سب سے اچھی کوالٹی (H.264، عموماً 1080p تک)۔",
    )
    "2160", "1440" -> tr(
        "Very large files, saved as MKV. Some phones cannot play it smoothly. If the video is smaller, you get the best it has.",
        "بہت بڑی فائلیں، MKV میں محفوظ۔ کچھ فونز پر اچھی نہیں چلتیں۔ ویڈیو چھوٹی ہو تو اس کی بہترین کوالٹی ملے گی۔",
    )
    "1080", "720", "480", "360", "240", "144" -> tr(
        "Closest to ${q}p without going above it. If the video has nothing that low, the next higher one is used.",
        "${q}p کے سب سے قریب، اس سے اوپر نہیں۔ اگر اتنی کم نہ ہو تو اگلی اونچی کوالٹی استعمال ہوگی۔",
    )
    "audio" -> tr("Audio only, converted to MP3.", "صرف آڈیو، MP3 میں تبدیل۔")
    "m4a" -> tr("Audio only, original M4A (no conversion, best sound).", "صرف آڈیو، اصلی M4A (بغیر تبدیلی، بہترین آواز)۔")
    "smallest" -> tr("Smallest possible file. Lower quality.", "سب سے چھوٹی فائل۔ کم کوالٹی۔")
    else -> ""
}

// ============================================================================ how it works (live summary)
@Composable
private fun HowItWorksCard(prefs: Prefs) {
    val q = qualityLabel(prefs.defaultQuality)
    val auto = prefs.autoStart
    val fixed = prefs.qualityMode == "fixed"
    val text = when {
        auto && fixed -> tr("Paste a link → it downloads by itself at $q.", "لنک پیسٹ کریں → $q میں خود ڈاؤن لوڈ ہوگی۔")
        auto -> tr("Paste a link → tap a quality chip on the card → download starts.", "لنک پیسٹ کریں → کارڈ پر کوالٹی چپ دبائیں → ڈاؤن لوڈ شروع۔")
        fixed -> tr("Paste a link → press the download icon → it downloads at $q.", "لنک پیسٹ کریں → ڈاؤن لوڈ آئیکن دبائیں → $q میں ڈاؤن لوڈ۔")
        else -> tr("Paste a link → choose a quality chip → press the download icon.", "لنک پیسٹ کریں → کوالٹی چپ چنیں → ڈاؤن لوڈ آئیکن دبائیں۔")
    }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(tr("How it works now", "ابھی کیسے کام کرتا ہے"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ============================================================================ download mode
@Composable
private fun DownloadModeSection(prefs: Prefs) {
    SectionTitle(tr("Download mode", "ڈاؤن لوڈ موڈ"))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChoiceCard(
            selected = prefs.autoStart, icon = Icons.Filled.FlashOn,
            title = tr("Automatic download", "خودکار ڈاؤن لوڈ"),
            subtitle = tr(
                "Just paste the link. The download starts by itself, no button to press.",
                "بس لنک پیسٹ کریں۔ ڈاؤن لوڈ خود شروع ہو جاتی ہے، کوئی بٹن نہیں دبانا۔",
            ),
        ) {
            Settings.update { it.copy(autoStart = true) }
            QueueManager.onModeChanged()
        }
        ChoiceCard(
            selected = !prefs.autoStart, icon = Icons.Filled.TouchApp,
            title = tr("Manual download", "دستی ڈاؤن لوڈ"),
            subtitle = tr(
                "After pasting, press the download icon on the card to start.",
                "پیسٹ کرنے کے بعد شروع کرنے کے لیے کارڈ پر ڈاؤن لوڈ آئیکن دبائیں۔",
            ),
        ) {
            Settings.update { it.copy(autoStart = false) }
        }
    }
}

// ============================================================================ quality
@Composable
private fun QualitySection(prefs: Prefs) {
    SectionTitle(tr("Video quality", "ویڈیو کوالٹی"))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChoiceCard(
            selected = prefs.qualityMode == "fixed", icon = Icons.Filled.HighQuality,
            title = tr("All videos this quality", "تمام ویڈیوز اسی کوالٹی میں"),
            subtitle = tr(
                "One quality for every link. Now: ${qualityLabel(prefs.defaultQuality)}",
                "ہر لنک کے لیے ایک ہی کوالٹی۔ ابھی: ${qualityLabel(prefs.defaultQuality)}",
            ),
        ) {
            Settings.update { it.copy(qualityMode = "fixed") }
            QueueManager.applyDefaultQuality(Settings.value.defaultQuality)
            QueueManager.onModeChanged()
        }
        AnimatedVisibility(visible = prefs.qualityMode == "fixed") {
            SCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("Choose the quality", "کوالٹی چنیں"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        QUALITY_OPTIONS.forEach { (key, label) ->
                            FilterChip(
                                selected = prefs.defaultQuality == key,
                                onClick = {
                                    Settings.update { it.copy(defaultQuality = key) }
                                    QueueManager.applyDefaultQuality(key)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    Hint(qualityHint(prefs.defaultQuality))
                }
            }
        }
        ChoiceCard(
            selected = prefs.qualityMode == "ask", icon = Icons.Filled.Tune,
            title = tr("I choose for each video", "ہر ویڈیو کی کوالٹی میں خود چنوں گا"),
            subtitle = tr(
                "Quality chips show on every card. In Automatic mode, tapping a chip starts the download.",
                "ہر کارڈ پر کوالٹی چپس آتی ہیں۔ خودکار موڈ میں چپ دبانے سے ڈاؤن لوڈ شروع ہو جاتی ہے۔",
            ),
        ) {
            Settings.update { it.copy(qualityMode = "ask") }
        }
    }
}

// ============================================================================ download options
@Composable
private fun DownloadOptionsSection(prefs: Prefs, hasScheduled: Boolean) {
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf<String?>(null) }

    SectionTitle(tr("Download options", "ڈاؤن لوڈ آپشنز"))
    SCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(tr("Parallel downloads", "ایک ساتھ ڈاؤن لوڈز"), style = MaterialTheme.typography.bodyLarge)
            Hint(tr("How many videos download at the same time", "ایک وقت میں کتنی ویڈیوز ڈاؤن لوڈ ہوں"))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..3).forEach { n ->
                    FilterChip(
                        selected = prefs.parallel == n,
                        onClick = { Settings.update { it.copy(parallel = n) }; QueueManager.pump() },
                        label = { Text("$n") },
                    )
                }
            }
        }
        HorizontalDivider()
        SwitchRow(
            tr("Wi-Fi only", "صرف وائی فائی"),
            tr("Downloads wait when you are on mobile data", "موبائل ڈیٹا پر ڈاؤن لوڈز رک جاتی ہیں"),
            prefs.wifiOnly,
        ) { v ->
            Settings.update { it.copy(wifiOnly = v) }
            QueueManager.recheck()
        }
        HorizontalDivider()
        ClickRow(tr("Speed limit", "اسپیڈ لمٹ"), trailing = fmtSpeed(prefs.speedLimitKb)) { dlg = "speed" }
        HorizontalDivider()
        ClickRow(
            tr("Download folder", "ڈاؤن لوڈ فولڈر"),
            subtitle = prefs.folder,
        ) { dlg = "folder" }
        HorizontalDivider()
        ClickRow(
            tr("File name format", "فائل نام کا انداز"),
            subtitle = prefs.nameTemplate,
        ) { dlg = "name" }
        HorizontalDivider()
        SwitchRow(
            tr("Save thumbnail", "تھمب نیل محفوظ کریں"),
            tr("Also saves the cover picture as a JPG", "کور تصویر JPG میں بھی محفوظ ہوگی"),
            prefs.saveThumb,
        ) { v -> Settings.update { it.copy(saveThumb = v) } }
        HorizontalDivider()
        SwitchRow(
            tr("Save subtitles", "سب ٹائٹلز محفوظ کریں"),
            tr("English and Urdu subtitles as .srt when available", "انگریزی اور اردو سب ٹائٹلز (.srt) اگر دستیاب ہوں"),
            prefs.saveSubs,
        ) { v -> Settings.update { it.copy(saveSubs = v) } }
        HorizontalDivider()
        SwitchRow(
            tr("Scheduled download", "شیڈول ڈاؤن لوڈ"),
            tr(
                "New videos wait and start every day at ${fmtClock(prefs.scheduleHour, prefs.scheduleMinute)}",
                "نئی ویڈیوز انتظار کرتی ہیں اور روزانہ ${fmtClock(prefs.scheduleHour, prefs.scheduleMinute)} پر شروع ہوتی ہیں",
            ),
            prefs.scheduleEnabled,
        ) { v ->
            Settings.update { it.copy(scheduleEnabled = v) }
            ScheduleReceiver.arm(ctx)
            if (!v) QueueManager.releaseScheduled()
        }
        if (prefs.scheduleEnabled) {
            HorizontalDivider()
            ClickRow(
                tr("Start time", "شروع ہونے کا وقت"),
                trailing = fmtClock(prefs.scheduleHour, prefs.scheduleMinute),
            ) { dlg = "time" }
        }
        if (hasScheduled) {
            HorizontalDivider()
            ClickRow(tr("Start waiting downloads now", "رکی ہوئی ڈاؤن لوڈز ابھی شروع کریں")) {
                QueueManager.releaseScheduled()
            }
        }
    }

    when (dlg) {
        "speed" -> SpeedDialog(
            current = prefs.speedLimitKb,
            onSave = { kb -> Settings.update { it.copy(speedLimitKb = kb) }; dlg = null },
            onDismiss = { dlg = null },
        )
        "folder" -> FolderDialog(
            current = prefs.folder,
            noPermission = !Utils.hasStoragePermission(ctx),
            onSave = { f ->
                val clean = cleanFolder(f)
                Settings.update { it.copy(folder = clean) }
                try { Utils.applyNoMedia(Utils.outputDir(ctx, clean), Settings.value.hiddenFolder) } catch (_: Throwable) { }
                dlg = null
            },
            onDismiss = { dlg = null },
        )
        "name" -> NameDialog(
            current = prefs.nameTemplate,
            onSave = { t ->
                var v = t.trim().ifEmpty { "%(title).120B.%(ext)s" }
                if (!v.contains("%(ext)s")) v += ".%(ext)s"
                Settings.update { it.copy(nameTemplate = v) }
                dlg = null
            },
            onDismiss = { dlg = null },
        )
        "time" -> TimeDialog(
            hour = prefs.scheduleHour, minute = prefs.scheduleMinute,
            onSave = { h, m ->
                Settings.update { it.copy(scheduleHour = h, scheduleMinute = m) }
                ScheduleReceiver.arm(ctx)
                dlg = null
            },
            onDismiss = { dlg = null },
        )
    }
}

@Composable
private fun SpeedDialog(current: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(if (current > 0) current.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Speed limit", "اسپیڈ لمٹ")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = v, onValueChange = { s -> if (s.length <= 6 && s.all(Char::isDigit)) v = s },
                    label = { Text("KB/s") }, singleLine = true,
                    placeholder = { Text(tr("Empty = unlimited", "خالی = لا محدود")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 256, 512, 1024, 2048).forEach { kb ->
                        AssistChip(onClick = { v = if (kb == 0) "" else kb.toString() }, label = { Text(fmtSpeed(kb)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(v.toIntOrNull() ?: 0) }) { Text(tr("Save", "محفوظ کریں")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "منسوخ")) } },
    )
}

@Composable
private fun FolderDialog(current: String, noPermission: Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Download folder", "ڈاؤن لوڈ فولڈر")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = v, onValueChange = { v = it },
                    label = { Text(tr("Folder in phone storage", "فون اسٹوریج میں فولڈر")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Hint(tr("Default: StreamSnap/Download", "ڈیفالٹ: StreamSnap/Download"))
                if (noPermission) {
                    Text(
                        tr(
                            "Storage access is not allowed yet, so files are saved inside the app's own folder. Allow it under Storage below.",
                            "اسٹوریج کی اجازت نہیں ملی، اس لیے فائلیں ایپ کے اپنے فولڈر میں محفوظ ہوں گی۔ نیچے Storage میں اجازت دیں۔",
                        ),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(v) }) { Text(tr("Save", "محفوظ کریں")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "منسوخ")) } },
    )
}

private val NAME_PRESETS: List<Pair<String, Pair<String, String>>> = listOf(
    "%(title).120B.%(ext)s" to ("Title" to "عنوان"),
    "%(title).100B [%(uploader).30B].%(ext)s" to ("Title [Channel]" to "عنوان [چینل]"),
    "%(upload_date)s - %(title).100B.%(ext)s" to ("Date - Title" to "تاریخ - عنوان"),
    "%(extractor)s - %(title).100B.%(ext)s" to ("Site - Title" to "سائٹ - عنوان"),
)

@Composable
private fun NameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("File name format", "فائل نام کا انداز")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NAME_PRESETS.forEach { (tpl, names) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { v = tpl }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = v == tpl, onClick = { v = tpl })
                        Text(tr(names.first, names.second))
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = v, onValueChange = { v = it },
                    label = { Text(tr("Custom (yt-dlp template)", "کسٹم (yt-dlp ٹیمپلیٹ)")) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(v) }) { Text(tr("Save", "محفوظ کریں")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "منسوخ")) } },
    )
}

@Composable
private fun TimeDialog(hour: Int, minute: Int, onSave: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val st = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.width(IntrinsicSize.Min).height(IntrinsicSize.Min),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    tr("Start time", "شروع ہونے کا وقت"),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
                TimePicker(state = st)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(tr("Cancel", "منسوخ")) }
                    TextButton(onClick = { onSave(st.hour, st.minute) }) { Text("OK") }
                }
            }
        }
    }
}

// ============================================================================ advanced (proxy, cookies)
@Composable
private fun AdvancedSection(prefs: Prefs) {
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf(false) }
    var cookieTick by remember { mutableIntStateOf(0) }
    val hasCookies = remember(cookieTick) { Engine.cookieFile(ctx).let { it.exists() && it.length() > 0 } }

    val cookieLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    Engine.cookieFile(ctx).outputStream().use { out -> input.copyTo(out) }
                }
                Settings.update { it.copy(useCookies = true) }
                cookieTick++
                LogStore.i("settings", "Cookies file imported")
                Toast.makeText(ctx, tr("Cookies imported", "کوکیز امپورٹ ہو گئیں"), Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                LogStore.e("settings", "Could not import the cookies file", t = t)
                Toast.makeText(ctx, tr("Could not import cookies (see Log)", "کوکیز امپورٹ نہیں ہوئیں (لاگ دیکھیں)"), Toast.LENGTH_LONG).show()
            }
        }
    }

    SectionTitle(tr("Advanced", "ایڈوانسڈ"))
    SCard {
        ClickRow(
            tr("Proxy", "پراکسی"),
            subtitle = tr("For sites blocked in your network", "ان سائٹس کے لیے جو آپ کے نیٹ ورک میں بند ہیں"),
            trailing = if (prefs.proxy.isBlank()) tr("Off", "بند") else tr("On", "چالو"),
        ) { dlg = true }
        HorizontalDivider()
        SwitchRow(
            tr("Use cookies (login)", "کوکیز استعمال کریں (لاگ اِن)"),
            if (hasCookies) tr("Cookies file is imported", "کوکیز فائل امپورٹ ہے") else tr("Import a cookies.txt first", "پہلے cookies.txt امپورٹ کریں"),
            prefs.useCookies && hasCookies,
        ) { v ->
            if (v && !hasCookies) cookieLauncher.launch(arrayOf("text/plain", "*/*"))
            else Settings.update { it.copy(useCookies = v) }
        }
        HorizontalDivider()
        ClickRow(tr("Import cookies.txt", "cookies.txt امپورٹ کریں")) { cookieLauncher.launch(arrayOf("text/plain", "*/*")) }
        if (hasCookies) {
            HorizontalDivider()
            ClickRow(tr("Remove cookies", "کوکیز ہٹائیں")) {
                try { Engine.cookieFile(ctx).delete() } catch (_: Throwable) { }
                Settings.update { it.copy(useCookies = false) }
                cookieTick++
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Hint(tr("Cookies stay on this phone and are never shown in the Log.", "کوکیز اسی فون میں رہتی ہیں اور لاگ میں کبھی نظر نہیں آتیں۔"))

    if (dlg) {
        var v by remember { mutableStateOf(prefs.proxy) }
        AlertDialog(
            onDismissRequest = { dlg = false },
            title = { Text(tr("Proxy", "پراکسی")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = v, onValueChange = { v = it }, singleLine = true,
                        label = { Text("http://host:port  /  socks5://host:port") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Hint(tr("Leave empty for no proxy.", "پراکسی نہ چاہیے تو خالی چھوڑیں۔"))
                }
            },
            confirmButton = {
                TextButton(onClick = { Settings.update { it.copy(proxy = v.trim()) }; dlg = false }) { Text(tr("Save", "محفوظ کریں")) }
            },
            dismissButton = { TextButton(onClick = { dlg = false }) { Text(tr("Cancel", "منسوخ")) } },
        )
    }
}

// ============================================================================ appearance
@Composable
private fun AppearanceSection(prefs: Prefs) {
    SectionTitle(tr("Appearance", "ظاہری شکل"))
    SCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Theme", "تھیم"), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to tr("System", "سسٹم"), 1 to tr("Light", "لائٹ"), 2 to tr("Dark", "ڈارک")).forEach { (mode, label) ->
                    FilterChip(
                        selected = prefs.themeMode == mode,
                        onClick = { Settings.update { it.copy(themeMode = mode) } },
                        label = { Text(label) },
                    )
                }
            }
        }
        HorizontalDivider()
        SwitchRow(
            tr("AMOLED black", "AMOLED بلیک"),
            tr("Pure black in dark theme", "ڈارک تھیم میں خالص کالا"),
            prefs.amoled,
        ) { v -> Settings.update { it.copy(amoled = v) } }
        HorizontalDivider()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Accent color", "اکسینٹ رنگ"), style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Accents.list.forEachIndexed { i, pair ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(pair.second))
                            .clickable { Settings.update { it.copy(accent = i) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (prefs.accent == i) Icon(Icons.Filled.Check, null, tint = Color.White)
                    }
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.sweepGradient(listOf(Color(0xFF1C68FA), Color(0xFFE91E63), Color(0xFFF5A623), Color(0xFF1FA971), Color(0xFF1C68FA))))
                            .clickable { Settings.update { it.copy(accent = Accents.DYNAMIC) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (prefs.accent == Accents.DYNAMIC) Icons.Filled.Check else Icons.Filled.AutoAwesome,
                            null, tint = Color.White,
                        )
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 31) Hint(tr("The rainbow circle uses your wallpaper colors.", "رنگین دائرہ آپ کے وال پیپر کے رنگ استعمال کرتا ہے۔"))
        }
        HorizontalDivider()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Language", "زبان"), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = prefs.language == "en",
                    onClick = { Settings.update { it.copy(language = "en") } },
                    label = { Text("English") },
                )
                FilterChip(
                    selected = prefs.language == "ur",
                    onClick = { Settings.update { it.copy(language = "ur") } },
                    label = { Text("اردو") },
                )
            }
        }
    }
}

// ============================================================================ notifications
@Composable
private fun NotificationSection(prefs: Prefs) {
    val ctx = LocalContext.current
    SectionTitle(tr("Notifications", "نوٹیفیکیشنز"))
    SCard {
        SwitchRow(tr("Live progress", "لائیو پیش رفت"), tr("Silent bar with Pause and Cancel buttons", "خاموش بار، Pause اور Cancel بٹنوں کے ساتھ"), prefs.nProgress) { v ->
            Settings.update { it.copy(nProgress = v) }
        }
        HorizontalDivider()
        SwitchRow(tr("Download completed", "ڈاؤن لوڈ مکمل"), tr("Tap to play", "چلانے کے لیے ٹیپ کریں"), prefs.nDone) { v ->
            Settings.update { it.copy(nDone = v) }
        }
        HorizontalDivider()
        SwitchRow(tr("Download failed", "ڈاؤن لوڈ ناکام"), tr("With a Retry button", "Retry بٹن کے ساتھ"), prefs.nFailed) { v ->
            Settings.update { it.copy(nFailed = v) }
        }
        HorizontalDivider()
        SwitchRow(tr("All done summary", "سب مکمل کا خلاصہ"), null, prefs.nSummary) { v ->
            Settings.update { it.copy(nSummary = v) }
        }
        HorizontalDivider()
        SwitchRow(tr("Network lost", "نیٹ ورک ختم"), tr("When internet or Wi-Fi is gone", "جب انٹرنیٹ یا وائی فائی چلا جائے"), prefs.nNetwork) { v ->
            Settings.update { it.copy(nNetwork = v) }
        }
        HorizontalDivider()
        SwitchRow(tr("Low storage", "اسٹوریج کم"), null, prefs.nStorage) { v ->
            Settings.update { it.copy(nStorage = v) }
        }
        HorizontalDivider()
        SwitchRow(tr("Engine update", "انجن اپڈیٹ"), null, prefs.nEngine) { v ->
            Settings.update { it.copy(nEngine = v) }
        }
        HorizontalDivider()
        SwitchRow(tr("Clipboard link", "کلپ بورڈ لنک"), tr("Add button when you copied a link", "لنک کاپی کرنے پر Add بٹن"), prefs.nClipboard) { v ->
            Settings.update { it.copy(nClipboard = v) }
        }
        HorizontalDivider()
        SwitchRow(tr("Sound", "آواز"), tr("Completed, failed and alerts", "مکمل، ناکام اور الرٹس"), prefs.notifSound) { v ->
            Settings.update { it.copy(notifSound = v) }
            Notifier.ensureChannels(ctx)
        }
        HorizontalDivider()
        SwitchRow(tr("Vibrate", "وائبریشن"), null, prefs.notifVibrate) { v ->
            Settings.update { it.copy(notifVibrate = v) }
            Notifier.ensureChannels(ctx)
        }
        HorizontalDivider()
        ClickRow(tr("System notification settings", "سسٹم نوٹیفیکیشن سیٹنگز")) { openNotificationSettings(ctx) }
    }
}

// ============================================================================ privacy
@Composable
private fun PrivacySection(prefs: Prefs) {
    val ctx = LocalContext.current
    var pinDialog by remember { mutableStateOf(false) }

    SectionTitle(tr("Privacy", "پرائیویسی"))
    SCard {
        SwitchRow(
            tr("App lock (PIN)", "ایپ لاک (PIN)"),
            tr("Ask for a 4 digit PIN when the app opens", "ایپ کھلتے وقت 4 ہندسوں کا PIN پوچھے"),
            prefs.appLock,
        ) { v ->
            if (v) pinDialog = true else Settings.disableLock()
        }
        if (prefs.appLock) {
            if (canUseBiometric(ctx)) {
                HorizontalDivider()
                SwitchRow(tr("Unlock with fingerprint", "فنگر پرنٹ سے کھولیں"), null, prefs.lockBiometric) { v ->
                    Settings.update { it.copy(lockBiometric = v) }
                }
            }
            HorizontalDivider()
            ClickRow(tr("Change PIN", "PIN تبدیل کریں")) { pinDialog = true }
        }
        HorizontalDivider()
        SwitchRow(
            tr("Block screenshots", "اسکرین شاٹ بلاک"),
            tr("Also hides the app in the recent apps screen", "حالیہ ایپس میں بھی ایپ چھپی رہتی ہے"),
            prefs.blockScreenshots,
        ) { v -> Settings.update { it.copy(blockScreenshots = v) } }
        HorizontalDivider()
        SwitchRow(
            tr("Hidden folder", "چھپا ہوا فولڈر"),
            tr("Videos will not show in Gallery or other apps (.nomedia)", "ویڈیوز گیلری اور دوسری ایپس میں نظر نہیں آئیں گی (.nomedia)"),
            prefs.hiddenFolder,
        ) { v ->
            Settings.update { it.copy(hiddenFolder = v) }
            try { Utils.applyNoMedia(Utils.outputDir(ctx, Settings.value.folder), v) } catch (_: Throwable) { }
        }
        HorizontalDivider()
        SwitchRow(
            tr("Auto-clear history on exit", "باہر نکلتے وقت ہسٹری صاف"),
            tr("Files stay on your phone, only the list is cleared", "فائلیں فون میں رہتی ہیں، صرف فہرست صاف ہوتی ہے"),
            prefs.autoClearHistory,
        ) { v -> Settings.update { it.copy(autoClearHistory = v) } }
    }

    if (pinDialog) {
        PinSetupDialog(
            onDone = { pin -> Settings.setPin(pin); pinDialog = false },
            onDismiss = { pinDialog = false },
        )
    }
}

// ============================================================================ storage and stats
@Composable
private fun StorageSection(prefs: Prefs, items: List<DownloadItem>) {
    val ctx = LocalContext.current
    val done = remember(items) { items.filter { it.status == Status.DONE } }
    val used = done.sumOf { it.fileSize }
    val audioCount = done.count { it.isAudio }
    val space = remember(prefs.folder, done.size) {
        val d = Utils.outputDir(ctx, prefs.folder)
        Utils.freeBytes(d) to Utils.totalBytes(d)
    }
    val free = space.first
    val total = space.second

    SectionTitle(tr("Storage and stats", "اسٹوریج اور اعداد و شمار"))
    SCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("${done.size}", tr("Downloads", "ڈاؤن لوڈز"))
            StatTile("${done.size - audioCount} / $audioCount", tr("Video / Audio", "ویڈیو / آڈیو"))
            StatTile(Utils.fmtBytes(used), tr("Used by app", "ایپ کا استعمال"))
        }
        if (total > 0 && free != Long.MAX_VALUE) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { ((total - free).toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                )
                Hint(
                    tr(
                        "${Utils.fmtBytes(free)} free of ${Utils.fmtBytes(total)}",
                        "${Utils.fmtBytes(total)} میں سے ${Utils.fmtBytes(free)} خالی",
                    )
                )
            }
        }
        HorizontalDivider()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Auto-delete old downloads", "پرانی ڈاؤن لوڈز خود حذف کریں"), style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 7, 30, 90).forEach { d ->
                    FilterChip(
                        selected = prefs.autoDeleteDays == d,
                        onClick = { Settings.update { it.copy(autoDeleteDays = d) } },
                        label = { Text(if (d == 0) tr("Never", "کبھی نہیں") else tr("$d days", "$d دن")) },
                    )
                }
            }
            Hint(tr("Checked each time the app starts. The files are deleted from your phone.", "ہر بار ایپ شروع ہونے پر چیک ہوتا ہے۔ فائلیں فون سے حذف ہو جاتی ہیں۔"))
        }
        HorizontalDivider()
        Column(Modifier.padding(16.dp)) { PermissionsBlock() }
    }
}

// ============================================================================ engine
@Composable
private fun EngineSection(prefs: Prefs, activity: MainActivity) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Engine.state.collectAsState()
    var version by remember { mutableStateOf(LogStore.engineVersion) }
    var busy by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(state) { version = LogStore.engineVersion }

    SectionTitle(tr("Download engine", "ڈاؤن لوڈ انجن"))
    SCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("yt-dlp  $version", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Hint(
                when (state) {
                    "ready" -> tr("Engine is ready", "انجن تیار ہے")
                    "error" -> tr("Engine could not start. Open the Log for the exact error.", "انجن شروع نہیں ہوا۔ درست ایرر کے لیے لاگ کھولیں۔")
                    else -> tr("Engine is starting…", "انجن شروع ہو رہا ہے…")
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = prefs.engineChannel == "stable",
                    onClick = { Settings.update { it.copy(engineChannel = "stable") } },
                    label = { Text(tr("Stable", "اسٹیبل")) },
                )
                FilterChip(
                    selected = prefs.engineChannel == "nightly",
                    onClick = { Settings.update { it.copy(engineChannel = "nightly") } },
                    label = { Text(tr("Nightly", "نائٹلی")) },
                )
            }
            Hint(tr("Nightly gets the newest site fixes first but can be less stable.", "نائٹلی میں سائٹس کی نئی فکسز پہلے آتی ہیں مگر کم مستحکم ہو سکتی ہے۔"))
        }
        HorizontalDivider()
        ClickRow(
            tr("Update engine", "انجن اپڈیٹ کریں"),
            subtitle = if (busy) tr("Updating… please wait", "اپڈیٹ ہو رہا ہے… انتظار کریں") else tr("Fixes sites that stopped working", "وہ سائٹس ٹھیک کرتا ہے جو چلنا بند ہو گئی ہوں"),
        ) {
            if (!busy) {
                busy = true
                scope.launch {
                    val channel = Settings.value.engineChannel
                    val res = withContext(Dispatchers.IO) {
                        try {
                            Result.success(Engine.update(ctx, channel))
                        } catch (t: Throwable) {
                            LogStore.e("engine", "Engine update failed", t = t)
                            Result.failure<String>(t)
                        }
                    }
                    busy = false
                    version = LogStore.engineVersion
                    Settings.update { it.copy(lastEngineCheck = System.currentTimeMillis()) }
                    res.fold(
                        onSuccess = { msg ->
                            LogStore.i("engine", "Engine update: $msg")
                            Toast.makeText(ctx, msg.take(120), Toast.LENGTH_LONG).show()
                        },
                        onFailure = { t ->
                            Toast.makeText(
                                ctx,
                                tr("Update failed (see Log): ", "اپڈیٹ ناکام (لاگ دیکھیں): ") + ((t.cause ?: t).message ?: "").take(100),
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                    )
                }
            }
        }
        HorizontalDivider()
        ClickRow(
            tr("Reset engine", "انجن ری سیٹ کریں"),
            subtitle = tr("Use this if downloads fail right after an update", "اپڈیٹ کے بعد ڈاؤن لوڈز ناکام ہوں تو یہ استعمال کریں"),
        ) { confirmReset = true }
        HorizontalDivider()
        ClickRow(tr("Open Activity Log", "ایکٹیویٹی لاگ کھولیں"), subtitle = tr("Exact errors you can copy", "درست ایرر جنہیں کاپی کیا جا سکتا ہے")) {
            activity.navRequest.value = "logs"
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(tr("Reset engine?", "انجن ری سیٹ کریں؟")) },
            text = {
                Text(
                    tr(
                        "The engine files are removed and extracted fresh. The app closes now; open it again. Running downloads pause and continue later.",
                        "انجن کی فائلیں ہٹ کر نئی بنیں گی۔ ایپ ابھی بند ہوگی، دوبارہ کھولیں۔ چلتی ڈاؤن لوڈز رک کر بعد میں جاری ہوں گی۔",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    QueueManager.saveNow()
                    val ok = Engine.resetFiles(ctx)
                    LogStore.w("engine", "Engine files reset by the user (success=$ok)")
                    Toast.makeText(ctx, tr("Engine reset. Open the app again.", "انجن ری سیٹ ہو گیا۔ ایپ دوبارہ کھولیں۔"), Toast.LENGTH_LONG).show()
                    scope.launch {
                        delay(1200)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }) { Text(tr("Reset", "ری سیٹ")) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(tr("Cancel", "منسوخ")) } },
        )
    }
}

// ============================================================================ backup
@Composable
private fun BackupSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) { BackupManager.backup(ctx, uri) }
            Toast.makeText(
                ctx,
                if (r.isSuccess) tr("Backup saved", "بیک اپ محفوظ ہو گیا") else tr("Backup failed (see Log)", "بیک اپ ناکام (لاگ دیکھیں)"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) { BackupManager.restore(ctx, uri) }
            Toast.makeText(
                ctx,
                if (r.isSuccess) tr("Backup restored", "بیک اپ بحال ہو گیا")
                else tr("Restore failed: ", "بحالی ناکام: ") + ((r.exceptionOrNull()?.message) ?: "").take(100),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    SectionTitle(tr("Backup and restore", "بیک اپ اور بحالی"))
    SCard {
        ClickRow(
            tr("Backup settings and history", "سیٹنگز اور ہسٹری کا بیک اپ"),
            subtitle = tr("Saves one small file. PIN and cookies are not included.", "ایک چھوٹی فائل محفوظ ہوتی ہے۔ PIN اور کوکیز شامل نہیں۔"),
        ) { backupLauncher.launch("streamsnap-backup.json") }
        HorizontalDivider()
        ClickRow(tr("Restore from backup", "بیک اپ سے بحال کریں")) {
            restoreLauncher.launch(arrayOf("application/json", "*/*"))
        }
    }
}

// ============================================================================ about
@Suppress("DEPRECATION")
private fun appVersion(ctx: Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
} catch (_: Throwable) { "?" }

@Composable
private fun AboutSection(activity: MainActivity) {
    SectionTitle(tr("About", "ایپ کے بارے میں"))
    SCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("StreamSnap  v${appVersion(activity)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Capture any stream, instantly.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Hint(
                tr(
                    "For personal use. Please download only content you have the right to save.",
                    "ذاتی استعمال کے لیے۔ صرف وہی مواد ڈاؤن لوڈ کریں جسے محفوظ کرنے کا آپ کو حق ہے۔",
                )
            )
            Spacer(Modifier.height(6.dp))
            Text("Made by Anas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}
