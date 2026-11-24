package com.anas.streamsnap.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import com.anas.streamsnap.core.ui.GoogleSignInButton
import com.anas.streamsnap.core.cloud.AuthRepo
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.anas.streamsnap.data.tr3
import com.anas.streamsnap.session.ProfileStore
import com.anas.streamsnap.session.Session
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Profile tab: picture, name, email and account facts. The gear icon opens Settings. */
@Composable
fun ProfileScreen(onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val p by ProfileStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var upgrading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ProfileStore.setPhoto(uri)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr3("Profile", "Profile", "پروفائل"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.SettingsIcon, contentDescription = tr3("Settings", "Settings", "سیٹنگز"),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            // ---- avatar
            Box(Modifier.size(116.dp)) {
                Box(
                    Modifier.size(112.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        p.hasLocalPhoto -> AsyncImage(
                            model = ImageRequest.Builder(ctx).data(ProfileStore.photoFile)
                                .memoryCacheKey("profile-" + p.photoVersion).diskCachePolicy(CachePolicy.DISABLED).build(),
                            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                        p.photoUrl.isNotBlank() -> AsyncImage(
                            model = p.photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                        else -> Text(
                            (p.name.trim().firstOrNull() ?: p.email.firstOrNull() ?: 'S').uppercaseChar().toString(),
                            fontSize = 44.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).size(40.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        .semantics { contentDescription = tr3("Change picture", "Picture badlein", "تصویر بدلیں") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.CameraAlt, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            if (p.hasLocalPhoto) {
                TextButton(onClick = { ProfileStore.removePhoto() }) {
                    Text(tr3("Remove my picture", "Meri picture hataein", "میری تصویر ہٹائیں"))
                }
            }
            Text(
                p.name.ifBlank { tr3("Your name", "Aapka naam", "آپ کا نام") },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (p.isGuest) {
                Text(
                    tr3("Guest account", "Guest account", "گیسٹ اکاؤنٹ"),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            } else {
                Text(p.email, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            if (p.isGuest) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            tr3("You are using a Guest account", "Aap Guest account istemal kar rahe hain", "آپ گیسٹ اکاؤنٹ استعمال کر رہے ہیں"),
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            tr3(
                                "Upgrade with an email or Google account to keep your profile, and to sign in again if you change phones.",
                                "Apna profile mehfooz rakhne aur phone badalne par dobara sign in karne ke liye email ya Google account se upgrade karein.",
                                "اپنا پروفائل محفوظ رکھنے اور فون بدلنے پر دوبارہ سائن اِن کرنے کے لیے ای میل یا گوگل اکاؤنٹ سے اپگریڈ کریں۔",
                            ),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { upgrading = true }, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                            Text(tr3("Upgrade account", "Account upgrade karein", "اکاؤنٹ اپگریڈ کریں"))
                        }
                    }
                }
            }

            if (!Session.cloudOn) {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        tr3(
                            "Cloud account is off: this build has no Firebase configuration.",
                            "Cloud account band hai: is build mein Firebase configuration nahi hai.",
                            "کلاؤڈ اکاؤنٹ بند ہے: اس بلڈ میں فائر بیس کنفیگریشن نہیں۔",
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // ---- facts
            val dFmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
            val dtFmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
            val dash = "—"
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    InfoRow(Icons.Filled.Phone, tr3("Phone", "Phone", "فون"), p.phone.ifBlank { dash })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(Icons.Filled.Flag, tr3("Country", "Mulk", "ملک"), p.country.ifBlank { dash })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(Icons.Filled.CalendarMonth, tr3("Joined", "Shamil huye", "شمولیت"), if (p.createdAt > 0) dFmt.format(Date(p.createdAt)) else dash)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(Icons.Filled.Download, tr3("Total downloads", "Kul downloads", "کل ڈاؤن لوڈز"), p.totalDownloads.toString())
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow(
                        Icons.Filled.Schedule, tr3("Last active", "Aakhri active", "آخری فعال"),
                        if (p.lastActive > 0) dtFmt.format(Date(p.lastActive)) else tr3("First session", "Pehla session", "پہلا سیشن"),
                    )
                }
            }
            OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(tr3("Edit details", "Tafseelat badlein", "تفصیلات بدلیں"))
            }
            OutlinedButton(
                onClick = { confirmLogout = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.size(8.dp))
                Text(tr3("Log out", "Log out", "لاگ آؤٹ"), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (editing) EditProfileDialog(p.name, p.phone, p.country) { n, ph, c ->
        editing = false
        if (n != null) {
            ProfileStore.edit(n, ph!!, c!!)
            Session.user.value?.uid?.let {
                com.anas.streamsnap.core.cloud.Cloud.updateUserFields(it, mapOf("name" to n.trim(), "phone" to ph.trim(), "country" to c.trim()))
            }
        }
    }

    if (upgrading) UpgradeDialog(onClose = { upgrading = false })

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(tr3("Log out?", "Log out karein?", "لاگ آؤٹ کریں؟")) },
            text = {
                Text(
                    if (p.isGuest) tr3(
                        "A guest account cannot be recovered after you log out. Upgrade it first if you want to keep it.",
                        "Log out ke baad guest account wapas nahi milta. Rakhna ho to pehle upgrade karein.",
                        "لاگ آؤٹ کے بعد گیسٹ اکاؤنٹ واپس نہیں ملتا۔ رکھنا ہو تو پہلے اپگریڈ کریں۔",
                    ) else tr3(
                        "Your downloaded files stay on the phone. Your profile picture on this phone is removed.",
                        "Aapki downloaded files phone mein rahengi. Is phone se aapki profile picture hat jayegi.",
                        "آپ کی ڈاؤن لوڈ فائلیں فون میں رہیں گی۔ اس فون سے آپ کی پروفائل تصویر ہٹ جائے گی۔",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; scope.launch { Session.logout() } }) {
                    Text(tr3("Log out", "Log out", "لاگ آؤٹ"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(tr3("Cancel", "Raddd karein", "منسوخ")) } },
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EditProfileDialog(name: String, phone: String, country: String, onDone: (String?, String?, String?) -> Unit) {
    var n by remember { mutableStateOf(name) }
    var ph by remember { mutableStateOf(phone) }
    var c by remember { mutableStateOf(country) }
    AlertDialog(
        onDismissRequest = { onDone(null, null, null) },
        title = { Text(tr3("Edit details", "Tafseelat badlein", "تفصیلات بدلیں")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = n, onValueChange = { n = it }, singleLine = true, label = { Text(tr3("Name", "Naam", "نام")) })
                OutlinedTextField(value = ph, onValueChange = { ph = it }, singleLine = true, label = { Text(tr3("Phone", "Phone", "فون")) })
                OutlinedTextField(value = c, onValueChange = { c = it }, singleLine = true, label = { Text(tr3("Country", "Mulk", "ملک")) })
            }
        },
        confirmButton = {
            TextButton(enabled = n.trim().length >= 2, onClick = { onDone(n, ph, c) }) { Text(tr3("Save", "Mehfooz karein", "محفوظ کریں")) }
        },
        dismissButton = { TextButton(onClick = { onDone(null, null, null) }) { Text(tr3("Cancel", "Raddd karein", "منسوخ")) } },
    )
}


/** Upgrade a guest account: with an email and password, or with Google. The user id stays the same. */
@Composable
private fun UpgradeDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun done(u: com.google.firebase.auth.FirebaseUser, provider: String) {
        Session.onSignedIn(u, provider, name.trim())
        onClose()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr3("Upgrade account", "Account upgrade karein", "اکاؤنٹ اپگریڈ کریں")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(tr3("Name", "Naam", "نام")) })
                OutlinedTextField(value = email, onValueChange = { email = it }, singleLine = true, label = { Text(tr3("Email", "Email", "ای میل")) })
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, singleLine = true,
                    label = { Text(tr3("Password", "Password", "پاس ورڈ")) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                GoogleSignInButton(
                    text = tr3("Link with Google", "Google se jorein", "گوگل سے جوڑیں"), enabled = !busy,
                    onClick = {
                        val act = ctx.findMainActivity() ?: return@GoogleSignInButton
                        error = ""; busy = true
                        scope.launch {
                            AuthRepo.linkGoogle(act).onSuccess { done(it, "google") }.onFailure { error = it.message ?: "" }
                            busy = false
                        }
                    },
                )
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && name.trim().length >= 2 && email.contains("@") && password.length >= 6,
                onClick = {
                    error = ""; busy = true
                    scope.launch {
                        AuthRepo.linkEmail(email, password, name).onSuccess { done(it, "email") }.onFailure { error = it.message ?: "" }
                        busy = false
                    }
                },
            ) { Text(tr3("Upgrade", "Upgrade", "اپگریڈ")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr3("Cancel", "Raddd karein", "منسوخ")) } },
    )
}
