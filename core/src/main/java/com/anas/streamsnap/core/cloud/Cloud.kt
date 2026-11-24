package com.anas.streamsnap.core.cloud

import android.content.Context
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
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
    const val ERRORS = "events_errors"

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

    /** Turns a Firebase failure into a short message that says what to do (shown in the admin app). */
    fun describeError(t: Throwable?): String {
        val raw = t?.message.orEmpty()
        return when ((t as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Permission denied. Publish the latest firestore.rules and make sure this account has a document in the admins collection."
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> "You are signed out. Sign in again."
            FirebaseFirestoreException.Code.UNAVAILABLE -> "Cannot reach Firebase. Check the internet connection and try again."
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "Firebase took too long to answer. Try again."
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> "The free Firebase quota for today is used up. It resets daily."
            FirebaseFirestoreException.Code.FAILED_PRECONDITION -> "Firestore needs an index for this query. $raw"
            else -> raw.ifBlank { "Something went wrong" }
        }
    }

    fun auth(): FirebaseAuth = FirebaseAuth.getInstance()
    fun db(): FirebaseFirestore = FirebaseFirestore.getInstance()
    fun uid(): String? = if (ready) auth().currentUser?.uid else null

    // --------------------------------------------------------------------------- user (app side)

    // --------------------------------------------------------------------------- write ordering
    //
    // The Firestore rules need `users/{uid}` to exist (status "active") before any other write of that user is
    // accepted (notBanned() reads that document). Writes of one client reach the server in the order they were
    // issued, so every other write waits here until the create / merge of the user document has been issued.

    private val gateLock = Any()
    private var readyUid: String? = null
    private var ensuringUid: String? = null
    private val waiting = ArrayList<Pair<String, () -> Unit>>()

    /** Runs [block] once the user document of [uid] is created / merged. Starts a minimal create by itself if nobody did. */
    private fun afterUser(uid: String, block: () -> Unit) {
        var runNow = false
        var startEnsure = false
        synchronized(gateLock) {
            if (readyUid == uid) {
                runNow = true
            } else {
                if (waiting.size >= 200) waiting.removeAt(0) // never grow without limit while offline
                waiting.add(uid to block)
                if (ensuringUid != uid) { ensuringUid = uid; startEnsure = true }
            }
        }
        if (runNow) {
            try { block() } catch (_: Throwable) { }
        } else if (startEnsure) {
            createOrMerge(uid, hashMapOf<String, Any>("uid" to uid, "lastActive" to System.currentTimeMillis()))
        }
    }

    private fun markReady(uid: String) {
        val run: List<() -> Unit>
        synchronized(gateLock) {
            readyUid = uid
            ensuringUid = null
            run = waiting.filter { it.first == uid }.map { it.second }
            waiting.clear() // anything queued for another account is dropped
        }
        run.forEach { try { it() } catch (_: Throwable) { } }
    }

    /** Forget the ready state (sign-out or another account): the next write starts the create / merge again. */
    fun resetUserGate() {
        synchronized(gateLock) {
            readyUid = null
            ensuringUid = null
            waiting.clear()
        }
    }

    /**
     * Creates the user document (status "active") when it is missing, otherwise merges [fields] into it.
     * The write is issued first; only then the queued writes are released (see [afterUser]).
     */
    private fun createOrMerge(uid: String, fields: HashMap<String, Any>) {
        try {
            val ref = db().collection(USERS).document(uid)
            fun write(isNew: Boolean) {
                // "status" is only set when the document is created; the rules refuse any change of it afterwards.
                if (isNew) fields["status"] = "active"
                try { ref.set(fields, SetOptions.merge()) } catch (_: Throwable) { }
                markReady(uid)
            }
            ref.get().addOnSuccessListener { snap -> write(!snap.exists()) }
                .addOnFailureListener {
                    // Offline: Firestore queues the write and sends it later. If the document already exists the rules
                    // reject only an attempt to change its status, so an existing (or banned) user can never be reset.
                    write(true)
                }
        } catch (_: Throwable) {
            synchronized(gateLock) { if (ensuringUid == uid) ensuringUid = null }
        }
    }

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
        synchronized(gateLock) { if (readyUid != uid) ensuringUid = uid }
        createOrMerge(uid, m)
    }

    fun updateUserFields(uid: String, fields: Map<String, Any>) {
        if (!ready) return
        afterUser(uid) {
            try { db().collection(USERS).document(uid).set(fields, SetOptions.merge()) } catch (_: Throwable) { }
        }
    }

    fun saveConsent(uid: String, version: Int) {
        updateUserFields(uid, mapOf("consentVersion" to version, "consentAt" to System.currentTimeMillis()))
    }

    /** Success(null) = the user has no document yet; failure = could not be read (offline), so nothing may be assumed. */
    suspend fun fetchUserResult(uid: String): Result<CloudUser?> = try {
        if (!ready) Result.success(null) else Result.success(db().collection(USERS).document(uid).get().await().toUser())
    } catch (t: Throwable) {
        Result.failure(t)
    }

    suspend fun fetchUser(uid: String): CloudUser? = fetchUserResult(uid).getOrNull()

    suspend fun fetchConfig(): AppConfig? = try {
        if (!ready) null else {
            val d = db().collection(CONFIG).document("app").get().await()
            if (!d.exists()) AppConfig() else d.toConfig()
        }
    } catch (_: Throwable) {
        null
    }

    // --------------------------------------------------------------------------- live data (app side)

    /** Live copy of `config/app`: banner, maintenance and minimum version change without restarting the app. */
    fun listenConfig(cb: (AppConfig) -> Unit): ListenerRegistration? = try {
        if (!ready) null else db().collection(CONFIG).document("app").addSnapshotListener { d, _ ->
            if (d != null) cb(if (d.exists()) d.toConfig() else AppConfig())
        }
    } catch (_: Throwable) {
        null
    }

    /** Live copy of the user's own document (a ban applies immediately). */
    fun listenUserDoc(uid: String, cb: (CloudUser?) -> Unit): ListenerRegistration? = try {
        if (!ready) null else db().collection(USERS).document(uid).addSnapshotListener { d, _ ->
            if (d != null) cb(d.toUser())
        }
    } catch (_: Throwable) {
        null
    }

    /** Messages sent by the admin that were not shown yet. */
    fun listenNotifications(uid: String, cb: (List<AdminNotice>) -> Unit): ListenerRegistration? = try {
        if (!ready) null else db().collection(USERS).document(uid).collection("notifications")
            .whereEqualTo("delivered", false)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    cb(
                        snap.documents.map {
                            AdminNotice(
                                id = it.id, title = it.getString("title") ?: "", message = it.getString("message") ?: "",
                                createdAt = it.getLong("createdAt") ?: 0,
                            )
                        }.sortedBy { it.createdAt },
                    )
                }
            }
    } catch (_: Throwable) {
        null
    }

    fun markNoticeDelivered(uid: String, id: String) {
        if (!ready) return
        afterUser(uid) {
            try {
                db().collection(USERS).document(uid).collection("notifications").document(id)
                    .update(mapOf("delivered" to true, "deliveredAt" to System.currentTimeMillis()))
            } catch (_: Throwable) { }
        }
    }

    /** Presence: online / offline, last heartbeat and what the user is doing. */
    fun updatePresence(uid: String, online: Boolean, activity: String) {
        if (!ready) return
        afterUser(uid) {
            try {
                db().collection(USERS).document(uid).set(
                    hashMapOf<String, Any>("isOnline" to online, "lastActive" to System.currentTimeMillis(), "currentActivity" to activity.take(80)),
                    SetOptions.merge(),
                )
            } catch (_: Throwable) { }
        }
    }

    /** Reports an error that is not a failed download (for example an app crash). */
    fun logError(uid: String, type: String, message: String, detail: String) {
        if (!ready || uid.isBlank()) return
        afterUser(uid) {
            try {
                db().collection(ERRORS).document().set(
                    hashMapOf<String, Any>(
                        "uid" to uid, "type" to type, "message" to message.take(300),
                        "detail" to detail.take(6000), "at" to System.currentTimeMillis(),
                    ),
                )
            } catch (_: Throwable) { }
        }
    }

    // --------------------------------------------------------------------------- events (app side)

    /** Fire-and-forget: Firestore keeps the write and retries it when the phone is online again. */
    fun logLink(e: LinkEvent) {
        if (!ready || e.uid.isBlank()) return
        afterUser(e.uid) {
            try {
                val m = hashMapOf<String, Any>(
                    "uid" to e.uid, "url" to e.url.take(600), "site" to e.site.take(80), "status" to e.status,
                    "quality" to e.quality.take(40), "addedAt" to e.addedAt,
                )
                if (e.finishedAt > 0) m["finishedAt"] = e.finishedAt
                if (e.error.isNotBlank()) {
                    m["error"] = e.error.take(1500)
                    m["errorType"] = e.errorType.ifBlank { ErrorKinds.classify(e.error) }
                }
                db().collection(LINKS).document(e.id).set(m, SetOptions.merge())
            } catch (_: Throwable) { }
        }
    }

    fun logScreens(list: List<ScreenEvent>) {
        if (!ready || list.isEmpty()) return
        val uid = list.first().uid
        if (uid.isBlank()) return
        val mine = list.filter { it.uid == uid } // one batch is one account (the rules check uid on every document)
        afterUser(uid) {
            try {
                val db = db()
                mine.chunked(400).forEach { part ->
                    val b = db.batch()
                    part.forEach { e ->
                        b.set(
                            db.collection(SCREENS).document(),
                            hashMapOf<String, Any>(
                                "uid" to e.uid, "screen" to e.screen.take(40), "startAt" to e.startAt, "durationMs" to e.durationMs,
                            ),
                        )
                    }
                    b.commit()
                }
            } catch (_: Throwable) { }
        }
    }

    // --------------------------------------------------------------------------- admin side

    suspend fun isAdmin(uid: String): Boolean = try {
        db().collection(ADMINS).document(uid).get().await().exists()
    } catch (_: Throwable) {
        false
    }

    suspend fun fetchUsers(): List<CloudUser> =
        db().collection(USERS).limit(1000).get().await().documents.mapNotNull { it.toUser() }

    /** Live list of users (presence updates arrive by themselves). */
    fun listenUsers(cb: (List<CloudUser>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        db().collection(USERS).limit(1000).addSnapshotListener { snap, e ->
            if (e != null) onError(describeError(e))
            if (snap != null) cb(snap.documents.mapNotNull { it.toUser() })
        }

    suspend fun sendNotification(uid: String, title: String, message: String) {
        db().collection(USERS).document(uid).collection("notifications").add(
            hashMapOf<String, Any>(
                "title" to title.trim(), "message" to message.trim(), "createdAt" to System.currentTimeMillis(),
                "delivered" to false, "from" to "StreamSnap Team",
            ),
        ).await()
    }

    suspend fun fetchUserErrors(uid: String): List<ErrorEvent> =
        db().collection(ERRORS).whereEqualTo("uid", uid).limit(300).get().await().documents.map {
            ErrorEvent(
                id = it.id, uid = it.getString("uid") ?: "", type = it.getString("type") ?: ErrorKinds.CRASH,
                message = it.getString("message") ?: "", detail = it.getString("detail") ?: "", at = it.getLong("at") ?: 0,
            )
        }.sortedByDescending { it.at }

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
                "announcementColor" to c.announcementColor,
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
            isOnline = getBoolean("isOnline") ?: false,
            currentActivity = getString("currentActivity") ?: "",
        )
    }

    private fun DocumentSnapshot.toConfig() = AppConfig(
        maintenance = getBoolean("maintenance") ?: false,
        announcement = getString("announcement") ?: "",
        announcementId = getLong("announcementId") ?: 0,
        minVersionCode = getLong("minVersionCode") ?: 0,
        announcementColor = getString("announcementColor") ?: "blue",
    )

    private fun DocumentSnapshot.toLink() = LinkEvent(
        id = id, uid = getString("uid") ?: "", url = getString("url") ?: "", site = getString("site") ?: "",
        status = getString("status") ?: "added", quality = getString("quality") ?: "",
        addedAt = getLong("addedAt") ?: 0, finishedAt = getLong("finishedAt") ?: 0, error = getString("error") ?: "",
        errorType = getString("errorType") ?: "",
    )

    private fun DocumentSnapshot.toScreen() = ScreenEvent(
        id = id, uid = getString("uid") ?: "", screen = getString("screen") ?: "",
        startAt = getLong("startAt") ?: 0, durationMs = getLong("durationMs") ?: 0,
    )
}
