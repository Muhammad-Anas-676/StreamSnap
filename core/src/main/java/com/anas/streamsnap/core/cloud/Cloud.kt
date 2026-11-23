package com.anas.streamsnap.core.cloud

import android.content.Context
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around Firebase. Every call is safe when Firebase is not configured
 * (no google-services.json): [ready] is false and the calls simply do nothing.
 */
object Cloud {
    const val USERS = "users"
    const val LINKS = "events_links"
    const val SCREENS = "events_screens"
    const val ADMINS = "admins"
    const val CONFIG = "config"

    @Volatile private var appCtx: Context? = null

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
    }

    val ready: Boolean
        get() = try {
            val c = appCtx
            c != null && FirebaseApp.getApps(c).isNotEmpty()
        } catch (_: Throwable) {
            false
        }

    fun auth(): FirebaseAuth = FirebaseAuth.getInstance()
    fun db(): FirebaseFirestore = FirebaseFirestore.getInstance()
    fun uid(): String? = if (ready) auth().currentUser?.uid else null

    // --------------------------------------------------------------------------- user (app side)

    /** Creates or refreshes the user's document. Fields that are blank are not overwritten. */
    fun upsertUser(
        uid: String, name: String, email: String, provider: String,
        phone: String = "", country: String = "", createdAt: Long = 0,
        versionName: String, totalDownloads: Long = -1,
    ) {
        if (!ready) return
        val m = HashMap<String, Any>()
        m["uid"] = uid
        if (name.isNotBlank()) m["name"] = name
        if (email.isNotBlank()) m["email"] = email
        if (provider.isNotBlank()) m["provider"] = provider
        if (phone.isNotBlank()) m["phone"] = phone
        if (country.isNotBlank()) m["country"] = country
        if (createdAt > 0) m["createdAt"] = createdAt
        m["lastActive"] = System.currentTimeMillis()
        m["appVersion"] = versionName
        m["device"] = (Build.MANUFACTURER + " " + Build.MODEL).trim()
        m["androidVersion"] = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
        if (totalDownloads >= 0) m["totalDownloads"] = totalDownloads
        try {
            val ref = db().collection(USERS).document(uid)
            // "status" is only set when the document is created; users can never change it afterwards.
            ref.get().addOnSuccessListener { snap ->
                if (!snap.exists()) m["status"] = "active"
                ref.set(m, SetOptions.merge())
            }.addOnFailureListener {
                // Offline: queue the update; Firestore sends it later. The rules refuse any attempt to change an existing status.
                m["status"] = "active"
                ref.set(m, SetOptions.merge())
            }
        } catch (_: Throwable) { }
    }

    fun updateUserFields(uid: String, fields: Map<String, Any>) {
        if (!ready) return
        try {
            db().collection(USERS).document(uid).set(fields, SetOptions.merge())
        } catch (_: Throwable) { }
    }

    fun saveConsent(uid: String, version: Int) {
        updateUserFields(uid, mapOf("consentVersion" to version, "consentAt" to System.currentTimeMillis()))
    }

    suspend fun fetchUser(uid: String): CloudUser? = try {
        if (!ready) null else db().collection(USERS).document(uid).get().await().toUser()
    } catch (_: Throwable) {
        null
    }

    suspend fun fetchConfig(): AppConfig? = try {
        if (!ready) null else {
            val d = db().collection(CONFIG).document("app").get().await()
            if (!d.exists()) AppConfig() else AppConfig(
                maintenance = d.getBoolean("maintenance") ?: false,
                announcement = d.getString("announcement") ?: "",
                announcementId = d.getLong("announcementId") ?: 0,
                minVersionCode = d.getLong("minVersionCode") ?: 0,
            )
        }
    } catch (_: Throwable) {
        null
    }

    // --------------------------------------------------------------------------- events (app side)

    /** Fire-and-forget: Firestore keeps the write and retries it when the phone is online again. */
    fun logLink(e: LinkEvent) {
        if (!ready || e.uid.isBlank()) return
        try {
            val m = hashMapOf<String, Any>(
                "uid" to e.uid, "url" to e.url.take(600), "site" to e.site, "status" to e.status,
                "quality" to e.quality, "addedAt" to e.addedAt,
            )
            if (e.finishedAt > 0) m["finishedAt"] = e.finishedAt
            if (e.error.isNotBlank()) m["error"] = e.error.take(200)
            db().collection(LINKS).document(e.id).set(m, SetOptions.merge())
        } catch (_: Throwable) { }
    }

    fun logScreens(list: List<ScreenEvent>) {
        if (!ready || list.isEmpty()) return
        try {
            val db = db()
            list.chunked(400).forEach { part ->
                val b = db.batch()
                part.forEach { e ->
                    b.set(
                        db.collection(SCREENS).document(),
                        hashMapOf<String, Any>(
                            "uid" to e.uid, "screen" to e.screen, "startAt" to e.startAt, "durationMs" to e.durationMs,
                        ),
                    )
                }
                b.commit()
            }
        } catch (_: Throwable) { }
    }

    // --------------------------------------------------------------------------- admin side

    suspend fun isAdmin(uid: String): Boolean = try {
        db().collection(ADMINS).document(uid).get().await().exists()
    } catch (_: Throwable) {
        false
    }

    suspend fun fetchUsers(): List<CloudUser> =
        db().collection(USERS).limit(1000).get().await().documents.mapNotNull { it.toUser() }

    suspend fun fetchLinks(sinceMs: Long, limit: Long = 3000): List<LinkEvent> =
        db().collection(LINKS).whereGreaterThanOrEqualTo("addedAt", sinceMs)
            .orderBy("addedAt", Query.Direction.DESCENDING).limit(limit).get().await()
            .documents.map { it.toLink() }

    suspend fun fetchScreens(sinceMs: Long, limit: Long = 4000): List<ScreenEvent> =
        db().collection(SCREENS).whereGreaterThanOrEqualTo("startAt", sinceMs)
            .orderBy("startAt", Query.Direction.DESCENDING).limit(limit).get().await()
            .documents.map { it.toScreen() }

    suspend fun fetchUserLinks(uid: String): List<LinkEvent> =
        db().collection(LINKS).whereEqualTo("uid", uid).limit(400).get().await()
            .documents.map { it.toLink() }.sortedByDescending { it.addedAt }

    suspend fun fetchUserScreens(uid: String): List<ScreenEvent> =
        db().collection(SCREENS).whereEqualTo("uid", uid).limit(600).get().await()
            .documents.map { it.toScreen() }.sortedByDescending { it.startAt }

    suspend fun setUserStatus(uid: String, status: String) {
        db().collection(USERS).document(uid).update("status", status).await()
    }

    suspend fun saveConfig(c: AppConfig) {
        db().collection(CONFIG).document("app").set(
            hashMapOf<String, Any>(
                "maintenance" to c.maintenance, "announcement" to c.announcement,
                "announcementId" to c.announcementId, "minVersionCode" to c.minVersionCode,
            ),
        ).await()
    }

    // --------------------------------------------------------------------------- parsing

    private fun DocumentSnapshot.toUser(): CloudUser? {
        if (!exists()) return null
        return CloudUser(
            uid = id,
            name = getString("name") ?: "",
            email = getString("email") ?: "",
            phone = getString("phone") ?: "",
            country = getString("country") ?: "",
            provider = getString("provider") ?: "",
            createdAt = getLong("createdAt") ?: 0,
            lastActive = getLong("lastActive") ?: 0,
            appVersion = getString("appVersion") ?: "",
            device = getString("device") ?: "",
            androidVersion = getString("androidVersion") ?: "",
            totalDownloads = getLong("totalDownloads") ?: 0,
            status = getString("status") ?: "active",
            consentVersion = (getLong("consentVersion") ?: 0).toInt(),
            consentAt = getLong("consentAt") ?: 0,
        )
    }

    private fun DocumentSnapshot.toLink() = LinkEvent(
        id = id, uid = getString("uid") ?: "", url = getString("url") ?: "", site = getString("site") ?: "",
        status = getString("status") ?: "added", quality = getString("quality") ?: "",
        addedAt = getLong("addedAt") ?: 0, finishedAt = getLong("finishedAt") ?: 0, error = getString("error") ?: "",
    )

    private fun DocumentSnapshot.toScreen() = ScreenEvent(
        id = id, uid = getString("uid") ?: "", screen = getString("screen") ?: "",
        startAt = getLong("startAt") ?: 0, durationMs = getLong("durationMs") ?: 0,
    )
}
