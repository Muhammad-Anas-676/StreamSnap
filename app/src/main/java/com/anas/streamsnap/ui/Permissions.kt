package com.anas.streamsnap.ui

import android.Manifest
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
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF1FA971))
        } else {
            OutlinedButton(onClick = onAllow) { Text(tr("Allow", "اجازت دیں")) }
        }
    }
}
