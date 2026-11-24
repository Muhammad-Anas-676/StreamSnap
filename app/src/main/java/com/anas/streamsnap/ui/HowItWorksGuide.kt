package com.anas.streamsnap.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anas.streamsnap.data.Settings
import com.anas.streamsnap.data.Tr
import com.anas.streamsnap.data.tr3

/**
 * Tracks the floating "How it works" pill on the Queue screen: it is shown only during the very first session
 * on the Queue screen. Tapping it, or the first session ending (the app going to the background), marks it as seen,
 * so it never appears on the second open. The guide stays available at the top of Settings.
 */
object HowItWorksPill {
    private var decided = false
    private var shown by mutableStateOf(false)

    /** Called when the Queue screen is shown: true only during the very first session. */
    fun visible(): Boolean {
        if (!decided) {
            decided = true
            shown = !Settings.value.howItWorksSeen
        }
        return shown
    }

    /** The user opened the guide (from the pill or from Settings). */
    fun markSeen() {
        decided = true
        shown = false
        if (!Settings.value.howItWorksSeen) Settings.update { it.copy(howItWorksSeen = true) }
    }

    /** MainActivity.onStop: the first session is over. */
    fun onSessionEnd() {
        if (decided && shown) markSeen()
    }
}

/** The small floating pill, bottom-end of the Queue screen, just above the bottom bar. */
@Composable
fun HowItWorksPillButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, null) },
        text = { Text(tr3("How it works", "Kaise kaam karta hai", "کیسے کام کرتا ہے")) },
    )
}

private class G(val en: String, val ro: String, val ur: String) {
    fun t() = tr3(en, ro, ur)
}

private class GSection(val icon: ImageVector, val title: G, val points: List<G>)

