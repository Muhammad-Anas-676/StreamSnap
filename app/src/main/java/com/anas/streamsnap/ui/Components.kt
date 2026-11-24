package com.anas.streamsnap.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.anas.streamsnap.data.tr3
import com.anas.streamsnap.util.Utils

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.anas.streamsnap.core.theme.ensureContrast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anas.streamsnap.data.Status
import com.anas.streamsnap.data.tr
import androidx.compose.foundation.clickable

/** Animated placeholder shimmer for cards that are still loading. */
fun Modifier.shimmer(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val hi = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f).let {
        Color(
            red = (base.red + 1f) / 2f * 0.35f + base.red * 0.65f,
            green = (base.green + 1f) / 2f * 0.35f + base.green * 0.65f,
            blue = (base.blue + 1f) / 2f * 0.35f + base.blue * 0.65f,
        )
    }
    this.drawBehind {
        val w = size.width
        val shift = (x * 2f - 0.5f) * w
        drawRect(
            Brush.linearGradient(
                colors = listOf(base, hi, base),
                start = Offset(shift - w / 2f, 0f),
                end = Offset(shift + w / 2f, 0f),
            )
        )
    }
}

@Composable
fun statusText(s: Status, reason: String = ""): String = when (s) {
    Status.FETCHING -> tr("Reading link…", "لنک پڑھا جا رہا ہے…")
    Status.READY -> tr("Ready", "تیار")
    Status.QUEUED -> tr("In queue", "قطار میں")
    Status.DOWNLOADING -> tr("Downloading", "ڈاؤن لوڈ ہو رہی ہے")
    Status.PAUSED -> tr("Paused", "رکی ہوئی")
    Status.WAITING -> reason.ifBlank { tr("Waiting", "انتظار") }
    Status.SCHEDULED -> tr("Scheduled", "شیڈول")
    Status.FAILED -> tr("Failed", "ناکام")
    Status.DONE -> tr("Done", "مکمل")
}

@Composable
fun statusColor(s: Status): Color = when (s) {
    Status.DOWNLOADING -> MaterialTheme.colorScheme.primary
    Status.FAILED -> MaterialTheme.colorScheme.error
    Status.DONE -> ensureContrast(Color(0xFF1FA971), MaterialTheme.colorScheme.surfaceContainerHighest, 4.7f)
    Status.WAITING, Status.PAUSED -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ClickRow(title: String, subtitle: String? = null, trailing: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}


// ----------------------------------------------------------------------------- platform tag
private class Platform(val label: String, val bg: Color?)

private fun platformOf(site: String, url: String): Platform {
    val h = (site + " " + url).lowercase()
    return when {
        "youtube" in h || "youtu.be" in h -> Platform("YT", Color(0xFFC4001B))
        "instagram" in h -> Platform("IG", Color(0xFFB5175F))
        "tiktok" in h -> Platform("TT", Color(0xFF161616))
        "facebook" in h || "fb.watch" in h -> Platform("FB", Color(0xFF1565C0))
        "twitter" in h || "x.com" in h -> Platform("X", Color(0xFF161616))
        "soundcloud" in h -> Platform("SC", Color(0xFFB93C00))
        "vimeo" in h -> Platform("VM", Color(0xFF0E6F8A))
        "twitch" in h -> Platform("TW", Color(0xFF7239D6))
        "reddit" in h -> Platform("RD", Color(0xFFB93C00))
        else -> Platform((site.trim().firstOrNull() ?: 'W').uppercaseChar().toString(), null)
    }
}

/** Small coloured tag with the platform's initials (white text on a deep brand colour, readable in every theme). */
@Composable
fun PlatformTag(site: String, url: String, modifier: Modifier = Modifier) {
    val pf = remember(site, url) { platformOf(site, url) }
    val bg = pf.bg ?: MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (pf.bg != null) Color.White else MaterialTheme.colorScheme.onSurface
    androidx.compose.material3.Text(
        pf.label,
        modifier = modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp)).background(bg)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold,
    )
}

// ----------------------------------------------------------------------------- storage meter (real numbers)
/**
 * Real storage numbers, measured again every few seconds and immediately when a download finishes or a file is removed:
 * the size of the download folder (all files found on the disk), and free / total space from StatFs.
 */
@Composable
fun StorageMeter(modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs by com.anas.streamsnap.data.Settings.state.collectAsState()
    val items by com.anas.streamsnap.engine.QueueManager.items.collectAsState()
    var stats by remember { mutableStateOf(Triple(0L, 0L, 0L)) } // used by the download folder, free, total
    // Changes when a download completes, a card is removed or a file size changes: the loop below restarts at once.
    val changeKey = items.count { it.status == Status.DONE } to items.filter { it.status == Status.DONE }.sumOf { it.fileSize }
    LaunchedEffect(changeKey, items.size, prefs.folder) {
        while (true) {
            stats = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val dir = com.anas.streamsnap.util.Utils.outputDir(ctx, prefs.folder)
                val free = com.anas.streamsnap.util.Utils.freeBytes(dir)
                Triple(
                    com.anas.streamsnap.util.Utils.dirSize(dir),
                    if (free == Long.MAX_VALUE) 0L else free,
                    com.anas.streamsnap.util.Utils.totalBytes(dir),
                )
            }
            kotlinx.coroutines.delay(3000)
        }
    }
    val (used, free, total) = stats
    val frac = if (total > 0) ((total - free).toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(modifier) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            Text(
                tr3("StreamSnap: ${Utils.fmtBytes(used)}", "StreamSnap: ${Utils.fmtBytes(used)}", "اسٹریم اسنیپ: ${Utils.fmtBytes(used)}"),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                tr3("${Utils.fmtBytes(free)} free", "${Utils.fmtBytes(free)} khali", "${Utils.fmtBytes(free)} خالی"),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
        androidx.compose.material3.LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(androidx.compose.foundation.shape.CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}
