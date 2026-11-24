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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.first
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


/**
 * First-run permission pop-ups, one after the other, over a plain background (after sign-in and consent):
 *  1. Notifications: the native Android 13+ dialog (older Android: only when they are off, a small dialog).
 *  2. Storage: the native dialog on Android 10 and below; on Android 11+ a small dialog that opens the
 *     "All files access" page and moves on by itself when the user comes back (or on Skip).
 * Every answer, including "no", moves to the next step, so the user is never stuck. [onDone] opens the main app.
 */
@Composable
fun PermissionPopups(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // 0 notifications, 1 storage, 2 finished
    var step by rememberSaveable { mutableIntStateOf(if (notificationsGranted(ctx) && Build.VERSION.SDK_INT >= 33) 1 else 0) }
    var requested by rememberSaveable { mutableStateOf(-1) } // the step whose native request was already fired
    var waitingSettings by rememberSaveable { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { step = 1 }
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { step = 2 }

    // Back from the system settings page: that step is handled, go on.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (waitingSettings) {
            waitingSettings = false
            step = step + 1
        }
    }

    LaunchedEffect(step) {
        if (step == 0) {
            if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted(ctx)) {
                if (requested != 0) {
                    // Never open a system dialog before the screen is really resumed (the activity can still be changing).
                    lifecycleOwner.lifecycle.currentStateFlow.first { it == Lifecycle.State.RESUMED }
                    requested = 0
                    try { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } catch (_: Throwable) { step = 1 }
                }
            } else if (Build.VERSION.SDK_INT >= 33 || notificationsGranted(ctx)) {
                step = 1 // already allowed (or Android 12 and older with notifications on)
            }
        } else if (step == 1) {
            if (Utils.hasStoragePermission(ctx)) {
                step = 2
            } else if (Build.VERSION.SDK_INT < 30 && requested != 1) {
                lifecycleOwner.lifecycle.currentStateFlow.first { it == Lifecycle.State.RESUMED }
                requested = 1
                try { storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) } catch (_: Throwable) { step = 2 }
            }
        } else {
            onDone()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

    // Android 12 and older: notifications are normally on already; only ask when they were turned off.
    if (step == 0 && Build.VERSION.SDK_INT < 33 && !notificationsGranted(ctx)) {
        AlertDialog(
            onDismissRequest = { step = 1 },
            title = { Text(tr3("Allow notifications", "Notifications ki ijazat dein", "نوٹیفیکیشنز کی اجازت دیں")) },
            text = {
                Text(
                    tr3(
                        "StreamSnap uses notifications for download progress and messages from the team.",
                        "StreamSnap download ki progress aur team ke paighamaat ke liye notifications istemal karta hai.",
                        "اسٹریم اسنیپ ڈاؤن لوڈ کی پیش رفت اور ٹیم کے پیغامات کے لیے نوٹیفیکیشنز استعمال کرتا ہے۔",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { waitingSettings = true; openNotificationSettings(ctx) }) {
                    Text(tr3("Allow", "Ijazat dein", "اجازت دیں"))
                }
            },
            dismissButton = { TextButton(onClick = { step = 1 }) { Text(tr3("Not now", "Abhi nahi", "ابھی نہیں")) } },
        )
    }

    // Android 11+: the all-files page cannot be a native one-tap dialog, so a compact dialog leads to it.
    if (step == 1 && Build.VERSION.SDK_INT >= 30 && !Utils.hasStoragePermission(ctx)) {
        AlertDialog(
            onDismissRequest = { step = 2 },
            title = { Text(tr3("Allow Storage Access", "Storage access ki ijazat dein", "اسٹوریج تک رسائی کی اجازت دیں")) },
            text = {
                Text(
                    tr3(
                        "StreamSnap saves your videos in StreamSnap/Download. Turn on \"Allow access to manage all files\" on the next page.",
                        "StreamSnap aap ki videos StreamSnap/Download mein mehfooz karta hai. Agle page par \"Allow access to manage all files\" on karein.",
                        "اسٹریم اسنیپ آپ کی ویڈیوز StreamSnap/Download میں محفوظ کرتا ہے۔ اگلے صفحے پر \"Allow access to manage all files\" آن کریں۔",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { waitingSettings = true; openStorageAccessSettings(ctx) }) {
                    Text(tr3("Allow Storage Access", "Storage access dein", "اسٹوریج تک رسائی دیں"))
                }
            },
            dismissButton = { TextButton(onClick = { step = 2 }) { Text(tr3("Skip / Later", "Chhor dein / Baad mein", "چھوڑیں / بعد میں")) } },
        )
    }
}
