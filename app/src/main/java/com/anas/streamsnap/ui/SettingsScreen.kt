@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.anas.streamsnap.ui

import com.anas.streamsnap.data.tr3
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.IconButton
import com.anas.streamsnap.core.ui.ThemeAppearanceCard
import com.anas.streamsnap.core.ui.MadeByAnasSignature
import com.anas.streamsnap.core.legal.LegalDocs
import com.anas.streamsnap.core.legal.LegalDocRow
import com.anas.streamsnap.core.legal.LegalReader
import com.anas.streamsnap.core.cloud.ScreenTracker

import android.content.Context
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anas.streamsnap.data.DownloadItem
import com.anas.streamsnap.data.Prefs
import com.anas.streamsnap.data.QUALITY_OPTIONS
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.data.Status
import com.anas.streamsnap.data.qualityLabel
import com.anas.streamsnap.data.subLangNames
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
fun SettingsScreen(activity: MainActivity, onBack: () -> Unit) {
    val prefs by Settings.state.collectAsState()
    val items by QueueManager.items.collectAsState()
    var legalOpen by rememberSaveable { mutableStateOf<String?>(null) }

    // Screen time for the admin: Settings, or Legal while a document is open.
    LaunchedEffect(legalOpen) { ScreenTracker.enter(if (legalOpen != null) "Legal" else "Settings") }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr3("Back", "Wapas", "واپس"),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                tr("Settings", "سیٹنگز"),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            AppearanceSection(prefs)
            HowItWorksCard(prefs)
            DownloadModeSection(prefs)
            QualitySection(prefs)
            DownloadOptionsSection(prefs, hasScheduled = items.any { it.status == Status.SCHEDULED })
            NotificationSection(prefs)
            PrivacySection(prefs)
            StorageSection(prefs, items)
            BackupSection()
            LegalAboutSection(activity) { legalOpen = it }
        }
    }
    legalOpen?.let { LegalReader(it, onClose = { legalOpen = null }) }
}

/** A row with a title and an expand / collapse arrow. */
@Composable
private fun ExpandHeader(title: String, subtitle: String?, isOpen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Hint(subtitle)
        }
        Icon(
            if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (isOpen) tr3("Collapse", "Band karein", "بند کریں") else tr3("Expand", "Kholein", "کھولیں"),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            selected = prefs.qualityMode == "fixed" && !prefs.cvOn, icon = Icons.Filled.HighQuality,
            title = tr("All videos this quality", "تمام ویڈیوز اسی کوالٹی میں"),
            subtitle = tr(
                "One quality for every link. Now: ${qualityLabel(prefs.defaultQuality)}",
                "ہر لنک کے لیے ایک ہی کوالٹی۔ ابھی: ${qualityLabel(prefs.defaultQuality)}",
            ),
        ) {
            Settings.update { it.copy(qualityMode = "fixed", cvOn = false) }
            QueueManager.applyDefaultQuality(Settings.value.defaultQuality)
            QueueManager.onModeChanged()
        }
        AnimatedVisibility(visible = prefs.qualityMode == "fixed" && !prefs.cvOn) {
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
            selected = prefs.qualityMode == "ask" && !prefs.cvOn, icon = Icons.Filled.Tune,
            title = tr("I choose for each video", "ہر ویڈیو کی کوالٹی میں خود چنوں گا"),
            subtitle = tr(
                "Quality chips show on every card. In Automatic mode, tapping a chip starts the download.",
                "ہر کارڈ پر کوالٹی چپس آتی ہیں۔ خودکار موڈ میں چپ دبانے سے ڈاؤن لوڈ شروع ہو جاتی ہے۔",
            ),
        ) {
            Settings.update { it.copy(qualityMode = "ask", cvOn = false) }
        }
        ChoiceCard(
            selected = prefs.cvOn, icon = Icons.Filled.VideoSettings,
            title = tr3("Custom video quality", "Custom video quality", "کسٹم ویڈیو کوالٹی"),
            subtitle = tr(
                "Every video is saved with the settings below",
                "ہر ویڈیو نیچے دی گئی سیٹنگز کے مطابق محفوظ ہوگی",
            ),
        ) {
            Settings.update { it.copy(qualityMode = "fixed", cvOn = true) }
            QueueManager.applyDefaultQuality("best")
            QueueManager.onModeChanged()
        }
        CustomVideoCard(prefs)
    }
}

// ---------------------------------------------------------------- custom video settings
private val CV_RES = listOf(0, 144, 240, 360, 480, 720, 1080)
private val CV_FPS = listOf(0, 15, 24, 25, 30, 50, 60)

