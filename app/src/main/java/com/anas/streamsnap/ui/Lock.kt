package com.anas.streamsnap.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.data.tr
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

fun canUseBiometric(ctx: Context): Boolean = try {
    BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        BiometricManager.BIOMETRIC_SUCCESS
} catch (_: Throwable) { false }

private fun showBiometric(activity: ComponentActivity, onSuccess: () -> Unit) {
    val fa = activity as? FragmentActivity ?: return
    val prompt = BiometricPrompt(fa, ContextCompat.getMainExecutor(fa), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("StreamSnap")
        .setSubtitle(tr("Unlock with fingerprint", "فنگر پرنٹ سے کھولیں"))
        .setNegativeButtonText(tr("Use PIN", "PIN استعمال کریں"))
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    try { prompt.authenticate(info) } catch (_: Throwable) { }
}

@Composable
fun LockScreen(activity: ComponentActivity, onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    val prefs by Settings.state.collectAsState()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val bioOk = prefs.lockBiometric && canUseBiometric(ctx)

    LaunchedEffect(Unit) { if (bioOk) showBiometric(activity, onUnlock) }

    fun press(d: String) {
        if (pin.length >= 4) return
        error = false
        pin += d
        if (pin.length == 4) {
            if (Settings.checkPin(pin)) {
                onUnlock()
            } else {
                error = true
                pin = ""
                scope.launch {
                    repeat(3) {
                        shake.animateTo(16f, tween(45))
                        shake.animateTo(-16f, tween(45))
                    }
                    shake.animateTo(0f, tween(45))
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("StreamSnap", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(
            if (error) tr("Wrong PIN, try again", "غلط PIN، دوبارہ کوشش کریں") else tr("Enter your PIN", "اپنا PIN درج کریں"),
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.offset { IntOffset(shake.value.roundToInt(), 0) },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            repeat(4) { i ->
                Box(
                    Modifier.size(16.dp).clip(CircleShape).background(
                        if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
        Spacer(Modifier.height(32.dp))

        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                r.forEach { d -> Key(d) { press(d) } }
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (bioOk) {
                Key(icon = true, content = {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }) { showBiometric(activity, onUnlock) }
            } else Spacer(Modifier.size(72.dp))
            Key("0") { press("0") }
            Key(icon = true, content = {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
            }) { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
        }
    }
}

@Composable
private fun Key(label: String = "", icon: Boolean = false, content: @Composable () -> Unit = {}, onClick: () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon) content() else Text(label, fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Ask for a 4 digit PIN twice. */
@Composable
fun PinSetupDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val valid = a.length == 4 && a == b
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Set app PIN", "ایپ PIN سیٹ کریں")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = a, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) a = it },
                    label = { Text(tr("New PIN (4 digits)", "نیا PIN (4 ہندسے)")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    value = b, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) b = it },
                    label = { Text(tr("Repeat PIN", "PIN دوبارہ لکھیں")) }, singleLine = true,
                    isError = b.length == 4 && a != b,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(a) }, enabled = valid) { Text(tr("Save", "محفوظ کریں")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "منسوخ")) } },
    )
}
