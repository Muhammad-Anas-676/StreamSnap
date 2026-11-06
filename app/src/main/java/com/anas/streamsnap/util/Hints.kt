package com.anas.streamsnap.util

import com.anas.streamsnap.data.tr

enum class HintAction { BROWSER, SETTINGS }

data class ErrorHint(val text: String, val action: HintAction? = null)

/** Plain-language help shown under a failed card. The exact error stays in the Log. */
object Hints {
    fun forError(error: String, url: String): ErrorHint? {
        if (error.isBlank()) return null
        val e = error.lowercase()
        val h = Utils.hostOf(url).lowercase()
        val yt = h == "youtu.be" || h.endsWith("youtube.com") || h.endsWith("youtube-nocookie.com")

        if (yt && (e.contains("sign in to confirm") || e.contains("not a bot"))) {
            return ErrorHint(
                tr(
                    "YouTube wants proof you are not a bot. Update the engine, then retry. If it still asks, try later or on another network.",
                    "یوٹیوب تصدیق مانگ رہا ہے۔ انجن اپڈیٹ کریں اور دوبارہ کوشش کریں۔ پھر بھی آئے تو بعد میں یا دوسرے نیٹ ورک پر کوشش کریں۔",
                ),
                HintAction.SETTINGS,
            )
        }
        if (e.contains("login required") || e.contains("rate-limit reached") || e.contains("--cookies") ||
            e.contains("log in") || (!yt && e.contains("cookies"))
        ) {
            return ErrorHint(
                tr(
                    "This site needs your login. Open Browser, sign in to the site, tap \"Save login\", then retry here.",
                    "اس سائٹ کو آپ کا لاگ اِن چاہیے۔ براؤزر کھولیں، سائٹ میں لاگ اِن کریں، \"Save login\" دبائیں، پھر یہاں دوبارہ کوشش کریں۔",
                ),
                HintAction.BROWSER,
            )
        }
        if (yt && (e.contains("403") || e.contains("sabr") || e.contains("format is not available") ||
                e.contains("unable to download video data") || e.contains("po token"))
        ) {
            return ErrorHint(
                tr(
                    "YouTube blocked this download (the app already tried other methods). Update the engine: Settings > Download engine > Update, then retry.",
                    "یوٹیوب نے یہ ڈاؤن لوڈ روک دیا (ایپ دوسرے طریقے بھی آزما چکی ہے)۔ انجن اپڈیٹ کریں: سیٹنگز > ڈاؤن لوڈ انجن > اپڈیٹ، پھر دوبارہ کوشش کریں۔",
                ),
                HintAction.SETTINGS,
            )
        }
        if (e.contains("http error 429") || e.contains("too many requests")) {
            return ErrorHint(
                tr(
                    "Too many requests. Wait a few minutes, then retry.",
                    "بہت زیادہ درخواستیں۔ چند منٹ انتظار کریں، پھر دوبارہ کوشش کریں۔",
                ),
            )
        }
        if (e.contains("reset by peer") || e.contains("errno 104") || e.contains("timed out") ||
            e.contains("transporterror") || e.contains("closed file") || e.contains("unable to download webpage") ||
            e.contains("network is unreachable") || e.contains("ssl") || e.contains("remote end closed") ||
            e.contains("name or service not known") || e.contains("http error 403")
        ) {
            return ErrorHint(
                tr(
                    "Your network (or the site) is cutting this connection. Turn your VPN on, or switch the VPN server, then retry.",
                    "آپ کا نیٹ ورک (یا سائٹ) کنکشن کاٹ رہی ہے۔ VPN آن کریں یا VPN سرور بدلیں، پھر دوبارہ کوشش کریں۔",
                ),
            )
        }
        if (e.contains("unsupported url")) {
            return ErrorHint(
                tr(
                    "This link is not a video page. Open the video itself and copy its link.",
                    "یہ لنک ویڈیو کا صفحہ نہیں ہے۔ ویڈیو خود کھول کر اس کا لنک کاپی کریں۔",
                ),
            )
        }
        if (e.contains("private video") || e.contains("video unavailable") || e.contains("has been removed") ||
            e.contains("http error 404") || e.contains("does not exist") || e.contains("removed by the uploader")
        ) {
            return ErrorHint(
                tr(
                    "The video is private, removed or not available in your country.",
                    "ویڈیو پرائیویٹ ہے، ہٹا دی گئی ہے یا آپ کے ملک میں دستیاب نہیں۔",
                ),
            )
        }
        if (e.contains("no space left")) {
            return ErrorHint(tr("The phone storage is full. Free some space and retry.", "فون کی اسٹوریج بھر گئی ہے۔ جگہ خالی کریں اور دوبارہ کوشش کریں۔"))
        }
        return null
    }
}
