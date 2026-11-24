package com.anas.streamsnap.core.cloud

/** One user, as stored in Firestore `users/{uid}`. All times are epoch milliseconds. */
data class CloudUser(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val country: String = "",
    val provider: String = "",
    val createdAt: Long = 0,
    val lastActive: Long = 0,
    val appVersion: String = "",
    val device: String = "",
    val androidVersion: String = "",
    val totalDownloads: Long = 0,
    val status: String = "active", // "active" or "banned"
    val consentVersion: Int = 0,
    val consentAt: Long = 0,
    val isOnline: Boolean = false,
    val currentActivity: String = "",
) {
    val banned: Boolean get() = status == "banned"

    /** Online means the app said so and its last heartbeat is recent (protects against an app that was killed). */
    fun onlineNow(now: Long = System.currentTimeMillis()): Boolean = isOnline && now - lastActive < 5 * 60_000L
}

/** A link the user added, `events_links/{uid}_{itemId}`. */
data class LinkEvent(
    val id: String = "",
    val uid: String = "",
    val url: String = "",
    val site: String = "",
    val status: String = "added", // added | done | failed
    val quality: String = "",
    val addedAt: Long = 0,
    val finishedAt: Long = 0,
    val error: String = "",
    val errorType: String = "",
)

/** One visit to a screen: when it started and how long the user stayed, `events_screens/{auto id}`. */
data class ScreenEvent(
    val id: String = "",
    val uid: String = "",
    val screen: String = "",
    val startAt: Long = 0,
    val durationMs: Long = 0,
)

/** Remote switches, `config/app`. Written by the admin app, read by the user app. */
data class AppConfig(
    val maintenance: Boolean = false,
    val announcement: String = "",
    val announcementId: Long = 0,
    val minVersionCode: Long = 0,
    val announcementColor: String = "blue",
)

/** A message the admin sent to one user, `users/{uid}/notifications/{id}`. */
data class AdminNotice(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val createdAt: Long = 0,
)

/** An error the app reported (crashes and similar), `events_errors/{auto id}`. */
data class ErrorEvent(
    val id: String = "",
    val uid: String = "",
    val type: String = "",
    val message: String = "",
    val detail: String = "",
    val at: Long = 0,
)
