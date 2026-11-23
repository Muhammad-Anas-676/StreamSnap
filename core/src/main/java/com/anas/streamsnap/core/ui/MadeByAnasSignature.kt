package com.anas.streamsnap.core.ui

import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.anas.streamsnap.core.theme.ensureContrast

private class ScriptFont(val family: FontFamily, val weight: FontWeight)

@Composable
private fun rememberScriptFont(): ScriptFont {
    val ctx = LocalContext.current
    return remember {
        try {
            val am = ctx.assets
            val bold: Typeface? = if (Build.VERSION.SDK_INT >= 26) {
                try {
                    Typeface.Builder(am, "fonts/DancingScript.ttf").setFontVariationSettings("'wght' 700").build()
                } catch (_: Throwable) {
                    null
                }
            } else null
            ScriptFont(FontFamily(bold ?: Typeface.createFromAsset(am, "fonts/DancingScript.ttf")), FontWeight.Normal)
        } catch (_: Throwable) {
            ScriptFont(FontFamily.Cursive, FontWeight.Bold)
        }
    }
}

// Fixed signature colours (green to orange). Brighter on dark / AMOLED, deeper on light.
private val DarkFrom = Color(0xFF4ADE80)
private val DarkTo = Color(0xFFFB923C)
private val LightFrom = Color(0xFF166534)
private val LightTo = Color(0xFF92400E)

/**
 * "Made by Anas" signature. Reusable in every app: put it centred inside the About card.
 * Same font file, sizes and underline as StreamSnap. The gradient is always green to orange and is
 * nudged only if needed so it stays readable on the card behind it.
 */
@Composable
fun MadeByAnasSignature(modifier: Modifier = Modifier, background: Color = MaterialTheme.colorScheme.surfaceContainerLow) {
    val font = rememberScriptFont()
    val darkSurface = background.luminance() < 0.18f
    val c1 = ensureContrast(if (darkSurface) DarkFrom else LightFrom, background, 4.5f)
    val c2 = ensureContrast(if (darkSurface) DarkTo else LightTo, background, 4.5f)
    val soft = MaterialTheme.colorScheme.onSurface
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Made by",
                style = TextStyle(fontFamily = font.family, fontWeight = font.weight, fontSize = 24.sp, color = soft),
                modifier = Modifier.padding(bottom = 5.dp),
            )
            Text(
                "Anas",
                style = TextStyle(
                    fontFamily = font.family, fontWeight = font.weight, fontSize = 46.sp,
                    brush = Brush.linearGradient(listOf(c1, c2)),
                ),
            )
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .width(104.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(c1, c2))),
        )
    }
}
