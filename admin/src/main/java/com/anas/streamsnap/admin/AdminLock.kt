package com.anas.streamsnap.admin

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Local security gate of the admin app: a 4-digit PIN (stored only as a salted PBKDF2 hash) and optional fingerprint.
 * The app locks when it opens and when it returns from the background after the idle timeout.
 */
object AdminLock {
    private lateinit var sp: SharedPreferences
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var leftAt = 0L

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences("admin_lock", Context.MODE_PRIVATE)
        _locked.value = hasPin
    }

    val hasPin: Boolean get() = !sp.getString("hash", "").isNullOrEmpty()

    var biometricOn: Boolean
        get() = sp.getBoolean("bio", false)
        set(v) { sp.edit().putBoolean("bio", v).apply() }

    /** Seconds in the background before the app locks again (0 = lock as soon as it is left). */
    var timeoutSec: Int
        get() = sp.getInt("timeout", 60)
        set(v) { sp.edit().putInt("timeout", v).apply() }

    private fun hash(pin: String, salt: String): String {
        val algo = if (Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val bytes = SecretKeyFactory.getInstance(algo)
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt.toByteArray(), 60_000, 256)).encoded
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        sp.edit().putString("salt", salt).putString("hash", hash(pin, salt)).putInt("fails", 0).putLong("lockedUntil", 0).apply()
    }

    /** Seconds before another PIN try is allowed (0 = allowed). */
    fun lockRemainingSec(): Int {
        val left = sp.getLong("lockedUntil", 0) - System.currentTimeMillis()
        return if (left > 0) ((left + 999) / 1000).toInt() else 0
    }

    fun check(pin: String): Boolean {
        if (lockRemainingSec() > 0) return false
        val stored = sp.getString("hash", "") ?: ""
        val ok = stored.isNotEmpty() &&
            MessageDigest.isEqual(hash(pin, sp.getString("salt", "") ?: "").toByteArray(), stored.toByteArray()) // constant-time compare
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
        return ok
    }

    fun unlock() { _locked.value = false }

    fun lockNow() { if (hasPin) _locked.value = true }

    fun onStop() { leftAt = SystemClock.elapsedRealtime() }

    fun onStart() {
        if (hasPin && leftAt > 0 && (SystemClock.elapsedRealtime() - leftAt) / 1000 >= timeoutSec) _locked.value = true
        leftAt = 0
    }

    // ------------------------------------------------------------------ fingerprint
    fun biometricAvailable(ctx: Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    fun promptBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
        if (!biometricAvailable(activity)) return
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock StreamSnap Admin")
            .setSubtitle("Use your fingerprint")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }
}
