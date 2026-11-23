package com.anas.streamsnap.session

import android.content.Context
import android.os.Build
import com.anas.streamsnap.core.cloud.AuthRepo
import com.anas.streamsnap.core.cloud.Cloud
import com.anas.streamsnap.core.cloud.ScreenTracker
import com.anas.streamsnap.core.legal.LegalDocs
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why the app is blocked right now (shown instead of the main screens). */
sealed class Gate {
    object Banned : Gate()
    data class Maintenance(val message: String) : Gate()
    object UpdateRequired : Gate()
}

/**
 * Sign-in state, consent and remote switches. When Firebase is not configured (no google-services.json)
 * [cloudOn] is false and the app simply runs without an account.
 */
object Session {
    private lateinit var app: Context
    private val sp by lazy { app.getSharedPreferences("session", Context.MODE_PRIVATE) }

    val cloudOn: Boolean get() = Cloud.ready

    private val _user = MutableStateFlow<FirebaseUser?>(null)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    private val _consented = MutableStateFlow(false)
    val consented: StateFlow<Boolean> = _consented.asStateFlow()

    private val _gate = MutableStateFlow<Gate?>(null)
    val gate: StateFlow<Gate?> = _gate.asStateFlow()

    private val _announcement = MutableStateFlow<String?>(null)
    val announcement: StateFlow<String?> = _announcement.asStateFlow()

    val versionName: String
        get() = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "" } catch (_: Throwable) { "" }

    @Suppress("DEPRECATION")
    val versionCode: Long
        get() = try {
            val p = app.packageManager.getPackageInfo(app.packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
        } catch (_: Throwable) { 0L }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        if (!Cloud.ready) return
        _user.value = Cloud.auth().currentUser
        _consented.value = hasConsent(_user.value?.uid)
        Cloud.auth().addAuthStateListener { fa ->
            _user.value = fa.currentUser
            _consented.value = hasConsent(fa.currentUser?.uid)
        }
    }

    private fun hasConsent(uid: String?): Boolean = uid != null && sp.getInt("consent_$uid", 0) >= LegalDocs.VERSION

    /** The user accepted the documents: remembered here and recorded in the cloud. */
    fun acceptConsent() {
        val uid = _user.value?.uid ?: return
        sp.edit().putInt("consent_$uid", LegalDocs.VERSION).apply()
        _consented.value = true
        Cloud.saveConsent(uid, LegalDocs.VERSION)
    }

    /** Called right after a successful sign-in or sign-up. */
    fun onSignedIn(u: FirebaseUser, provider: String, name: String = "", phone: String = "", country: String = "") {
        val finalName = name.ifBlank { u.displayName ?: "" }
        val email = u.email ?: ""
        val created = u.metadata?.creationTimestamp ?: 0L
        ProfileStore.onSignedIn(u.uid, finalName, email, phone, country, u.photoUrl?.toString() ?: "", created)
        Cloud.upsertUser(
            uid = u.uid, name = finalName, email = email, provider = provider,
            phone = phone, country = country, createdAt = created, versionName = versionName,
        )
    }

    /** Refreshes "last active", app version and device info once per app start. */
    fun touch() {
        val u = _user.value ?: return
        val p = ProfileStore.state.value
        Cloud.upsertUser(
            uid = u.uid, name = p.name, email = p.email, provider = AuthRepo.provider,
            phone = p.phone, country = p.country, createdAt = p.createdAt, versionName = versionName,
            totalDownloads = ProfileStore.totalDownloads(),
        )
    }

    /** Checks if the account was banned and reads the maintenance / update switches. */
    suspend fun refreshGate() {
        val u = _user.value ?: return
        val me = Cloud.fetchUser(u.uid)
        val cfg = Cloud.fetchConfig()
        _gate.value = when {
            me != null && me.banned -> Gate.Banned
            cfg != null && cfg.maintenance -> Gate.Maintenance(cfg.announcement)
            cfg != null && cfg.minVersionCode > versionCode -> Gate.UpdateRequired
            else -> null
        }
        if (cfg != null && cfg.announcement.isNotBlank() && !cfg.maintenance && cfg.announcementId > sp.getLong("seen_announcement", 0)) {
            _announcement.value = cfg.announcement
            sp.edit().putLong("seen_announcement", cfg.announcementId).apply()
        }
    }

    fun dismissAnnouncement() { _announcement.value = null }

    suspend fun logout() {
        ScreenTracker.pause()
        ScreenTracker.reset()
        AuthRepo.signOut(app)
        ProfileStore.clear()
        _gate.value = null
    }
}
