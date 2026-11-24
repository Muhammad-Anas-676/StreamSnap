package com.anas.streamsnap.service

import com.anas.streamsnap.core.cloud.Cloud
import com.anas.streamsnap.session.Session
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives the admin's push (a data message) even when the app is closed or swiped away and shows it at once.
 * The same message also exists in Firestore; [PushDedupe] makes sure it becomes one notification only.
 */
class StreamSnapMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        try {
            val d = message.data
            if (d["type"] != "admin_message") return
            val uid = Cloud.uid() ?: return                      // signed out: nothing to show
            if (d["uid"] != uid || !Session.consented.value) return // meant for another account, or not consented yet
            val id = d["id"].orEmpty()
            val title = d["title"].orEmpty().take(200)
            val text = d["message"].orEmpty().take(1500)
            if (title.isBlank() && text.isBlank()) return
            if (PushDedupe.markIfNew(applicationContext, id)) Notifier.adminMessage(applicationContext, id, title, text)
        } catch (_: Throwable) { }
    }

    override fun onNewToken(token: String) {
        try {
            val uid = Cloud.uid() ?: return
            if (Session.consented.value) Cloud.saveFcmToken(uid, token)
        } catch (_: Throwable) { }
    }
}
