package com.anas.streamsnap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anas.streamsnap.data.tr3
import com.anas.streamsnap.util.Country
import com.anas.streamsnap.util.CountryCodes

/**
 * One field for the phone number: a tappable "flag + dial code" chip on the left (opens a searchable list)
 * and the local number on the right. Typing or pasting a number that starts with + or 00 switches the country by itself.
 *
 * [iso] and [local] are owned by the caller (so they survive rotation). [onChange] gets the new country and local text.
 */
@Composable
fun CountryPhoneField(
    iso: String,
    local: String,
    onChange: (iso: String, local: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val country = CountryCodes.byIso(iso) ?: CountryCodes.default()
    var picker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = local,
        onValueChange = { raw ->
            val cleaned = raw.filter { c -> c.isDigit() || c == '+' || c == ' ' || c == '-' }
            val d = CountryCodes.detect(cleaned, country)
            if (d != null) onChange(d.first.iso, d.second) else onChange(country.iso, cleaned.filter { c -> c != '+' })
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        label = { Text(tr3("Phone number", "Phone number", "فون نمبر")) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        leadingIcon = {
            Row(
                Modifier
                    .padding(start = 6.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { picker = true }
                    .padding(horizontal = 6.dp, vertical = 10.dp)
                    .semantics { contentDescription = country.name + " " + country.dialCode },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(country.flagEmoji, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                Text(country.dialCode, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )

    if (picker) {
        CountryPickerDialog(
            onPick = { c -> onChange(c.iso, local); picker = false },
            onDismiss = { picker = false },
        )
    }
}

/** Searchable list of all countries (name, ISO code or dial code). */
@Composable
fun CountryPickerDialog(onPick: (Country) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val list = remember(query) { CountryCodes.search(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr3("Select country", "Mulk chunein", "ملک منتخب کریں")) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(tr3("Search country or code", "Mulk ya code talash karein", "ملک یا کوڈ تلاش کریں")) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                )
                Spacer(Modifier.size(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list, key = { it.iso }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(c.flagEmoji, style = MaterialTheme.typography.titleMedium)
                            Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(c.dialCode, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr3("Close", "Band karein", "بند کریں")) } },
    )
}
