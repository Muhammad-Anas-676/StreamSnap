package com.anas.streamsnap.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.anas.streamsnap.data.tr
import com.anas.streamsnap.engine.QueueManager
import com.anas.streamsnap.util.LogStore

/** Keeps the page and the media links found, so switching tabs does not lose them. */
private object BrowserMemory {
    var lastUrl: String = ""
    val sniffed: MutableList<String> = ArrayList()
}

private val MEDIA_EXT = listOf(".m3u8", ".mpd", ".mp4", ".webm", ".m4v", ".mov")

private fun looksLikeMedia(url: String): Boolean {
    if (url.startsWith("blob:") || url.startsWith("data:")) return false
    if (url.contains("videoplayback")) return false // YouTube's signed pieces, not a usable link
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return MEDIA_EXT.any { path.endsWith(it) }
}

private fun toUrl(input: String): String {
    val s = input.trim()
    if (s.isEmpty()) return ""
    if (s.startsWith("http://", true) || s.startsWith("https://", true)) return s
    if (!s.contains(' ') && s.contains('.')) return "https://$s"
    return "https://www.google.com/search?q=" + Uri.encode(s)
}

private val QUICK_SITES = listOf(
    "YouTube" to "https://m.youtube.com",
    "Vimeo" to "https://vimeo.com",
    "Dailymotion" to "https://www.dailymotion.com",
    "Facebook" to "https://m.facebook.com",
    "Instagram" to "https://www.instagram.com",
    "TikTok" to "https://www.tiktok.com",
    "X" to "https://x.com",
    "Reddit" to "https://www.reddit.com",
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen() {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current

    var webView by remember { mutableStateOf<WebView?>(null) }
    var curUrl by remember { mutableStateOf(BrowserMemory.lastUrl) }
    var urlText by remember { mutableStateOf(BrowserMemory.lastUrl) }
    var editing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var showMedia by remember { mutableStateOf(false) }
    val sniffed = remember { mutableStateListOf<String>().also { it.addAll(BrowserMemory.sniffed) } }

    DisposableEffect(Unit) {
        onDispose {
            BrowserMemory.lastUrl = curUrl
            BrowserMemory.sniffed.clear()
            BrowserMemory.sniffed.addAll(sniffed)
            try { webView?.stopLoading(); webView?.destroy() } catch (_: Throwable) { }
            webView = null
        }
    }

    BackHandler(enabled = canBack) { webView?.goBack() }

    fun go(input: String) {
        val u = toUrl(input)
        if (u.isEmpty()) return
        focus.clearFocus()
        curUrl = u
        urlText = u
        webView?.loadUrl(u) ?: run { BrowserMemory.lastUrl = u }
    }

    fun addToQueue(link: String) {
        val r = QueueManager.addLinks(link)
        Toast.makeText(ctx, addResultMessage(r), Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { webView?.goBack() }, enabled = canBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "پیچھے"))
            }
            IconButton(onClick = { webView?.goForward() }, enabled = canForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = tr("Forward", "آگے"))
            }
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                modifier = Modifier.weight(1f).onFocusChanged { editing = it.isFocused },
                singleLine = true,
                placeholder = { Text(tr("Search or type a link", "تلاش کریں یا لنک لکھیں")) },
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go(urlText) }),
            )
            IconButton(onClick = { if (webView != null && curUrl.isNotEmpty()) webView?.reload() else go(urlText) }) {
                Icon(Icons.Filled.Refresh, contentDescription = tr("Reload", "ری لوڈ"))
            }
        }
        if (progress in 1..99) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    WebView(c).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val u = request.url.toString()
                                if (looksLikeMedia(u)) {
                                    view.post {
                                        if (!sniffed.contains(u)) {
                                            sniffed.add(u)
                                            if (sniffed.size > 40) sniffed.removeAt(0)
                                        }
                                    }
                                }
                                return null
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                curUrl = url
                                if (!editing) urlText = url
                                sniffed.clear()
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                canBack = view.canGoBack()
                                canForward = view.canGoForward()
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                curUrl = url
                                if (!editing) urlText = url
                                canBack = view.canGoBack()
                                canForward = view.canGoForward()
                            }

                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                // Stay inside the browser for web links; ignore app links (intent://, market://...).
                                val scheme = request.url.scheme ?: ""
                                return scheme != "http" && scheme != "https"
                            }

                            @Suppress("OVERRIDE_DEPRECATION")
                            override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                                LogStore.w("browser", "Page error $errorCode: ${description ?: ""}", url = failingUrl ?: "")
                            }
                        }
                        webView = this
                        val start = BrowserMemory.lastUrl
                        if (start.isNotEmpty()) loadUrl(start)
                    }
                },
            )

            if (curUrl.isEmpty()) {
                // Start page
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.VideoLibrary, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                    Text(tr("Browse and add videos", "براؤز کریں اور ویڈیوز شامل کریں"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr(
                            "Open a page with a video, then tap \"Add this page\". Direct video links found on the page are listed for you too.",
                            "ویڈیو والا صفحہ کھولیں، پھر \"Add this page\" دبائیں۔ صفحے پر ملنے والے براہِ راست ویڈیو لنک بھی دکھائے جاتے ہیں۔",
                        ),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QUICK_SITES.forEach { (name, url) -> AssistChip(onClick = { go(url) }, label = { Text(name) }) }
                    }
                }
            }

            if (curUrl.isNotEmpty()) {
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (sniffed.isNotEmpty()) {
                        ExtendedFloatingActionButton(
                            onClick = { showMedia = true },
                            icon = {
                                BadgedBox(badge = { Badge { Text("${sniffed.size}") } }) {
                                    Icon(Icons.Filled.VideoLibrary, contentDescription = null)
                                }
                            },
                            text = { Text(tr("Found", "ملے")) },
                        )
                    }
                    ExtendedFloatingActionButton(
                        onClick = { addToQueue(curUrl) },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(tr("Add this page", "یہ صفحہ شامل کریں")) },
                    )
                }
            }
        }
    }

    if (showMedia) {
        AlertDialog(
            onDismissRequest = { showMedia = false },
            title = { Text(tr("Video links on this page", "اس صفحے پر ویڈیو لنک")) },
            text = {
                Column(Modifier.padding(0.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sniffed.takeLast(12).asReversed().forEach { u ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                u, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = { addToQueue(u) }) { Text(tr("Add", "شامل")) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMedia = false }) { Text(tr("Close", "بند کریں")) } },
        )
    }
}
