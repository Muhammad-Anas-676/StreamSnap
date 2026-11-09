package com.anas.streamsnap.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.anas.streamsnap.data.DownloadItem
import com.anas.streamsnap.data.Prefs
import com.anas.streamsnap.data.Status
import com.anas.streamsnap.data.Tr
import com.anas.streamsnap.data.chipQualitiesFor
import com.anas.streamsnap.data.qualityLabel
import com.anas.streamsnap.data.tr
import com.anas.streamsnap.engine.QueueManager
import com.anas.streamsnap.util.HintAction
import com.anas.streamsnap.util.Hints
import com.anas.streamsnap.util.Utils

/** The Compose context is the Activity (possibly wrapped); this finds it to ask for a tab switch. */
internal fun Context.findMainActivity(): MainActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is MainActivity) return c
        c = c.baseContext
    }
    return null
}

/** What a left-to-right swipe (or the main round button) does for this state. */
fun primaryAction(item: DownloadItem) {
    when (item.status) {
        Status.FAILED -> QueueManager.retry(item.id)
        Status.PAUSED, Status.WAITING, Status.SCHEDULED -> QueueManager.resume(item.id)
        Status.READY -> QueueManager.startItem(item.id)
        Status.DOWNLOADING, Status.QUEUED -> QueueManager.pause(item.id)
        else -> {}
    }
}

@Composable
fun SwipeQueueCard(
    item: DownloadItem,
    prefs: Prefs,
    dragging: Boolean,
    handle: Modifier,
    modifier: Modifier,
    onOpenLog: () -> Unit,
    selecting: Boolean = false,
    selected: Boolean = false,
    onToggle: () -> Unit = {},
    onSelect: () -> Unit = {},
) {
    val cur by rememberUpdatedState(item)
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.EndToStart -> { QueueManager.cancel(cur.id); true }
            SwipeToDismissBoxValue.StartToEnd -> { primaryAction(cur); false }
            else -> false
        }
    })
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = item.status != Status.FETCHING && !selecting,
        enableDismissFromEndToStart = !selecting,
        backgroundContent = {
            val dir = state.dismissDirection
            val remove = dir == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(
                    when (dir) {
                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                        else -> Color.Transparent
                    }
                ).padding(horizontal = 24.dp),
                contentAlignment = if (remove) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                when (dir) {
                    SwipeToDismissBoxValue.EndToStart ->
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    SwipeToDismissBoxValue.StartToEnd ->
                        Icon(
                            when (cur.status) {
                                Status.FAILED -> Icons.Filled.Refresh
                                Status.DOWNLOADING, Status.QUEUED -> Icons.Filled.Pause
                                Status.READY -> Icons.Filled.Download
                                else -> Icons.Filled.PlayArrow
                            },
                            null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    else -> {}
                }
            }
        },
    ) {
        QueueCard(item, prefs, dragging, handle, onOpenLog, selecting, selected, onToggle, onSelect)
    }
}