private val sections = listOf(
    GSection(
        Icons.Filled.Download,
        G("1. Basics", "1. Bunyadi baatein", "۱۔ بنیادی باتیں"),
        listOf(
            G(
                "Paste one link or many (one per line) in the box at the top of the Queue screen, or tap Paste to take the link from your clipboard.",
                "Queue screen ke upar wale box mein aik ya kai links (har line mein aik) paste karein, ya Paste dabayein taake clipboard se link aa jaye.",
                "قطار اسکرین کے اوپر والے باکس میں ایک یا کئی لنک (ہر لائن میں ایک) پیسٹ کریں، یا Paste دبائیں تاکہ کلپ بورڈ سے لنک آ جائے۔",
            ),
            G(
                "Automatic download: a pasted link starts by itself. Manual download: press the download icon on each card. Change it in Settings > Download mode.",
                "Automatic download mein paste karte hi download shuru ho jata hai. Manual mein har card ka download icon dabayein. Settings > Download mode mein badal sakte hain.",
                "خودکار ڈاؤن لوڈ میں پیسٹ کرتے ہی ڈاؤن لوڈ شروع ہو جاتی ہے۔ دستی میں ہر کارڈ کا ڈاؤن لوڈ آئیکن دبائیں۔ Settings > Download mode میں بدل سکتے ہیں۔",
            ),
            G(
                "The Downloading tab shows running and waiting videos. The Downloaded tab shows finished videos: tap one to play it.",
                "Downloading tab mein chalti aur intezar wali videos hoti hain. Downloaded tab mein mukammal videos hoti hain: play karne ke liye dabayein.",
                "Downloading ٹیب میں چلتی اور انتظار والی ویڈیوز ہوتی ہیں۔ Downloaded ٹیب میں مکمل ویڈیوز ہوتی ہیں: چلانے کے لیے دبائیں۔",
            ),
            G(
                "Use Select to pick several items, then Pause all, Resume all or Remove them together.",
                "Select se kai items chunein, phir Pause all, Resume all ya Remove aik saath karein.",
                "Select سے کئی آئٹمز چنیں، پھر Pause all، Resume all یا Remove ایک ساتھ کریں۔",
            ),
        ),
    ),
    GSection(
        Icons.Filled.Tune,
        G("2. Quality and Custom Video", "2. Quality aur Custom Video", "۲۔ کوالٹی اور کسٹم ویڈیو"),
        listOf(
            G(
                "Settings > Video quality has three modes: one quality for all videos, I choose for each video, or Custom video. Only one mode is active at a time.",
                "Settings > Video quality mein teen modes hain: sab videos aik quality, har video ki alag, ya Custom video. Aik waqt mein sirf aik mode chalta hai.",
                "Settings > Video quality میں تین موڈ ہیں: سب ویڈیوز ایک کوالٹی، ہر ویڈیو کی الگ، یا کسٹم ویڈیو۔ ایک وقت میں صرف ایک موڈ چلتا ہے۔",
            ),
            G(
                "Per-video chips: when you choose for each video, every card shows quality chips. Tap one to start that video at that quality.",
                "Per-video chips: jab har video ki alag chunte hain to har card par quality chips aate hain. Aik dabayein to wo video usi quality mein shuru hogi.",
                "ہر ویڈیو کی الگ کوالٹی: اس موڈ میں ہر کارڈ پر کوالٹی چپس آتی ہیں۔ ایک دبائیں تو وہ ویڈیو اسی کوالٹی میں شروع ہوگی۔",
            ),
            G(
                "Custom video settings: resolution, frame rate (FPS), video codec (H.264, H.265 or MPEG4), rotation, flip and compress for a smaller file. Reset brings back the defaults.",
                "Custom video settings: resolution, frame rate (FPS), video codec (H.264, H.265 ya MPEG4), rotation, flip aur chhoti file ke liye compress. Reset se defaults wapas aate hain.",
                "کسٹم ویڈیو سیٹنگز: ریزولوشن، فریم ریٹ (FPS)، ویڈیو کوڈک (H.264، H.265 یا MPEG4)، روٹیشن، فلپ اور چھوٹی فائل کے لیے کمپریس۔ Reset سے ڈیفالٹ واپس آتے ہیں۔",
            ),
            G(
                "Audio only: choose MP3 (converted) or M4A (original sound, no conversion).",
                "Sirf audio: MP3 (convert hoti hai) ya M4A (asli awaaz, bagair conversion) chunein.",
                "صرف آڈیو: MP3 (تبدیل ہوتی ہے) یا M4A (اصلی آواز، بغیر تبدیلی) چنیں۔",
            ),
        ),
    ),
    GSection(
        Icons.Filled.PlaylistAdd,
        G("3. Playlists and Saved Links", "3. Playlists aur Saved Links", "۳۔ پلے لسٹ اور محفوظ لنکس"),
        listOf(
            G(
                "Paste a playlist link and a checklist opens. Tick the videos you want (All or None help) and tap Add.",
                "Playlist ka link paste karein to checklist khulti hai. Jo videos chahiyen un par tick karein (All ya None se madad lein) aur Add dabayein.",
                "پلے لسٹ کا لنک پیسٹ کریں تو چیک لسٹ کھلتی ہے۔ جو ویڈیوز چاہییں ان پر ٹک کریں (All یا None سے مدد لیں) اور Add دبائیں۔",
            ),
            G(
                "Save for later: bookmark a link without downloading it. Open Saved for later whenever you want to download those links.",
                "Save for later: link ko download kiye bagair bookmark karein. Jab chahein Saved for later kholkar un links ko download karein.",
                "Save for later: لنک کو ڈاؤن لوڈ کیے بغیر بک مارک کریں۔ جب چاہیں Saved for later کھول کر ان لنکس کو ڈاؤن لوڈ کریں۔",
            ),
        ),
    ),
    GSection(
        Icons.Filled.Language,
        G("4. Built-in Browser and Private Sites", "4. Built-in Browser aur Private Sites", "۴۔ بلٹ ان براؤزر اور نجی سائٹس"),
        listOf(
            G(
                "The Browser tab opens any site. When a page has videos, the Found list shows the video links on that page: tap Add to put one in the queue.",
                "Browser tab koi bhi site kholta hai. Page par videos hon to Found list mein unke links nazar aate hain: queue mein daalne ke liye Add dabayein.",
                "براؤزر ٹیب کوئی بھی سائٹ کھولتا ہے۔ صفحے پر ویڈیوز ہوں تو Found لسٹ میں ان کے لنک نظر آتے ہیں: قطار میں ڈالنے کے لیے Add دبائیں۔",
            ),
            G(
                "Sites that ask for a login (Instagram, Facebook and others): sign in inside the browser, then tap Save login in the bottom bar. Your downloads from that site then use the login.",
                "Jo sites login mangti hain (Instagram, Facebook waghera): browser ke andar sign in karein, phir neeche wali bar mein Save login dabayein. Us site ki downloads phir isi login se hongi.",
                "جو سائٹس لاگ اِن مانگتی ہیں (انسٹاگرام، فیس بک وغیرہ): براؤزر کے اندر سائن اِن کریں، پھر نیچے والی بار میں Save login دبائیں۔ اس سائٹ کی ڈاؤن لوڈز پھر اسی لاگ اِن سے ہوں گی۔",
            ),
        ),
    ),
    GSection(
        Icons.Filled.PlayCircle,
        G("5. Built-in Video Player", "5. Built-in Video Player", "۵۔ بلٹ ان ویڈیو پلیئر"),
        listOf(
            G(
                "Double-tap the left or right side to jump 10 seconds back or forward. Drag sideways to seek.",
                "Left ya right side par double-tap se 10 second peechay ya aagay jayein. Seek ke liye side mein drag karein.",
                "بائیں یا دائیں طرف ڈبل ٹیپ سے ۱۰ سیکنڈ پیچھے یا آگے جائیں۔ سیک کے لیے سائیڈ میں ڈریگ کریں۔",
            ),
            G(
                "Swipe up or down on the left side for brightness, and on the right side for volume (up to 200% boost).",
                "Left side par upar neeche swipe se brightness, right side par volume (200% tak boost).",
                "بائیں طرف اوپر نیچے سوائپ سے برائٹنس، اور دائیں طرف والیوم (۲۰۰٪ تک بوسٹ)۔",
            ),
            G(
                "Press and hold to play at 2x speed while you hold. Pinch to zoom the picture.",
                "Dabaye rakhein to jab tak dabaye hain 2x speed par chalegi. Pinch se tasveer zoom karein.",
                "دبائے رکھیں تو جب تک دبائے ہیں ۲ گنا رفتار سے چلے گی۔ پنچ سے تصویر زوم کریں۔",
            ),
            G(
                "Picture-in-Picture keeps a small window playing when you leave the app. The sleep timer stops playback after the time you pick.",
                "Picture-in-Picture se app chhorne par chhoti window chalti rehti hai. Sleep timer aap ke chune hue waqt ke baad playback rok deta hai.",
                "پکچر اِن پکچر سے ایپ چھوڑنے پر چھوٹی ونڈو چلتی رہتی ہے۔ سلیپ ٹائمر آپ کے چنے ہوئے وقت کے بعد پلے بیک روک دیتا ہے۔",
            ),
            G(
                "Subtitles: a .srt file next to the video is found by itself, or you can pick a .srt file from the player.",
                "Subtitles: video ke saath wali .srt file khud mil jati hai, ya player se .srt file chun sakte hain.",
                "سب ٹائٹلز: ویڈیو کے ساتھ والی .srt فائل خود مل جاتی ہے، یا پلیئر سے .srt فائل چن سکتے ہیں۔",
            ),
        ),
    ),
    GSection(
        Icons.Filled.Lock,
        G("6. Background, Network and Privacy", "6. Background, Network aur Privacy", "۶۔ بیک گراؤنڈ، نیٹ ورک اور پرائیویسی"),
        listOf(
            G(
                "Wi-Fi only makes downloads wait while you are on mobile data. Parallel downloads sets how many videos download at once. Speed limit slows downloads so your internet stays free.",
                "Wi-Fi only se mobile data par downloads intezar karti hain. Parallel downloads tay karta hai ke aik waqt mein kitni videos download hon. Speed limit se internet doosre kaam ke liye free rehta hai.",
                "Wi-Fi only سے موبائل ڈیٹا پر ڈاؤن لوڈز انتظار کرتی ہیں۔ Parallel downloads طے کرتا ہے کہ ایک وقت میں کتنی ویڈیوز ڈاؤن لوڈ ہوں۔ Speed limit سے انٹرنیٹ دوسرے کام کے لیے فری رہتا ہے۔",
            ),
            G(
                "Scheduled download starts waiting downloads at the start time you set. Auto-retry tries failed downloads again, as many times as you choose.",
                "Scheduled download intezar wali downloads aap ke tay kiye waqt par shuru karta hai. Auto-retry nakam downloads ko dobara try karta hai, jitni baar aap chunein.",
                "Scheduled download انتظار والی ڈاؤن لوڈز آپ کے طے کیے وقت پر شروع کرتا ہے۔ Auto-retry ناکام ڈاؤن لوڈز کو دوبارہ آزماتا ہے، جتنی بار آپ چنیں۔",
            ),
            G(
                "Site-wise folders save videos in one folder per site. The Hidden folder (.nomedia) keeps videos out of Gallery and other apps.",
                "Site-wise folders har site ka alag folder banate hain. Hidden folder (.nomedia) videos ko Gallery aur doosri apps se chhupata hai.",
                "Site-wise folders ہر سائٹ کا الگ فولڈر بناتے ہیں۔ Hidden folder (.nomedia) ویڈیوز کو گیلری اور دوسری ایپس سے چھپاتا ہے۔",
            ),
            G(
                "App lock (PIN) asks for a 4-digit PIN when the app opens. Backup and restore saves your settings and history in one small file (the PIN and logins are not included).",
                "App lock (PIN) app kholne par 4 digit PIN mangta hai. Backup and restore aap ki settings aur history aik chhoti file mein mehfooz karta hai (PIN aur logins shamil nahi).",
                "App lock (PIN) ایپ کھولنے پر ۴ ہندسوں کا PIN مانگتا ہے۔ Backup and restore آپ کی سیٹنگز اور ہسٹری ایک چھوٹی فائل میں محفوظ کرتا ہے (PIN اور لاگ اِن شامل نہیں)۔",
            ),
        ),
    ),
)

/** Full-screen guide, from basic to advanced. Opened from the top of Settings or from the Queue-screen pill. */
@Composable
fun HowItWorksGuide(onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dir = if (Tr.isUrdu) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides dir) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = tr3("Back", "Wapas", "واپس"),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                tr3("How StreamSnap works", "StreamSnap kaise kaam karta hai", "اسٹریم اسنیپ کیسے کام کرتا ہے"),
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                tr3("Basic to advanced", "Bunyadi se advanced tak", "بنیادی سے ایڈوانس تک"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(sections) { _, s -> SectionCard(s) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(s: GSection) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(s.icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                Text(
                    s.title.t(), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            s.points.forEach { p ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(p.t(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
