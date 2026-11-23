package com.anas.streamsnap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anas.streamsnap.data.tr3
import com.anas.streamsnap.session.Gate
import com.anas.streamsnap.session.Session
import kotlinx.coroutines.launch

/** Shown instead of the app when the account is banned, the app is in maintenance, or an update is required. */
@Composable
fun GateScreen(gate: Gate) {
    val scope = rememberCoroutineScope()
    val icon = when (gate) {
        is Gate.Banned -> Icons.Filled.Block
        is Gate.Maintenance -> Icons.Filled.Construction
        is Gate.UpdateRequired -> Icons.Filled.SystemUpdate
    }
    val title = when (gate) {
        is Gate.Banned -> tr3("Account suspended", "Account muattal hai", "اکاؤنٹ معطل ہے")
        is Gate.Maintenance -> tr3("Under maintenance", "Maintenance jaari hai", "مینٹیننس جاری ہے")
        is Gate.UpdateRequired -> tr3("Update required", "Update zaroori hai", "اپڈیٹ ضروری ہے")
    }
    val body = when (gate) {
        is Gate.Banned -> tr3(
            "This account has been suspended for breaking the Terms of Service. Contact the developer if you think this is a mistake.",
            "Yeh account Terms of Service todne par muattal kar diya gaya hai. Agar aapko lagta hai ghalti hui hai to developer se rabta karein.",
            "یہ اکاؤنٹ سروس کی شرائط توڑنے پر معطل کر دیا گیا ہے۔ اگر آپ کو لگتا ہے غلطی ہوئی ہے تو ڈیولپر سے رابطہ کریں۔",
        )
        is Gate.Maintenance -> gate.message.ifBlank {
            tr3(
                "StreamSnap is being improved right now. Please try again in a little while.",
                "StreamSnap abhi behtar ki ja rahi hai. Thori der baad dobara koshish karein.",
                "اسٹریم اسنیپ ابھی بہتر کی جا رہی ہے۔ تھوڑی دیر بعد دوبارہ کوشش کریں۔",
            )
        }
        is Gate.UpdateRequired -> tr3(
            "A newer version of StreamSnap is needed. Please install the latest version.",
            "StreamSnap ka naya version chahiye. Meherbani karke latest version install karein.",
            "اسٹریم اسنیپ کا نیا ورژن چاہیے۔ براہ کرم تازہ ترین ورژن انسٹال کریں۔",
        )
    }
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        if (gate !is Gate.Banned) {
            Button(onClick = { scope.launch { Session.refreshGate() } }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(tr3("Try again", "Dobara koshish karein", "دوبارہ کوشش کریں"))
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = { scope.launch { Session.logout() } }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(tr3("Sign out", "Sign out", "سائن آؤٹ"))
        }
    }
}
