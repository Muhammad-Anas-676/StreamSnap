package com.anas.streamsnap.session

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.anas.streamsnap.core.cloud.CloudUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class Profile(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val country: String = "",
    val photoUrl: String = "",        // Google photo link (default picture for Google sign-in)
    val createdAt: Long = 0,
    val lastActive: Long = 0,         // when the user used the app before this session
    val totalDownloads: Long = 0,
    val photoVersion: Long = 0,       // bumps when the local picture changes so the UI reloads it
    val hasLocalPhoto: Boolean = false,
    val isGuest: Boolean = false,     // true for a guest (anonymous) account until it is upgraded
)

/**
 * The user's profile, kept on the phone so the app works offline.
 * The picture is saved as a small JPEG inside the app's private storage and stays until the user changes it.
 */
object ProfileStore {
    private lateinit var ctx: Context
    private val sp by lazy { ctx.getSharedPreferences("profile", Context.MODE_PRIVATE) }
    private val _state = MutableStateFlow(Profile())
    val state: StateFlow<Profile> = _state.asStateFlow()
    private var previousSessionEnd = 0L

    fun init(context: Context) {
        ctx = context.applicationContext
        previousSessionEnd = sp.getLong("lastActive", 0)
        _state.value = read()
    }

    val photoFile: File get() = File(ctx.filesDir, "profile.jpg")

    private fun read() = Profile(
        name = sp.getString("name", "") ?: "",
        email = sp.getString("email", "") ?: "",
        phone = sp.getString("phone", "") ?: "",
        country = sp.getString("country", "") ?: "",
        photoUrl = sp.getString("photoUrl", "") ?: "",
        createdAt = sp.getLong("createdAt", 0),
        lastActive = previousSessionEnd,
        totalDownloads = sp.getLong("totalDownloads", 0),
        photoVersion = sp.getLong("photoVersion", 0),
        hasLocalPhoto = photoFile.exists(),
        isGuest = sp.getBoolean("guest", false),
    )

    /** Called after sign-in: stores what the account tells us, without wiping what the user typed. */
    fun onSignedIn(uid: String, name: String, email: String, phone: String, country: String, photoUrl: String, createdAt: Long, guest: Boolean = false) {
        val prevUid = sp.getString("uid", "") ?: ""
        if (prevUid.isNotEmpty() && prevUid != uid) clear() // another account: start clean
        val e = sp.edit().putString("uid", uid)
        if (name.isNotBlank()) e.putString("name", name)
        if (email.isNotBlank()) e.putString("email", email)
        if (phone.isNotBlank()) e.putString("phone", phone)
        if (country.isNotBlank()) e.putString("country", country)
        e.putString("photoUrl", photoUrl).putBoolean("guest", guest)
        if (createdAt > 0) e.putLong("createdAt", createdAt)
        e.apply()
        _state.value = read()
    }

    /**
     * Sign-in on a phone that does not have the profile yet (fresh install, new phone): the cloud profile is the
     * truth for name, phone, country and the download counter, so nothing the cloud already knows is lost.
     * A value that the user typed in the sign-in form right now ([keepName] and so on) stays as typed.
     * The download counter only ever goes up.
     */
    fun mergeFromCloud(remote: CloudUser, keepName: Boolean = false, keepPhone: Boolean = false, keepCountry: Boolean = false) {
        val e = sp.edit()
        if (!keepName && remote.name.isNotBlank()) e.putString("name", remote.name)
        if (!keepPhone && remote.phone.isNotBlank()) e.putString("phone", remote.phone)
        if (!keepCountry && remote.country.isNotBlank()) e.putString("country", remote.country)
        if (remote.createdAt > 0 && sp.getLong("createdAt", 0) == 0L) e.putLong("createdAt", remote.createdAt)
        if (remote.totalDownloads > totalDownloads()) e.putLong("totalDownloads", remote.totalDownloads)
        e.apply()
        _state.value = read()
    }

    fun edit(name: String, phone: String, country: String) {
        sp.edit().putString("name", name.trim()).putString("phone", phone.trim()).putString("country", country.trim()).apply()
        _state.value = read()
    }

    fun setTotalDownloads(n: Long) {
        sp.edit().putLong("totalDownloads", n).apply()
        _state.value = read()
    }

    fun totalDownloads(): Long = sp.getLong("totalDownloads", 0)

    /** Remembers when the user last left the app (shown as "Last active" in the next session). */
    fun markLeft() {
        sp.edit().putLong("lastActive", System.currentTimeMillis()).apply()
    }

    /** Saves a picked picture, scaled down to 512 px, as the local profile picture. */
    fun setPhoto(uri: Uri): Boolean = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / sample > 1024 || opts.outHeight / sample > 1024) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (bmp == null) false else {
            val side = minOf(bmp.width, bmp.height)
            val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            val out = Bitmap.createScaledBitmap(sq, minOf(512, side), minOf(512, side), true)
            photoFile.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            sp.edit().putLong("photoVersion", System.currentTimeMillis()).apply()
            _state.value = read()
            true
        }
    } catch (_: Throwable) {
        false
    }

    fun removePhoto() {
        try { photoFile.delete() } catch (_: Throwable) { }
        sp.edit().putLong("photoVersion", System.currentTimeMillis()).apply()
        _state.value = read()
    }

    /** Logout: removes the profile and picture of this phone. */
    fun clear() {
        try { photoFile.delete() } catch (_: Throwable) { }
        sp.edit().clear().apply()
        _state.value = read()
    }
}
