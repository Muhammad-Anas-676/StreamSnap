package com.anas.streamsnap.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.anas.streamsnap.core.R

/**
 * "Continue with Google" button that follows Google's branding rules: the 4-colour G, a flat surface with a thin
 * border, and the official light / dark colours. It switches with the active theme (Light, Dark, AMOLED).
 */
@Composable
fun GoogleSignInButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.3f
    val fill = if (dark) Color(0xFF131314) else Color(0xFFFFFFFF)
    val stroke = if (dark) Color(0xFF8E918F) else Color(0xFF747775)
    val label = if (dark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(26.dp),
        color = fill,
        border = BorderStroke(1.dp, stroke),
        shadowElevation = if (dark) 0.dp else 1.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(22.dp))
            Text(
                text, modifier = Modifier.padding(start = 12.dp),
                color = if (enabled) label else label.copy(alpha = 0.5f),
                fontSize = 15.sp, fontWeight = FontWeight.Medium,
            )
        }
    }
}
