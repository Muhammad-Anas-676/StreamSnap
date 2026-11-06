package com.anas.streamsnap.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.anas.streamsnap.data.Status
import com.anas.streamsnap.data.tr
import com.anas.streamsnap.engine.QueueManager
import com.anas.streamsnap.util.CookieBridge
import com.anas.streamsnap.util.LogStore
import com.anas.streamsnap.util.Utils

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

private data class Site(val name: String, val url: String, val domain: String, val brand: Color)

private val SITES = listOf(
    Site("YouTube", "https://m.youtube.com", "youtube.com", Color(0xFFFF0000)),
    Site("Instagram", "https://www.instagram.com", "instagram.com", Color(0xFFC13584)),
    Site("Facebook", "https://m.facebook.com", "facebook.com", Color(0xFF1877F2)),
    Site("TikTok", "https://www.tiktok.com", "tiktok.com", Color(0xFF2B2B2B)),
    Site("X", "https://x.com", "x.com", Color(0xFF2B2B2B)),
    Site("Vimeo", "https://vimeo.com", "vimeo.com", Color(0xFF1AB7EA)),
    Site("Dailymotion", "https://www.dailymotion.com", "dailymotion.com", Color(0xFF0066DC)),
    Site("Reddit", "https://www.reddit.com", "reddit.com", Color(0xFFFF4500)),
    Site("Twitch", "https://www.twitch.tv", "twitch.tv", Color(0xFF9146FF)),
    Site("Pinterest", "https://www.pinterest.com", "pinterest.com", Color(0xFFE60023)),
    Site("SoundCloud", "https://soundcloud.com", "soundcloud.com", Color(0xFFFF5500)),
    Site("Bilibili", "https://www.bilibili.com", "bilibili.com", Color(0xFF00A1D6)),
)

/** Retries the failed cards of a site right after its login was saved. */
private fun retryFailedFor(baseDomain: String): Int {
    val failed = QueueManager.items.value.filter {
        it.status == Status.FAILED && CookieBridge.baseDomain(Utils.hostOf(it.url)) == baseDomain
    }
    failed.forEach { QueueManager.retry(it.id) }
    return failed.size
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen() {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val bgColor = MaterialTheme.colorScheme.background

    var webView by remember { mutableStateOf<WebView?>(null) }
    var showWeb by remember { mutableStateOf(BrowserMemory.lastUrl.isNotEmpty()) }
    var pendingUrl by remember { mutableStateOf("") }
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
            BrowserMemory.lastUrl = if (showWeb) curUrl else ""
            BrowserMemory.sniffed.clear()
            BrowserMemory.sniffed.addAll(sniffed)
        }
    }

    fun goHome() {
        focus.clearFocus()
        try { webView?.stopLoading() } catch (_: Throwable) { }
        showWeb = false
        curUrl = ""
        urlText = ""
        progress = 0
        canBack = false
        canForward = false
        sniffed.clear()
        BrowserMemory.lastUrl = ""
    }

    BackHandler(enabled = showWeb) {
        if (canBack) webView?.goBack() else goHome()
    }

    fun go(input: String) {
        val u = toUrl(input)
        if (u.isEmpty()) return
        focus.clearFocus()
        curUrl = u
        urlText = u
        if (showWeb && webView != null) {
            webView?.loadUrl(u)
        } else {
            pendingUrl = u
            showWeb = true
        }
    }

    fun addToQueue(link: String) {
        val r = QueueManager.addLinks(link)
        Toast.makeText(ctx, addResultMessage(r), Toast.LENGTH_SHORT).show()
    }

    fun saveLogin() {
        val r = CookieBridge.saveFromWebView(ctx, curUrl)
        r.onSuccess { (domain, n) ->
            val retried = retryFailedFor(domain)
            val msg = tr("Login saved for $domain ($n cookies).", "$domain کا لاگ اِن محفوظ ہو گیا ($n کوکیز)۔") +
                if (retried > 0) tr(" Retrying $retried failed link(s).", " $retried ناکام لنک دوبارہ آزمائے جا رہے ہیں۔") else ""
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }.onFailure {
            Toast.makeText(ctx, it.message ?: tr("Could not save the login", "لاگ اِن محفوظ نہیں ہو سکا"), Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize().background(bgColor).statusBarsPadding()) {
        // ---- address pill
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (showWeb && curUrl.startsWith("https")) Icons.Filled.Lock else Icons.Filled.Search,
                    contentDescription = null, modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    modifier = Modifier.weight(1f).onFocusChanged { editing = it.isFocused },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go(urlText) }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (urlText.isEmpty()) {
                                Text(
                                    tr("Search or paste a link", "تلاش کریں یا لنک پیسٹ کریں"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            inner()
                        }
                    },
                )
                when {
                    editing && urlText.isNotEmpty() -> IconButton(onClick = { urlText = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = tr("Clear", "صاف کریں"))
                    }
                    showWeb -> IconButton(onClick = { if (progress in 1..99) webView?.stopLoading() else webView?.reload() }) {
                        Icon(
                            if (progress in 1..99) Icons.Filled.Close else Icons.Filled.Refresh,
                            contentDescription = tr("Reload", "ری لوڈ"),
                        )
                    }
                    urlText.isNotEmpty() -> IconButton(onClick = { go(urlText) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = tr("Go", "جائیں"))
                    }
                    else -> Spacer(Modifier.width(12.dp))
                }
            }
        }

        // ---- toolbar (only while a page is open)
        if (showWeb) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { webView?.goBack() }, enabled = canBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "پیچھے"))
                }
                IconButton(onClick = { webView?.goForward() }, enabled = canForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = tr("Forward", "آگے"))
                }
                IconButton(onClick = { goHome() }) {
                    Icon(Icons.Filled.Home, contentDescription = tr("Browser home", "براؤزر ہوم"))
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(
                    onClick = { saveLogin() },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                ) {
                    Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Save login", "لاگ اِن محفوظ کریں"))
                }
            }
        }
        if (showWeb && progress in 1..99) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
        } else {
            Spacer(Modifier.height(2.dp))
        }

        // ---- content
        Box(Modifier.weight(1f).fillMaxWidth().background(bgColor)) {
            if (!showWeb) {
                StartPage(onSite = { go(it) }, modifier = Modifier.fillMaxSize())
            } else {
                val webBg = bgColor.toArgb()
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        WebView(c).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            setBackgroundColor(webBg)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            // Look like a normal Chrome: some sites refuse to log in inside a "WebView".
                            try {
                                settings.userAgentString = settings.userAgentString
                                    .replace("; wv)", ")")
                                    .replace(Regex(" Version/\\d+\\.\\d+"), "")
                            } catch (_: Throwable) { }
                            CookieManager.getInstance().also { cm ->
                                cm.setAcceptCookie(true)
                                try { cm.setAcceptThirdPartyCookies(this, true) } catch (_: Throwable) { }
                            }
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
                                    // After the first page the normal white page background is right again
                                    // (a transparent page with dark text would be unreadable on dark).
                                    view.setBackgroundColor(android.graphics.Color.WHITE)
                                    try { CookieManager.getInstance().flush() } catch (_: Throwable) { }
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
                            val start = pendingUrl.ifEmpty { BrowserMemory.lastUrl }
                            pendingUrl = ""
                            if (start.isNotEmpty()) loadUrl(start)
                        }
                    },
                )
                DisposableEffect(Unit) {
                    onDispose {
                        try { webView?.stopLoading(); webView?.destroy() } catch (_: Throwable) { }
                        webView = null
                    }
                }

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