@Composable
fun QueueCard(
    item: DownloadItem,
    prefs: Prefs,
    dragging: Boolean,
    handle: Modifier,
    onOpenLog: () -> Unit,
    selecting: Boolean = false,
    selected: Boolean = false,
    onToggle: () -> Unit = {},
    onSelect: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val progress by animateFloatAsState(item.progress, tween(450), label = "progress")
    val perVideo = prefs.qualityMode == "ask"
    val autoPick = prefs.autoStart && perVideo && item.status == Status.READY

    Card(
        modifier = if (selecting) Modifier.clickable(onClick = onToggle) else Modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 10.dp else 0.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                if (selecting) {
                    Icon(
                        if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                        modifier = Modifier.size(22.dp).padding(top = 2.dp),
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Icon(
                        Icons.Filled.DragIndicator, null,
                        modifier = handle.size(22.dp).padding(top = 2.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Thumb(item) {
                    if (!selecting) QueueMenuButton(item, onSelect)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (item.status == Status.FETCHING && item.title.isBlank()) {
                        Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                        Box(Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                    } else {
                        Text(
                            item.displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        )
                        val meta = buildString {
                            append(item.site.ifBlank { Utils.siteName(item.url) })
                            if (!perVideo || item.status != Status.READY) append(" · ").append(qualityLabel(item.quality))
                            if (item.sizeEstimate > 0 && !perVideo) append(" · ~").append(Utils.fmtBytes(item.sizeEstimate))
                        }
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        StatusLine(item, autoPick, prefs)
                    }
                }
                if (!selecting) Trailing(item, progress, hideForPick = autoPick)
            }

            if (perVideo && item.status in setOf(Status.READY, Status.PAUSED, Status.FAILED, Status.QUEUED, Status.SCHEDULED, Status.WAITING)) {
                Spacer(Modifier.height(8.dp))
                QualityChips(item, hideSelection = autoPick)
            }
            if (item.status == Status.FAILED && item.error.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(item.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val hint = remember(item.error, item.url, Tr.lang) { Hints.forError(item.error, item.url) }
                if (hint != null) {
                    Spacer(Modifier.height(6.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)).padding(10.dp),
                    ) {
                        Text(
                            hint.text, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        if (hint.action != null) {
                            val act = hint.action
                            TextButton(
                                onClick = {
                                    ctx.findMainActivity()?.navRequest?.value =
                                        if (act == HintAction.BROWSER) "browser" else "settings"
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            ) {
                                Text(
                                    if (act == HintAction.BROWSER) tr("Open Browser", "براؤزر کھولیں")
                                    else tr("Open Settings", "سیٹنگز کھولیں"),
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onOpenLog) { Text(tr("View exact error in Log", "لاگ میں درست ایرر دیکھیں")) }
            }
        }
    }
}

@Composable
private fun StatusLine(item: DownloadItem, autoPick: Boolean, prefs: Prefs) {
    val text: String
    val color: Color
    when {
        item.status == Status.DOWNLOADING -> {
            text = if (item.progress <= 0f && item.speedText.isBlank()) {
                // The engine is still reading the page / solving the site's checks: no bytes yet.
                tr("Preparing… (can take a minute)", "تیاری ہو رہی ہے… (ایک منٹ لگ سکتا ہے)")
            } else buildString {
                append("${(item.progress * 100).toInt()}%")
                if (item.speedText.isNotBlank()) append(" · ").append(item.speedText)
                if (item.etaSec >= 0) append(" · ETA ").append(Utils.fmtEta(item.etaSec))
            }
            color = MaterialTheme.colorScheme.primary
        }
        autoPick -> {
            text = tr("Pick a quality to start", "شروع کرنے کے لیے کوالٹی چنیں")
            color = MaterialTheme.colorScheme.primary
        }
        item.status == Status.READY && !prefs.autoStart -> {
            text = tr("Ready. Tap the download icon", "تیار۔ ڈاؤن لوڈ آئیکن دبائیں")
            color = MaterialTheme.colorScheme.primary
        }
        else -> {
            text = statusText(item.status, item.waitReason)
            color = statusColor(item.status)
        }
    }
    Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Thumb(item: DownloadItem, overlay: @Composable () -> Unit = {}) {
    Box(
        Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        when {
            item.thumbnail.isNotBlank() -> AsyncImage(
                model = item.thumbnail, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            item.status == Status.FETCHING -> Box(Modifier.fillMaxSize().shimmer())
            else -> Icon(
                if (item.isAudio) Icons.Filled.MusicNote else Icons.Filled.VideoLibrary, null,
                modifier = Modifier.align(Alignment.Center).size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.align(Alignment.TopEnd).padding(3.dp)) { overlay() }
        if (item.durationSec > 0) {
            Text(
                Utils.fmtDuration(item.durationSec),
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .clip(RoundedCornerShape(5.dp)).background(Color(0xB3000000)).padding(horizontal = 5.dp, vertical = 1.dp),
                color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun Trailing(item: DownloadItem, progress: Float, hideForPick: Boolean) {
    when (item.status) {
        Status.FETCHING -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        Status.READY -> if (!hideForPick) {
            FilledIconButton(onClick = { QueueManager.startItem(item.id) }) {
                Icon(Icons.Filled.Download, contentDescription = tr("Download", "ڈاؤن لوڈ"))
            }
        }
        Status.DOWNLOADING -> Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(46.dp), strokeWidth = 4.dp)
            IconButton(onClick = { QueueManager.pause(item.id) }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Filled.Pause, contentDescription = tr("Pause", "روکیں"), modifier = Modifier.size(20.dp))
            }
        }
        Status.QUEUED -> IconButton(onClick = { QueueManager.pause(item.id) }) {
            Icon(Icons.Filled.Pause, contentDescription = tr("Pause", "روکیں"))
        }
        Status.PAUSED, Status.WAITING, Status.SCHEDULED -> IconButton(onClick = { QueueManager.resume(item.id) }) {
            Icon(Icons.Filled.PlayArrow, contentDescription = tr("Start", "شروع"))
        }
        Status.FAILED -> IconButton(onClick = { QueueManager.retry(item.id) }) {
            Icon(Icons.Filled.Refresh, contentDescription = tr("Retry", "دوبارہ"), tint = MaterialTheme.colorScheme.error)
        }
        Status.DONE -> {}
    }
}

@Composable
private fun QualityChips(item: DownloadItem, hideSelection: Boolean) {
    val qualities = chipQualitiesFor(item)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(qualities, key = { it }) { q ->
            val size = item.sizeFor(q)
            FilterChip(
                selected = !hideSelection && item.quality == q,
                onClick = { QueueManager.chooseQuality(item.id, q) },
                label = { Text(qualityLabel(q) + if (size > 0) " · ${Utils.fmtBytes(size)}" else "") },
            )
        }
    }
}
