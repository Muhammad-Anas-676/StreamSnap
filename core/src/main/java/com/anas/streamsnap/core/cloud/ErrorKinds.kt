package com.anas.streamsnap.core.cloud

/** Log categories shown in the admin app. */
object ErrorKinds {
    const val DOWNLOAD = "Download Error"
    const val NETWORK = "Network Failure"
    const val ENGINE = "Engine Failure"
    const val STORAGE = "Storage Issue"
    const val CRASH = "App Crash"

    val all = listOf(DOWNLOAD, NETWORK, ENGINE, STORAGE, CRASH)

    /** Sorts a raw error text into one of the categories. */
    fun classify(text: String): String {
        val s = text.lowercase()
        return when {
            listOf("no space left", "enospc", "not enough space", "storage", "eacces", "permission denied", "read-only file system", "disk")
                .any { it in s } -> STORAGE
            listOf("unable to resolve host", "timed out", "timeout", "connection reset", "connection refused", "network is unreachable",
                "no internet", "ssl", "handshake", "unknownhost", "sockettimeout", "econnreset", "failed to connect", "connectexception")
                .any { it in s } -> NETWORK
            listOf("yt-dlp", "ytdl", "extractor", "unsupported url", "python", "ffmpeg", "youtubedl", "sign in to confirm", "player response", "nsig")
                .any { it in s } -> ENGINE
            else -> DOWNLOAD
        }
    }
}