// ------------------------------------------------------------------ start page

@Composable
private fun StartPage(onSite: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // hero
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                    ),
                ),
            ).padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        tr("Browse and add videos", "براؤز کریں اور ویڈیوز شامل کریں"),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        tr(
                            "Open a site, find a video, then tap \"Add this page\".",
                            "کوئی سائٹ کھولیں، ویڈیو ڈھونڈیں، پھر \"Add this page\" دبائیں۔",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
        }

        SectionTitle(tr("Popular sites", "مقبول سائٹس"), Modifier.padding(top = 0.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                SITES.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { s -> SiteTile(s, Modifier.weight(1f)) { onSite(s.url) } }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        // login tip
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        tr("Sites that ask for a login", "جو سائٹس لاگ اِن مانگتی ہیں"),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        tr(
                            "Open the site here and sign in, then tap \"Save login\". After that, links from that site (for example Instagram) can be downloaded.",
                            "سائٹ یہاں کھول کر لاگ اِن کریں، پھر \"Save login\" دبائیں۔ اس کے بعد اس سائٹ (مثلاً Instagram) کے لنک ڈاؤن لوڈ ہو سکیں گے۔",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SiteTile(site: Site, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "tile")
    Column(
        modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(site.brand)) {
            // The real site icon is loaded from the web; until then (or offline) a coloured letter is shown.
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data("https://www.google.com/s2/favicons?domain=${site.domain}&sz=128")
                    .crossfade(true)
                    .build(),
                contentDescription = site.name,
                modifier = Modifier.fillMaxSize(),
                loading = { SiteLetter(site) },
                error = { SiteLetter(site) },
                success = {
                    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                        SubcomposeAsyncImageContent(Modifier.size(34.dp))
                    }
                },
            )
        }
        Text(
            site.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SiteLetter(site: Site) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(site.name.take(1), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
