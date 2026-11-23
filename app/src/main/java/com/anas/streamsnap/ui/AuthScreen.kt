package com.anas.streamsnap.ui

import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anas.streamsnap.R
import com.anas.streamsnap.core.cloud.AuthRepo
import com.anas.streamsnap.data.tr3
import com.anas.streamsnap.session.Session
import kotlinx.coroutines.launch
import java.util.Locale

/** Sign in, sign up and password reset. Google sign-in is offered on both. */
@Composable
fun AuthScreen(activity: MainActivity) {
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 sign in, 1 sign up, 2 reset password
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf(Locale.getDefault().displayCountry) }
    var password by rememberSaveable { mutableStateOf("") }
    var showPw by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun validate(): String {
        val e = email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(e).matches()) return tr3("Enter a valid email address.", "Durust email address likhein.", "درست ای میل ایڈریس لکھیں۔")
        if (mode == 2) return ""
        if (password.length < 6) return tr3("Password must be at least 6 characters.", "Password kam az kam 6 huroof ka ho.", "پاس ورڈ کم از کم 6 حروف کا ہو۔")
        if (mode == 1) {
            if (name.trim().length < 2) return tr3("Enter your name.", "Apna naam likhein.", "اپنا نام لکھیں۔")
            val digits = phone.filter { it.isDigit() }
            if (digits.length < 7 || digits.length > 15) return tr3("Enter a valid phone number.", "Durust phone number likhein.", "درست فون نمبر لکھیں۔")
            if (country.trim().length < 2) return tr3("Enter your country.", "Apna mulk likhein.", "اپنا ملک لکھیں۔")
        }
        return ""
    }

    fun submit() {
        error = ""; info = ""
        val v = validate()
        if (v.isNotEmpty()) { error = v; return }
        busy = true
        scope.launch {
            when (mode) {
                0 -> AuthRepo.signIn(email, password)
                    .onSuccess { Session.onSignedIn(it, "email") }
                    .onFailure { error = it.message ?: "" }
                1 -> AuthRepo.signUp(email, password, name)
                    .onSuccess { Session.onSignedIn(it, "email", name.trim(), phone.trim(), country.trim()) }
                    .onFailure { error = it.message ?: "" }
                else -> AuthRepo.sendReset(email)
                    .onSuccess {
                        info = tr3("Reset link sent. Check your email.", "Reset link bhej diya. Apni email dekhein.", "ری سیٹ لنک بھیج دیا۔ اپنی ای میل دیکھیں۔")
                    }
                    .onFailure { error = it.message ?: "" }
            }
            busy = false
        }
    }

    fun google() {
        error = ""; info = ""
        busy = true
        scope.launch {
            AuthRepo.signInWithGoogle(activity)
                .onSuccess { Session.onSignedIn(it, "google") }
                .onFailure { error = it.message ?: "" }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Image(
            painterResource(R.drawable.streamsnap_logo), null,
            Modifier.size(84.dp).clip(RoundedCornerShape(22.dp)),
        )
        Spacer(Modifier.height(12.dp))
        Text("StreamSnap", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(
            when (mode) {
                1 -> tr3("Create your account", "Apna account banayein", "اپنا اکاؤنٹ بنائیں")
                2 -> tr3("Reset your password", "Password reset karein", "پاس ورڈ ری سیٹ کریں")
                else -> tr3("Sign in to continue", "Jaari rakhne ke liye sign in karein", "جاری رکھنے کے لیے سائن اِن کریں")
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        LanguageChips()
        Spacer(Modifier.height(10.dp))

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (mode == 1) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(tr3("Name", "Naam", "نام")) },
                )
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(tr3("Email", "Email", "ای میل")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            if (mode == 1) {
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' } },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(tr3("Phone number", "Phone number", "فون نمبر")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                OutlinedTextField(
                    value = country, onValueChange = { country = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(tr3("Country", "Mulk", "ملک")) },
                )
            }
            if (mode != 2) {
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(tr3("Password", "Password", "پاس ورڈ")) },
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPw = !showPw }) {
                            Icon(
                                if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = tr3("Show or hide password", "Password dikhayein ya chhupayein", "پاس ورڈ دکھائیں یا چھپائیں"),
                            )
                        }
                    },
                )
            }
        }

        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        if (info.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(info, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text(
                when (mode) {
                    1 -> tr3("Create account", "Account banayein", "اکاؤنٹ بنائیں")
                    2 -> tr3("Send reset link", "Reset link bhejein", "ری سیٹ لنک بھیجیں")
                    else -> tr3("Sign in", "Sign in", "سائن اِن")
                },
            )
        }
        if (mode == 0) {
            TextButton(onClick = { mode = 2; error = ""; info = "" }) {
                Text(tr3("Forgot password?", "Password bhool gaye?", "پاس ورڈ بھول گئے؟"))
            }
        }
        if (mode != 2) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                Text(tr3("or", "ya", "یا"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }
            OutlinedButton(onClick = { google() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(tr3("Continue with Google", "Google se jaari rakhein", "گوگل سے جاری رکھیں"))
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { mode = if (mode == 1) 0 else if (mode == 2) 0 else 1; error = ""; info = "" }) {
            Text(
                when (mode) {
                    1 -> tr3("I already have an account", "Mera account pehle se hai", "میرا اکاؤنٹ پہلے سے ہے")
                    2 -> tr3("Back to sign in", "Sign in par wapas", "سائن اِن پر واپس")
                    else -> tr3("Create a new account", "Naya account banayein", "نیا اکاؤنٹ بنائیں")
                },
            )
        }
    }
}
