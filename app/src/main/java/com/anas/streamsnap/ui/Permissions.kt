package com.anas.streamsnap.ui

import android.Manifest
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.anas.streamsnap.data.tr3
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.anas.streamsnap.data.tr
import com.anas.streamsnap.util.Utils
import android.provider.Settings as AndroidSettings

fun notificationsGranted(ctx: Context): Boolean =
    try { NotificationManagerCompat.from(ctx).areNotificationsEnabled() } catch (_: Throwable) { true }

fun openStorageAccessSettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Throwable) {
        try {
            ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) { }
    }
}

fun openNotificationSettings(ctx: Context) {
    try {
        val i = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (_: Throwable) { }
}

/** Notification + storage permission rows (onboarding and Settings). */
@Composable
fun PermissionsBlock() {
    val ctx = LocalContext.current
    var notifOk by remember { mutableStateOf(notificationsGranted(ctx)) }
    var storageOk by remember { mutableStateOf(Utils.hasStoragePermission(ctx)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notifOk = notificationsGranted(ctx)
        storageOk = Utils.hasStoragePermission(ctx)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifOk = notificationsGranted(ctx)
    }
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        storageOk = Utils.hasStoragePermission(ctx)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PermissionRow(
            title = tr("Notifications", "نوٹیفیکیشنز"),
            subtitle = tr("Progress, completed and failed alerts", "پیش رفت، مکمل اور ناکام الرٹس"),
            granted = notifOk,
        ) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else openNotificationSettings(ctx)
        }
        PermissionRow(
            title = tr("Storage access", "اسٹوریج تک رسائی"),
            subtitle = tr("Saves videos in StreamSnap/Download", "ویڈیوز StreamSnap/Download میں محفوظ ہوں گی"),
            granted = storageOk,
        ) {
            if (Build.VERSION.SDK_INT >= 30) openStorageAccessSettings(ctx)
            else storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}

@Composable
private fun PermissionRow(title: String, subtitle: String, granted: Boolean, onAllow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = com.anas.streamsnap.core.theme.ensureContrast(Color(0xFF1FA971), MaterialTheme.colorScheme.surfaceContainerHighest, 4.7f))
        } else {
            OutlinedButton(onClick = onAllow) { Text(tr("Allow", "اجازت دیں")) }
        }
    }
}


/** Shown once, after sign-in and after the documents were accepted: asks for notifications and storage. */
@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.CheckCircle, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(14.dp))
        Text(
            tr3("Allow a few permissions", "Chand ijazatein dein", "چند اجازتیں دیں"),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tr3(
                "StreamSnap needs these to show download progress and to save videos on your phone. You can change them later in Settings.",
                "StreamSnap ko download ki progress dikhane aur videos phone mein mehfooz karne ke liye yeh chahiye. Aap baad mein Settings mein badal sakte hain.",
                "اسٹریم اسنیپ کو ڈاؤن لوڈ کی پیش رفت دکھانے اور ویڈیوز فون میں محفوظ کرنے کے لیے یہ چاہیے۔ آپ بعد میں سیٹنگز میں بدل سکتے ہیں۔",
            ),
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        PermissionsBlock()
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(tr3("Continue", "Jaari rakhein", "جاری رکھیں"))
        }
    }
}