private fun cvResLabel(v: Int) = if (v <= 0) tr("Original", "اصل") else "${v}P"
private fun cvFpsLabel(v: Int) = if (v <= 0) tr("Original", "اصل") else "$v"
private fun cvVCodecLabel(v: String) = when (v) { "h264" -> "H.264"; "h265" -> "H.265 (HEVC)"; else -> "MPEG4" }
private fun cvACodecLabel(v: String) = if (v == "mp3") "MP3" else "AAC"
private fun cvQualityLabel(v: Int) = when (v) {
    1 -> tr("1 (Best, biggest file)", "1 (بہترین، بڑی فائل)")
    5 -> tr("5 (Smallest file)", "5 (سب سے چھوٹی فائل)")
    else -> "$v"
}
private fun cvFlipLabel(v: String) = when (v) {
    "h" -> tr("Horizontal", "افقی")
    "v" -> tr("Vertical", "عمودی")
    else -> tr("No flip", "کوئی فلپ نہیں")
}

@Composable
private fun CustomVideoCard(prefs: Prefs) {
    var dlg by remember { mutableStateOf<String?>(null) }
    AnimatedVisibility(visible = prefs.cvOn) {
      SCard {
            Column {
                ClickRow(tr("Resolution", "ریزولوشن"), trailing = cvResLabel(prefs.cvRes)) { dlg = "res" }
                ClickRow(tr("Frame rate", "فریم ریٹ"), trailing = cvFpsLabel(prefs.cvFps)) { dlg = "fps" }
                ClickRow(tr("Video codec", "ویڈیو کوڈیک"), trailing = cvVCodecLabel(prefs.cvVCodec)) { dlg = "vcodec" }
                ClickRow(tr("Audio codec", "آڈیو کوڈیک"), trailing = cvACodecLabel(prefs.cvACodec)) { dlg = "acodec" }
                ClickRow(tr("Video quality", "ویڈیو کوالٹی"), trailing = "${prefs.cvQuality}") { dlg = "quality" }
                ClickRow(tr("Video rotation", "ویڈیو روٹیشن"), trailing = "${prefs.cvRotate}°") { dlg = "rotate" }
                ClickRow(tr("Video flip", "ویڈیو فلپ"), trailing = cvFlipLabel(prefs.cvFlip)) { dlg = "flip" }
                SwitchRow(
                    tr("Compress", "کمپریس"),
                    tr("Smaller file: lower quality and lower audio bitrate", "چھوٹی فائل: کم کوالٹی اور کم آڈیو بٹ ریٹ"),
                    prefs.cvCompress,
                ) { v -> Settings.update { it.copy(cvCompress = v) } }
                HorizontalDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Hint(
                        tr(
                            "Applies to video downloads only; MP3 and M4A are not changed. The video is converted on your phone right after it downloads, so it takes extra time. The resolution here replaces the quality chosen above. If a setting fails, the video is kept as downloaded.",
                            "صرف ویڈیو ڈاؤن لوڈز پر لاگو ہوتی ہے؛ MP3 اور M4A میں تبدیلی نہیں ہوتی۔ ڈاؤن لوڈ کے فوراً بعد ویڈیو آپ کے فون پر کنورٹ ہوتی ہے، اس لیے زیادہ وقت لگتا ہے۔ یہاں کی ریزولوشن اوپر والی کوالٹی کی جگہ لے لیتی ہے۔ اگر کوئی سیٹنگ ناکام ہو تو ویڈیو جیسی ڈاؤن لوڈ ہوئی ویسی رہتی ہے اور لاگ میں وجہ دکھتی ہے۔",
                        ),
                    )
                    Hint(
                        tr(
                            "If this phone's engine has no H.264 / H.265 encoder, MPEG4 is used instead.",
                            "اگر اس فون کے انجن میں H.264 / H.265 انکوڈر نہ ہو تو MPEG4 استعمال ہوگا۔",
                        ),
                    )
                    TextButton(
                        onClick = {
                            val d = Prefs()
                            Settings.update {
                                it.copy(
                                    cvRes = d.cvRes, cvFps = d.cvFps, cvVCodec = d.cvVCodec, cvACodec = d.cvACodec,
                                    cvQuality = d.cvQuality, cvRotate = d.cvRotate, cvFlip = d.cvFlip, cvCompress = d.cvCompress,
                                )
                            }
                        },
                    ) { Text(tr("Reset to default (240P, 24 fps, MPEG4, AAC)", "ڈیفالٹ پر واپس (240P، 24 fps، MPEG4، AAC)")) }
                }
            }
        }
    }
    when (dlg) {
        "res" -> PickDialog(
            tr("Resolution", "ریزولوشن"), CV_RES.map { it to cvResLabel(it) }, prefs.cvRes,
            onPick = { v -> Settings.update { it.copy(cvRes = v) }; dlg = null }, onDismiss = { dlg = null },
        )
        "fps" -> PickDialog(
            tr("Frame rate", "فریم ریٹ"), CV_FPS.map { it to cvFpsLabel(it) }, prefs.cvFps,
            onPick = { v -> Settings.update { it.copy(cvFps = v) }; dlg = null }, onDismiss = { dlg = null },
        )
        "vcodec" -> PickDialog(
            tr("Video codec", "ویڈیو کوڈیک"), listOf("mpeg4", "h264", "h265").map { it to cvVCodecLabel(it) }, prefs.cvVCodec,
            onPick = { v -> Settings.update { it.copy(cvVCodec = v) }; dlg = null }, onDismiss = { dlg = null },
        )
        "acodec" -> PickDialog(
            tr("Audio codec", "آڈیو کوڈیک"), listOf("aac", "mp3").map { it to cvACodecLabel(it) }, prefs.cvACodec,
            onPick = { v -> Settings.update { it.copy(cvACodec = v) }; dlg = null }, onDismiss = { dlg = null },
        )
        "quality" -> PickDialog(
            tr("Video quality", "ویڈیو کوالٹی"), (1..5).map { it to cvQualityLabel(it) }, prefs.cvQuality,
            onPick = { v -> Settings.update { it.copy(cvQuality = v) }; dlg = null }, onDismiss = { dlg = null },
        )
        "rotate" -> PickDialog(
            tr("Video rotation", "ویڈیو روٹیشن"), listOf(0, 90, 180, 270).map { it to "$it°" }, prefs.cvRotate,
            onPick = { v -> Settings.update { it.copy(cvRotate = v) }; dlg = null }, onDismiss = { dlg = null },
        )
        "flip" -> PickDialog(
            tr("Video flip", "ویڈیو فلپ"), listOf("none", "h", "v").map { it to cvFlipLabel(it) }, prefs.cvFlip,
            onPick = { v -> Settings.update { it.copy(cvFlip = v) }; dlg = null }, onDismiss = { dlg = null },
        )
    }
}

