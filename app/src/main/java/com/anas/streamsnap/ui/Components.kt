package com.anas.streamsnap.ui

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
    Status.DONE -> Color(0xFF1FA971)
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
