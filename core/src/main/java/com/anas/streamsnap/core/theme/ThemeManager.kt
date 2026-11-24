package com.anas.streamsnap.core.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

enum class ThemeMode(val key: String) {
    LIGHT("light"), DARK("dark"), AMOLED("amoled"), SYSTEM("system");

    companion object {
        fun from(key: String?): ThemeMode = values().firstOrNull { it.key == key } ?: LIGHT
    }
}

/** Text size choice. Applied only by apps that ask for it (the admin app); the user app ignores it. */
enum class TextScale(val key: String, val factor: Float) {
    SMALL("small", 0.88f), MEDIUM("medium", 1.0f), LARGE("large", 1.16f);

    companion object {
        fun from(key: String?): TextScale = values().firstOrNull { it.key == key } ?: MEDIUM
    }
}

data class ThemeState(
    val mode: ThemeMode = ThemeMode.LIGHT, // Light is the default on first launch
    val accent: Int = 0,                    // index in Accents.list
    val textScale: TextScale = TextScale.MEDIUM,
)

private val Context.themeStore: DataStore<Preferences> by preferencesDataStore(name = "app_theme")

/**
 * One reusable theme manager: copy the `core/theme` folder into any other app.
 * The choice is saved with Jetpack DataStore and applies instantly (no restart).
 */
object ThemeManager {
    private val KEY_MODE = stringPreferencesKey("mode")
    private val KEY_ACCENT = intPreferencesKey("accent")
    private val KEY_TEXT = stringPreferencesKey("text_scale")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ThemeState())
    val state: StateFlow<ThemeState> = _state.asStateFlow()
    private lateinit var appCtx: Context

    /** Call once from Application.onCreate. The small file is read up front so the first frame already has the right theme. */
    fun init(context: Context) {
        appCtx = context.applicationContext
        val p = try {
            runBlocking { appCtx.themeStore.data.first() }
        } catch (_: Throwable) {
            null
        }
        if (p != null) {
            _state.value = ThemeState(
                mode = ThemeMode.from(p[KEY_MODE]),
                accent = (p[KEY_ACCENT] ?: 0).coerceIn(0, Accents.list.size - 1),
                textScale = TextScale.from(p[KEY_TEXT]),
            )
        }
    }

    fun setMode(mode: ThemeMode) {
        _state.value = _state.value.copy(mode = mode)
        scope.launch { appCtx.themeStore.edit { it[KEY_MODE] = mode.key } }
    }

    fun setAccent(index: Int) {
        val i = index.coerceIn(0, Accents.list.size - 1)
        _state.value = _state.value.copy(accent = i)
        scope.launch { appCtx.themeStore.edit { it[KEY_ACCENT] = i } }
    }

    fun setTextScale(scale: TextScale) {
        _state.value = _state.value.copy(textScale = scale)
        scope.launch { appCtx.themeStore.edit { it[KEY_TEXT] = scale.key } }
    }
}
