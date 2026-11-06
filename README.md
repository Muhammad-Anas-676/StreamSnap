# StreamSnap

**Capture any stream, instantly.**

Native Android (Kotlin, Jetpack Compose, Material 3) video downloader for personal use.
Paste many links, every link gets a preview card, downloads run through the yt-dlp engine (1 to 3 at a time, Settings)
([youtubedl-android](https://github.com/yausername/youtubedl-android)). No backend, no API keys.

## Build

Push to GitHub. The workflow `.github/workflows/build-apk.yml` builds the APK
(JDK 17, Gradle 8.13) and uploads it as the **StreamSnap-APK** artifact.

Signed release build (optional): add the repo secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`. Without them a debug APK is built.

## Where to look when something fails

* In the app: **Log** tab (exact errors, tap to expand, Copy / Share).
* On GitHub: Actions run, red error lines.

## Notes

* Download folder default: `StreamSnap/Download` (Settings to change).
* Modes: Automatic / Manual download, one quality for all videos or choose per video (Settings).
* Please download only content you have the right to save.

Made by Anas
