package com.anas.streamsnap.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anas.streamsnap.core.legal.LegalDocRow
import com.anas.streamsnap.core.legal.LegalDocs
import com.anas.streamsnap.core.legal.LegalReader
import com.anas.streamsnap.data.tr3
import com.anas.streamsnap.session.Session
import kotlinx.coroutines.launch

/** Shown after sign-up / sign-in until the user accepts the current version of the documents. */
@Composable
fun ConsentScreen() {
    var agreed by rememberSaveable { mutableStateOf(false) }
    var openDoc by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Icon(Icons.Filled.VerifiedUser, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Text(
            tr3("Before you continue", "Aage barhne se pehle", "آگے بڑھنے سے پہلے"),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            tr3(
                "Please read these documents. They explain what we collect, including the links you add and the time you spend on each screen, and the rules for using the app.",
                "Meherbani karke yeh documents parhein. Inmein likha hai ke hum kya jama karte hain, jis mein aapke dale hue links aur har screen par guzara hua waqt shamil hai, aur app ke qawaid.",
                "براہ کرم یہ دستاویزات پڑھیں۔ ان میں لکھا ہے کہ ہم کیا جمع کرتے ہیں، جس میں آپ کے ڈالے ہوئے لنکس اور ہر اسکرین پر گزارا ہوا وقت شامل ہے، اور ایپ کے قواعد۔",
            ),
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                LegalDocs.consentIds.forEachIndexed { i, id ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LegalDocRow(id) { openDoc = id }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clickable { agreed = !agreed },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = agreed, onCheckedChange = { agreed = it })
            Text(
                tr3(
                    "I have read and agree to the Privacy Policy, Terms of Service, Disclaimer and the Data and Activity Notice.",
                    "Maine Privacy Policy, Terms of Service, Disclaimer aur Data and Activity Notice parh liye hain aur main in se ittefaq karta hoon.",
                    "میں نے پرائیویسی پالیسی، سروس کی شرائط، دستبرداری اور ڈیٹا و سرگرمی نوٹس پڑھ لیے ہیں اور ان سے اتفاق کرتا ہوں۔",
                ),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = { Session.acceptConsent() }, enabled = agreed, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(tr3("Accept and continue", "Manzoor karein aur jaari rakhein", "قبول کریں اور جاری رکھیں"))
        }
        TextButton(onClick = { scope.launch { Session.logout() } }) {
            Text(tr3("Decline and sign out", "Inkar karein aur sign out", "انکار کریں اور سائن آؤٹ"))
        }
    }

    openDoc?.let { LegalReader(it, onClose = { openDoc = null }) }
}