@Composable
private fun <T> PickDialog(title: String, options: List<Pair<T, String>>, current: T, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(value) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == current, onClick = { onPick(value) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "منسوخ")) } },
    )
}

// ============================================================================ download options
@Composable
private fun DownloadOptionsSection(prefs: Prefs, hasScheduled: Boolean) {
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf<String?>(null) }

    var parOpen by rememberSaveable { mutableStateOf(false) }
    SectionTitle(tr("Download options", "ڈاؤن لوڈ آپشنز"))
    SCard {
        ExpandHeader(
            title = tr("Parallel downloads", "ایک ساتھ ڈاؤن لوڈز"),
            subtitle = tr3("${prefs.parallel} at the same time", "${prefs.parallel} ek saath", "${prefs.parallel} ایک ساتھ"),
            isOpen = parOpen,
        ) { parOpen = !parOpen }
        AnimatedVisibility(visible = parOpen) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                Hint(
                    tr(
                        "How many videos download at the same time (1 to 10). Many at once can slow a phone and some sites refuse too many requests.",
                        "ایک وقت میں کتنی ویڈیوز ڈاؤن لوڈ ہوں (1 سے 10)۔ بہت زیادہ ہوں تو فون سست ہو سکتا ہے اور کچھ سائٹس بہت زیادہ ریکویسٹس رد کر دیتی ہیں۔",
                    ),
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    (1..10).forEach { n ->
                        FilterChip(
                            selected = prefs.parallel == n,
                            onClick = { Settings.update { it.copy(parallel = n) }; QueueManager.pump() },
                            label = { Text("$n") },
                        )
                    }
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
        SwitchRow(
            tr("Auto-retry failed downloads", "ناکام ڈاؤن لوڈ خود دوبارہ"),
            tr(
                "A failed download tries again by itself after a short wait. Private, removed or login-only videos are not retried.",
                "ناکام ڈاؤن لوڈ تھوڑے انتظار کے بعد خود دوبارہ کوشش کرتی ہے۔ پرائیویٹ، ہٹائی گئی یا لاگ اِن والی ویڈیوز دوبارہ نہیں آزمائی جاتیں۔",
            ),
            prefs.autoRetry,
        ) { v -> Settings.update { it.copy(autoRetry = v) } }
        if (prefs.autoRetry) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(tr("Retries per video", "ہر ویڈیو کی کوششیں"), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..4).forEach { n ->
                        FilterChip(
                            selected = prefs.autoRetryMax == n,
                            onClick = { Settings.update { it.copy(autoRetryMax = n) } },
                            label = { Text("$n") },
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        SwitchRow(
            tr("Playlist checklist", "پلے لسٹ چیک لسٹ"),
            tr(
                "When you paste a playlist, choose which videos to download. Off: all videos (up to 20) are added.",
                "پلے لسٹ پیسٹ کرنے پر چنیں کون سی ویڈیوز ڈاؤن لوڈ ہوں۔ آف: تمام ویڈیوز (20 تک) شامل ہو جاتی ہیں۔",
            ),
            prefs.playlistAsk,
        ) { v -> Settings.update { it.copy(playlistAsk = v) } }
        HorizontalDivider()
        SwitchRow(
            tr("YouTube fast mode", "یوٹیوب فاسٹ موڈ"),
            tr(
                "On: YouTube links use the quick route (about a minute, qualities up to 1080p). Off: slow (several minutes on this phone) but the full quality list. If the quick route fails, the slow one is used automatically.",
                "آن: یوٹیوب لنک تیز راستے سے چلتے ہیں (تقریباً ایک منٹ، 1080p تک کوالٹی)۔ آف: سست (کئی منٹ) مگر مکمل کوالٹی لسٹ۔ تیز راستہ ناکام ہو تو سست راستہ خود چلتا ہے۔",
            ),
            prefs.ytFast,
        ) { v -> Settings.update { it.copy(ytFast = v) } }
        HorizontalDivider()
        ClickRow(tr("Speed limit", "اسپیڈ لمٹ"), trailing = fmtSpeed(prefs.speedLimitKb)) { dlg = "speed" }
        HorizontalDivider()
        ClickRow(
            tr("Download folder", "ڈاؤن لوڈ فولڈر"),
            subtitle = prefs.folder,
        ) { dlg = "folder" }
        HorizontalDivider()
        SwitchRow(
            tr("Site-wise folders", "سائٹ کے مطابق فولڈرز"),
            tr(
                "Each site gets its own folder inside the download folder (YouTube, Instagram ...)",
                "ہر سائٹ کا ڈاؤن لوڈ فولڈر کے اندر الگ فولڈر بنتا ہے (YouTube، Instagram ...)",
            ),
            prefs.siteFolders,
        ) { v -> Settings.update { it.copy(siteFolders = v) } }
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
            tr("Saved as .srt when the site has them. Pick the languages below.", "سائٹ پر ہوں تو .srt میں محفوظ ہوں گے۔ زبانیں نیچے چنیں۔"),
            prefs.saveSubs,
        ) { v -> Settings.update { it.copy(saveSubs = v) } }
        HorizontalDivider()
        ClickRow(
            tr("Subtitle languages", "سب ٹائٹل زبانیں"),
            subtitle = subLangNames(prefs.subLangs).ifBlank { tr("none", "کوئی نہیں") },
        ) { dlg = "subs" }
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
        "subs" -> SubLangsDialog(
            current = prefs.subLangs,
            onSave = { v -> Settings.update { it.copy(subLangs = v) }; dlg = null },
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
private fun SubLangsDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var sel by remember { mutableStateOf(current.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Subtitle languages", "سب ٹائٹل زبانیں")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SubLangChecklist(sel) { sel = it }
                Text(
                    tr("A single video can use other languages from its three-dot menu.", "کسی ایک ویڈیو کے لیے تین نقطوں والے مینو سے دوسری زبانیں چن سکتے ہیں۔"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(sel.joinToString(",").ifEmpty { "en" }) }) { Text(tr("Save", "محفوظ")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "منسوخ")) } },
    )
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

// ============================================================================ appearance
@Composable
private fun AppearanceSection(prefs: Prefs) {
    SectionTitle(tr3("Appearance", "Zaahiri shakal", "ظاہری شکل"))
    ThemeAppearanceCard()
    Spacer(Modifier.height(10.dp))
    SCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr3("Language", "Zubaan", "زبان"), style = MaterialTheme.typography.bodyLarge)
            LanguageChips()
        }
        HorizontalDivider()
        SwitchRow(
            tr("Gesture control", "جیسچر کنٹرول"),
            tr(
                "On: slide a Downloading card left to delete it, right to pause / resume. Off: sliding does nothing (use the buttons or the three-dot menu).",
                "آن: ڈاؤن لوڈنگ کارڈ کو بائیں سلائیڈ کرنے سے ڈیلیٹ، دائیں سے پاز / ریزیوم ہوتا ہے۔ بند: سلائیڈ کرنے سے کچھ نہیں ہوگا (بٹن یا تین نقطوں والا مینو استعمال کریں)۔",
            ),
            prefs.swipeActions,
        ) { v -> Settings.update { it.copy(swipeActions = v) } }
    }
}

