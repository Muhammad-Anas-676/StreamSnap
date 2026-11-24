package com.anas.streamsnap.admin

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Optional local security gate of the admin app: a 4-digit PIN (stored only as a salted PBKDF2 hash) and optional fingerprint.
 * The lock is OFF until the admin turns on "App Lock (PIN)" in More > Security. When it is on, the app locks when it
 * opens and when it returns from the background after the idle timeout.
 */
object AdminLock {
    private lateinit var sp: SharedPreferences
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var leftAt = 0L
    private val pinMutex = Mutex()

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences("admin_lock", Context.MODE_PRIVATE)
        _locked.value = lockActive
    }

    val hasPin: Boolean get() = !sp.getString("hash", "").isNullOrEmpty()

    /** Master switch. Off by default; it is turned on only after a PIN exists. */
    var appLockEnabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) {
            sp.edit().putBoolean("enabled", v).apply()
            if (!v) _locked.value = false
        }

    /** The lock really applies only when the switch is on and a PIN exists. */
    val lockActive: Boolean get() = appLockEnabled && hasPin

    var biometricOn: Boolean
        get() = sp.getBoolean("bio", false)
        set(v) { sp.edit().putBoolean("bio", v).apply() }

    /** Seconds in the background before the app locks again (0 = lock as soon as it is left). */
    var timeoutSec: Int
        get() = sp.getInt("timeout", 60)
        set(v) { sp.edit().putInt("timeout", v).apply() }

    // 60,000 PBKDF2 rounds are slow on budget CPUs: they always run on Dispatchers.Default, never on the main thread.
    private fun hash(pin: String, salt: String): String {
        val preferred = if (Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val factory = try {
            SecretKeyFactory.getInstance(preferred)
        } catch (_: Throwable) {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        }
        val bytes = factory.generateSecret(PBEKeySpec(pin.toCharArray(), salt.toByteArray(), 60_000, 256)).encoded
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun setPin(pin: String) = withContext(Dispatchers.Default) {
        pinMutex.withLock {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            val h = hash(pin, salt)
            sp.edit().putString("salt", salt).putString("hash", h).putInt("fails", 0).putLong("lockedUntil", 0).apply()
        }
    }

    /** Seconds before another PIN try is allowed (0 = allowed). */
    fun lockRemainingSec(): Int {
        val left = sp.getLong("lockedUntil", 0) - System.currentTimeMillis()
        return if (left > 0) ((left + 999) / 1000).toInt() else 0
    }

    suspend fun check(pin: String): Boolean = withContext(Dispatchers.Default) {
        pinMutex.withLock {
            if (lockRemainingSec() > 0) return@withLock false
            val ok = try {
                hash(pin, sp.getString("salt", "") ?: "") == sp.getString("hash", "")
            } catch (_: Throwable) {
                false
            }
            if (ok) {
                sp.edit().putInt("fails", 0).putLong("lockedUntil", 0).apply()
                _locked.value = false
            } else {
                // 5 wrong tries lock the pad for 30 s, then longer each round (up to 15 min).
                val fails = sp.getInt("fails", 0) + 1
                val e = sp.edit().putInt("fails", fails)
                if (fails % 5 == 0) {
                    val round = (fails / 5 - 1).coerceAtMost(5)
                    e.putLong("lockedUntil", System.currentTimeMillis() + minOf(30_000L shl round, 900_000L))
                }
                e.apply()
            }
            ok
        }
    }

    fun unlock() { _locked.value = false }

    fun lockNow() { if (lockActive) _locked.value = true }

    fun onStop() { leftAt = SystemClock.elapsedRealtime() }

    fun onStart() {
        if (lockActive && leftAt > 0 && (SystemClock.elapsedRealtime() - leftAt) / 1000 >= timeoutSec) _locked.value = true
        leftAt = 0
    }

    // ------------------------------------------------------------------ fingerprint
    // Budget phones with OEM fingerprint HALs (for example Infinix Hot 8 Lite, Android 8.1 Go) can throw from
    // BiometricManager or BiometricPrompt, so every call is wrapped and any failure just means "use the PIN".

    fun biometricAvailable(ctx: Context): Boolean = try {
        if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
            false
        } else {
            BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        }
    } catch (t: Throwable) {
        false
    }

    // Main thread only: stops two prompts from opening on top of each other.
    private var promptShowing = false

    /**
     * Shows the fingerprint prompt when it is safe to do so. Never throws.
     * [onMessage] gets a short non-fatal text (the PIN pad stays usable); [onSuccess] unlocks.
     */
    fun promptBiometric(activity: FragmentActivity, onSuccess: () -> Unit, onMessage: (String) -> Unit = {}) {
        try {
            if (promptShowing) return
            if (activity.isFinishing || activity.isDestroyed) return
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !activity.hasWindowFocus()) return
            if (activity.supportFragmentManager.isStateSaved) return
            if (!biometricAvailable(activity)) {
                onMessage("Fingerprint is not available on this phone. Use your PIN.")
                return
            }
            promptShowing = true
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    try { onSuccess() } catch (_: Throwable) { }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptShowing = false
                    try {
                        when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_CANCELED -> Unit // the admin chose the PIN
                            BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                                onMessage("Too many fingerprint tries. Use your PIN.")
                            BiometricPrompt.ERROR_HW_UNAVAILABLE, BiometricPrompt.ERROR_HW_NOT_PRESENT, BiometricPrompt.ERROR_NO_BIOMETRICS ->
                                onMessage("Fingerprint is not available right now. Use your PIN.")
                            else -> onMessage("Fingerprint did not work. Use your PIN.")
                        }
                    } catch (_: Throwable) { }
                }

                override fun onAuthenticationFailed() {
                    // One bad touch: the prompt stays open and shows its own hint. Nothing else to do.
                    try { onMessage("Fingerprint not recognised. Try again or use your PIN.") } catch (_: Throwable) { }
                }
            }
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock StreamSnap Admin")
                .setSubtitle("Use your fingerprint")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
            prompt.authenticate(info)
        } catch (t: Throwable) {
            promptShowing = false
            try { onMessage("Fingerprint is not available on this phone. Use your PIN.") } catch (_: Throwable) { }
        }
    }
}
