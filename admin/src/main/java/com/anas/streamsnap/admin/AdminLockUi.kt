package com.anas.streamsnap.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Dots plus a number pad. Calls [onComplete] when 4 digits were entered. */
@Composable
private fun PinPad(
    title: String, message: String, isError: Boolean, enabled: Boolean,
    onComplete: (String) -> Unit, below: @Composable () -> Unit = {},
) {
    var pin by remember { mutableStateOf("") }
    fun press(d: String) {
        if (!enabled || pin.length >= 4) return
        pin += d
        if (pin.length == 4) {
            val done = pin
            pin = ""
            onComplete(done)
        }
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message, textAlign = TextAlign.Center,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier.size(16.dp).clip(CircleShape).background(
                        if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            rows.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { r.forEach { Key(it, enabled) { press(it) } } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(68.dp))
                Key("0", enabled) { press("0") }
                Box(
                    Modifier.size(68.dp).clip(CircleShape).clickable(enabled = enabled && pin.isNotEmpty()) { pin = pin.dropLast(1) }
                        .semantics { contentDescription = "Delete" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = MaterialTheme.colorScheme.onSurface) }
            }
        }
        Spacer(Modifier.height(16.dp))
        below()
    }
}

@Composable
private fun Key(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(68.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface) }
}

/** The lock screen. Fingerprint is offered first when it is turned on. */
@Composable
fun AdminLockScreen(activity: MainActivity) {
    var error by remember { mutableStateOf(false) }
    var remain by remember { mutableIntStateOf(AdminLock.lockRemainingSec()) }
    val bioReady = AdminLock.biometricOn && AdminLock.biometricAvailable(activity)

    LaunchedEffect(Unit) {
        if (bioReady) AdminLock.promptBiometric(activity) { AdminLock.unlock() }
    }
    LaunchedEffect(Unit) {
        while (true) { remain = AdminLock.lockRemainingSec(); delay(1000) }
    }
    PinPad(
        title = "StreamSnap Admin is locked",
        message = when {
            remain > 0 -> "Too many wrong tries. Wait $remain s."
            error -> "Wrong PIN. Try again."
            else -> "Enter your 4-digit PIN"
        },
        isError = error || remain > 0,
        enabled = remain == 0,
        onComplete = { pin ->
            if (!AdminLock.check(pin)) { error = true; remain = AdminLock.lockRemainingSec() } else error = false
        },
        below = {
            if (bioReady) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).clickable { AdminLock.promptBiometric(activity) { AdminLock.unlock() } }
                        .semantics { contentDescription = "Use fingerprint" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Fingerprint, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary) }
            }
        },
    )
}

/** Creates or changes the PIN: enter it twice. */
@Composable
fun AdminPinSetup(onDone: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableStateOf(false) }
    PinPad(
        title = if (first == null) "Create a PIN" else "Repeat the PIN",
        message = when {
            mismatch -> "The PINs did not match. Start again."
            first == null -> "Choose a 4-digit PIN that protects the admin app"
            else -> "Enter the same PIN once more"
        },
        isError = mismatch, enabled = true,
        onComplete = { pin ->
            val f = first
            if (f == null) { first = pin; mismatch = false }
            else if (f == pin) { AdminLock.setPin(pin); onDone() }
            else { first = null; mismatch = true }
        },
    )
}
