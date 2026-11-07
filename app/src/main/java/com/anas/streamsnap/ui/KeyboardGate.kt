package com.anas.streamsnap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.anas.streamsnap.data.Settings

/**
 * "Keyboard only when I tap a text field" (Settings > Appearance > Auto keyboard is OFF).
 *
 * Put this on a text field. When the field gets the focus WITHOUT the user touching it (a screen was just
 * opened, the system moved the focus, ...) the focus is taken away again and the keyboard is closed.
 * A real tap on the field works as normal. When "Auto keyboard" is ON this does nothing.
 */
@Composable
fun Modifier.tapToType(): Modifier {
    val prefs by Settings.state.collectAsState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val touch = remember { longArrayOf(0L) }
    var rejected by remember { mutableStateOf(false) }

    LaunchedEffect(rejected) {
        if (rejected) {
            focus.clearFocus(force = true)
            keyboard?.hide()
            rejected = false
        }
    }

    if (prefs.autoKeyboard) return this
    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    if (e.changes.any { it.pressed || it.previousPressed }) touch[0] = System.currentTimeMillis()
                }
            }
        }
        .onFocusChanged { st ->
            if (st.isFocused && System.currentTimeMillis() - touch[0] > 3000L) {
                keyboard?.hide()
                rejected = true
            }
        }
}
