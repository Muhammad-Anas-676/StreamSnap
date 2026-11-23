package com.anas.streamsnap.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Card used around every chart. Colours come only from the theme, so every chart reads in Light, Dark and AMOLED. */
@Composable
fun ChartCard(title: String, modifier: Modifier = Modifier, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyChart() {
    Text("No data in this period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Line chart with a soft fill. [first] and [last] label the two ends of the x axis. */
@Composable
fun LineChart(values: List<Float>, first: String, last: String) {
    if (values.isEmpty() || values.all { it == 0f }) { EmptyChart(); return }
    val line = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = values.max().coerceAtLeast(1f)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("max ${max.toInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val w = size.width
            val h = size.height
            drawLine(grid, Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())
            drawLine(grid, Offset(0f, 0f), Offset(w, 0f), strokeWidth = 1.dp.toPx())
            val stepX = if (values.size > 1) w / (values.size - 1) else 0f
            val path = Path()
            val area = Path()
            values.forEachIndexed { i, v ->
                val x = i * stepX
                val y = h - (v / max) * (h - 6.dp.toPx()) - 3.dp.toPx()
                if (i == 0) { path.moveTo(x, y); area.moveTo(x, h); area.lineTo(x, y) } else { path.lineTo(x, y); area.lineTo(x, y) }
            }
            area.lineTo((values.size - 1) * stepX, h)
            area.close()
            drawPath(area, fill)
            drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            values.forEachIndexed { i, v ->
                val x = i * stepX
                val y = h - (v / max) * (h - 6.dp.toPx()) - 3.dp.toPx()
                drawCircle(line, radius = 3.5.dp.toPx(), center = Offset(x, y))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(last, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Vertical bars. [labels] are shown under the first, middle and last bar. */
@Composable
fun BarChart(values: List<Float>, labels: List<String>) {
    if (values.isEmpty() || values.all { it == 0f }) { EmptyChart(); return }
    val max = values.max().coerceAtLeast(1f)
    Column {
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            values.forEach { v ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight((v / max).coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (v == max) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Horizontal bars with the label on the left and the number on the right. */
@Composable
fun HBarChart(items: List<Pair<String, Float>>, unit: (Float) -> String = { it.toInt().toString() }) {
    if (items.isEmpty()) { EmptyChart(); return }
    val max = items.maxOf { it.second }.coerceAtLeast(1f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, v) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    label, modifier = Modifier.width(96.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
                )
                Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth((v / max).coerceIn(0.02f, 1f)).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
                Text(unit(v), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Donut chart with a legend. Slice colours come from the theme roles. */
@Composable
fun DonutChart(slices: List<Pair<String, Float>>) {
    val total = slices.sumOf { it.second.toDouble() }.toFloat()
    if (slices.isEmpty() || total <= 0f) { EmptyChart(); return }
    val colors = listOf(
        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.outline,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(Modifier.size(110.dp)) {
            val stroke = 20.dp.toPx()
            var start = -90f
            slices.forEachIndexed { i, (_, v) ->
                val sweep = 360f * v / total
                drawArc(
                    color = colors[i % colors.size], startAngle = start, sweepAngle = (sweep - 2f).coerceAtLeast(0.5f), useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke),
                )
                start += sweep
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEachIndexed { i, (label, v) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(colors[i % colors.size]))
                    Text(
                        "$label  ${v.toInt()} (${(v * 100 / total).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
