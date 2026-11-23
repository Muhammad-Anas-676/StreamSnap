package com.anas.streamsnap.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anas.streamsnap.core.cloud.AuthRepo
import com.anas.streamsnap.core.cloud.Cloud
import com.anas.streamsnap.core.cloud.CloudUser
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

@Composable
fun AdminRoot(activity: MainActivity) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!Cloud.ready) {
            Notice(
                icon = { Icon(Icons.Filled.CloudOff, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error) },
                title = "Firebase is not set up",
                body = "This build has no google-services.json. Add the GOOGLE_SERVICES_ADMIN_B64 secret in GitHub and build again (see SETUP.md).",
            )
            return@Surface
        }
        var user by remember { mutableStateOf<FirebaseUser?>(AuthRepo.user) }
        DisposableEffect(Unit) {
            val l = com.google.firebase.auth.FirebaseAuth.AuthStateListener { user = it.currentUser }
            Cloud.auth().addAuthStateListener(l)
            onDispose { Cloud.auth().removeAuthStateListener(l) }
        }
        val u = user
        if (u == null) {
            AdminLogin(activity)
            return@Surface
        }
        // 0 checking, 1 admin, 2 not an admin
        var state by remember(u.uid) { mutableIntStateOf(0) }
        LaunchedEffect(u.uid) {
            state = if (Cloud.isAdmin(u.uid)) 1 else 2
            if (state == 1) AdminStore.refresh()
        }
        when (state) {
            0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            1 -> AdminScaffold()
            else -> NotAdmin(u)
        }
    }
}

@Composable
private fun Notice(icon: @Composable () -> Unit, title: String, body: String, actions: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        actions()
    }
}

@Composable
private fun NotAdmin(u: FirebaseUser) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    Notice(
        icon = { Icon(Icons.Filled.AdminPanelSettings, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error) },
        title = "Not an admin",
        body = "This account is not allowed to open the admin app. In the Firebase console, create a document in the collection admins with this exact ID:\n\n${u.uid}",
    ) {
        Button(onClick = {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("uid", u.uid))
        }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Copy my UID") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { scope.launch { AuthRepo.signOut(ctx) } }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Sign out") }
    }
}

@Composable
private fun AdminLogin(activity: MainActivity) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.AdminPanelSettings, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Text("StreamSnap Admin", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text("Sign in with the admin account", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, singleLine = true, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, singleLine = true, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                error = ""; busy = true
                scope.launch { AuthRepo.signIn(email, password).onFailure { error = it.message ?: "" }; busy = false }
            },
            enabled = !busy && email.isNotBlank() && password.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Sign in") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                error = ""; busy = true
                scope.launch { AuthRepo.signInWithGoogle(activity).onFailure { error = it.message ?: "" }; busy = false }
            },
            enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Continue with Google") }
    }
}

private data class AdminTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    AdminTab("Dashboard", Icons.Filled.BarChart),
    AdminTab("Users", Icons.Filled.Group),
    AdminTab("Screen time", Icons.Filled.Schedule),
    AdminTab("More", Icons.Filled.Tune),
)

@Composable
private fun AdminScaffold() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<CloudUser?>(null) }
    BackHandler(enabled = selected != null) { selected = null }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i && selected == null,
                        onClick = { tab = i; selected = null },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val s = selected
            if (s != null) {
                UserDetailScreen(s, onBack = { selected = null })
            } else when (tab) {
                0 -> DashboardScreen()
                1 -> UsersScreen(onOpen = { selected = it })
                2 -> ScreenTimeScreen()
                else -> MoreScreen()
            }
        }
    }
}