// ============================================================================ notifications
@Composable
private fun NotificationSection(prefs: Prefs) {
    val ctx = LocalContext.current
    var notifOpen by rememberSaveable { mutableStateOf(false) }
    SectionTitle(tr3("Notifications", "Notifications", "نوٹیفیکیشنز"))
    SCard {
        ExpandHeader(
            title = tr3("Notification options", "Notification ke options", "نوٹیفیکیشن کے آپشنز"),
            subtitle = tr3("Progress, done, failed, sound and more", "Progress, mukammal, nakam, awaaz aur mazeed", "پیش رفت، مکمل، ناکام، آواز اور مزید"),
            isOpen = notifOpen,
        ) { notifOpen = !notifOpen }
        AnimatedVisibility(visible = notifOpen) {
            Column {
                HorizontalDivider()
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
    val latest by rememberUpdatedState(items)
    // Real numbers: every finished file is looked up on the disk, and the disk itself is asked for free / total space.
    // The block refreshes by itself every few seconds while Settings is open.
    var st by remember { mutableStateOf(LiveStorage()) }
    LaunchedEffect(prefs.folder) {
        while (true) {
            st = withContext(Dispatchers.IO) { measureStorage(ctx, prefs.folder, latest) }
            delay(4000)
        }
    }

    SectionTitle(tr3("Storage and stats", "Storage aur stats", "اسٹوریج اور اعداد و شمار"))
    SCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("${st.files}", tr3("Files", "Files", "فائلیں"))
            StatTile("${st.videos} / ${st.audios}", tr3("Video / Audio", "Video / Audio", "ویڈیو / آڈیو"))
            StatTile(Utils.fmtBytes(st.usedByApp), tr3("Used by app", "App ka istemal", "ایپ کا استعمال"))
        }
        if (st.total > 0) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { ((st.total - st.free).toFloat() / st.total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Hint(
                    tr3(
                        "${Utils.fmtBytes(st.free)} free of ${Utils.fmtBytes(st.total)}",
                        "${Utils.fmtBytes(st.total)} mein se ${Utils.fmtBytes(st.free)} khali",
                        "${Utils.fmtBytes(st.total)} میں سے ${Utils.fmtBytes(st.free)} خالی",
                    ),
                )
                if (st.missing > 0) {
                    Hint(
                        tr3(
                            "${st.missing} finished item(s) no longer have a file on the phone.",
                            "${st.missing} mukammal item(s) ki file ab phone mein nahi.",
                            "${st.missing} مکمل آئٹم کی فائل اب فون میں نہیں۔",
                        ),
                    )
                }
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

private class LiveStorage(
    val files: Int = 0, val videos: Int = 0, val audios: Int = 0, val missing: Int = 0,
    val usedByApp: Long = 0, val free: Long = 0, val total: Long = 0,
)

private fun measureStorage(ctx: Context, folder: String, items: List<DownloadItem>): LiveStorage {
    var files = 0; var videos = 0; var audios = 0; var missing = 0; var used = 0L
    for (it in items) {
        if (it.status != Status.DONE) continue
        val f = if (it.filePath.isBlank()) null else java.io.File(it.filePath)
        if (f != null && f.exists()) {
            files++; used += f.length()
            if (it.isAudio) audios++ else videos++
        } else missing++
    }
    val dir = Utils.outputDir(ctx, folder)
    val free = Utils.freeBytes(dir)
    return LiveStorage(files, videos, audios, missing, used, if (free == Long.MAX_VALUE) 0 else free, Utils.totalBytes(dir))
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
                if (r.isSuccess) tr("Backup saved", "بیک اپ محفوظ ہو گیا") else tr("Backup failed", "بیک اپ ناکام"),
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

// ============================================================================ legal and about
@Suppress("DEPRECATION")
private fun appVersion(ctx: Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
} catch (_: Throwable) { "?" }

@Composable
private fun LegalAboutSection(activity: MainActivity, onOpen: (String) -> Unit) {
    SectionTitle(tr3("Legal & About", "Legal aur About", "قانونی اور ایپ کے بارے میں"))
    SCard {
        LegalDocs.allIds.forEachIndexed { i, id ->
            if (i > 0) HorizontalDivider()
            LegalDocRow(id) { onOpen(id) }
        }
    }
    Spacer(Modifier.height(12.dp))
    SCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("StreamSnap  v${appVersion(activity)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Capture any stream, instantly.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            MadeByAnasSignature()
        }
    }
}
