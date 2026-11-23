package com.anas.streamsnap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.unit.dp
import com.anas.streamsnap.data.Settings

/** English / Roman / Urdu chips. Used in Settings and on the login and consent screens. */
@Composable
fun LanguageChips(modifier: Modifier = Modifier) {
    val prefs by Settings.state.collectAsState()
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("en" to "English", "ro" to "Roman", "ur" to "اردو").forEach { (code, label) ->
            FilterChip(
                selected = prefs.language == code,
                onClick = { Settings.update { it.copy(language = code) } },
                label = { Text(label) },
            )
        }
    }
}
