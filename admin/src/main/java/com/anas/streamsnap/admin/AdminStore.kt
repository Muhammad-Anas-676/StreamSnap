package com.anas.streamsnap.admin

import com.anas.streamsnap.core.cloud.AppConfig
import com.anas.streamsnap.core.cloud.Cloud
import com.anas.streamsnap.core.cloud.CloudUser
import com.anas.streamsnap.core.cloud.LinkEvent
import com.anas.streamsnap.core.cloud.ScreenEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminData(
    val users: List<CloudUser> = emptyList(),
    val links: List<LinkEvent> = emptyList(),
    val screens: List<ScreenEvent> = emptyList(),
    val days: Int = 7,
    val loading: Boolean = false,
    val error: String = "",
    val loadedAt: Long = 0,
)

/** Holds what the dashboard shows. Data is read on demand (pull / refresh button) to stay inside the free Firestore limits. */
object AdminStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _data = MutableStateFlow(AdminData())
    val data: StateFlow<AdminData> = _data.asStateFlow()

    private var liveReg: com.google.firebase.firestore.ListenerRegistration? = null

    /** Keeps the user list (and so the online status) up to date while the admin app is open. */
    fun startLive() {
        if (liveReg != null) return
        liveReg = Cloud.listenUsers(
            { list -> _data.update { it.copy(users = list, error = "") } },
            { msg ->
                // Firestore ends a listener after an error: forget it so the next refresh can start a new one.
                stopLive()
                _data.update { it.copy(error = msg) }
            },
        )
    }

    /** Sign-out: stop listening and drop every piece of user data held in memory. */
    fun clear() {
        stopLive()
        _data.value = AdminData()
    }

    fun stopLive() {
        try { liveReg?.remove() } catch (_: Throwable) { }
        liveReg = null
    }

    fun setDays(d: Int) {
        if (_data.value.days == d) return
        _data.update { it.copy(days = d) }
        refresh()
    }

    fun refresh() {
        if (_data.value.loading) return
        _data.update { it.copy(loading = true, error = "") }
        scope.launch {
            try {
                val since = System.currentTimeMillis() - _data.value.days * 86_400_000L
                val u = async { Cloud.fetchUsers() }
                val l = async { Cloud.fetchLinks(since) }
                val s = async { Cloud.fetchScreens(since) }
                val users = u.await(); val links = l.await(); val screens = s.await()
                _data.update { it.copy(users = users, links = links, screens = screens, loading = false, loadedAt = System.currentTimeMillis()) }
                startLive() // restarts the live list if an earlier error had stopped it (no-op when it is running)
            } catch (e: Throwable) {
                _data.update { it.copy(loading = false, error = Cloud.describeError(e)) }
            }
        }
    }

    fun setBanned(uid: String, banned: Boolean, done: () -> Unit) {
        scope.launch {
            try {
                Cloud.setUserStatus(uid, if (banned) "banned" else "active")
                _data.update { d ->
                    d.copy(users = d.users.map { if (it.uid == uid) it.copy(status = if (banned) "banned" else "active") else it })
                }
            } catch (e: Throwable) {
                _data.update { it.copy(error = Cloud.describeError(e)) }
            }
            done()
        }
    }

    suspend fun loadConfig(): AppConfig = Cloud.fetchConfig() ?: AppConfig()
}
