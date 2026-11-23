package com.anas.streamsnap.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anas.streamsnap.core.t
import com.anas.streamsnap.core.theme.Accents
import com.anas.streamsnap.core.theme.ThemeManager
import com.anas.streamsnap.core.theme.ThemeMode
import com.anas.streamsnap.core.theme.bestOn

/**
 * Theme card for Settings: mode chips (Light, Dark, AMOLED, System) and a tappable
 * "Accent colour" row that expands into the 10 accent swatches. Drop it into any app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeAppearanceCard(modifier: Modifier = Modifier) {
    val st by ThemeManager.state.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("Theme", "Theme", "تھیم"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    ThemeMode.LIGHT to t("Light", "Light", "لائٹ"),
                    ThemeMode.DARK to t("Dark", "Dark", "ڈارک"),
                    ThemeMode.AMOLED to t("AMOLED", "AMOLED", "AMOLED"),
                    ThemeMode.SYSTEM to t("System", "System", "سسٹم"),
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = st.mode == mode,
                        onClick = { ThemeManager.setMode(mode) },
                        label = { Text(label) },
                        leadingIcon = if (st.mode == mode) {
                            { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                        } else null,
                    )
                }
            }
            if (st.mode == ThemeMode.AMOLED) {
                Text(
                    t("Pure black background to save battery on AMOLED screens.",
                        "AMOLED screen par battery bachane ke liye bilkul kala background.",
                        "AMOLED اسکرین پر بیٹری بچانے کے لیے خالص کالا بیک گراؤنڈ۔"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Accent colour row with expand icon
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Accents.color(st.accent))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
                Column(Modifier.weight(1f)) {
                    Text(t("Accent colour", "Accent rang", "اکسینٹ رنگ"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(Accents.name(st.accent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = t("Show colours", "Rang dekhein", "رنگ دیکھیں"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Accents.list.forEachIndexed { i, (name, color) ->
                        val selected = st.accent == i
                        Box(
                            Modifier
                                .size(48.dp) // 48dp touch target
                                .clip(CircleShape)
                                .clickable { ThemeManager.setAccent(i) }
                                .semantics { contentDescription = name },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        if (selected) 3.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(Icons.Filled.Check, null, tint = bestOn(color), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
