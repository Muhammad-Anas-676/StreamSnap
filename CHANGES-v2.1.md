# StreamSnap 2.1.0 (user app) and Admin 1.1.0

## Update to 2.1.0 (this build)

### User app
- Onboarding removed (`OnboardingScreen`, `OnbPage`, stage 1, the `onboarded` preference). Flow: Lock (if on) > Sign-in > Consent > Gate (blocked / maintenance / update) > Permissions (once) > Main. Old backups that still contain `"onboarded"` import fine (the key is ignored and removed from storage).
- Clipboard scan (`MainActivity.onWindowFocusChanged`) now runs only on the real app screens, not on Lock, Sign-in, Consent, Gate or Permissions.
- New 3D launcher icon in mdpi to xxxhdpi (`ic_launcher`, `ic_launcher_round`), Android 8+ adaptive icon (transparent background, artwork inside the 66 dp safe zone of the 108 dp layer) and a new in-app logo (`streamsnap_logo`). `ic_stat_download` is unchanged. The rounded clip on the logo was removed so the transparent artwork is never cropped.
- Sync fix: signing in on a fresh install no longer overwrites the cloud profile. The existing `users/{uid}` document (name, phone, country, totalDownloads, consentVersion) is read and merged first; the download counter only goes up. If the cloud cannot be read (offline), no profile field or counter is pushed until the next start.
- Sync fix: `users/{uid}` is created (status "active") before any other write. Presence, consent, link events, screen visits and crash reports wait for it (`Cloud.afterUser`), so the rules never refuse a valid first write.
- Sync fix: `CloudSync` no longer marks a link as sent while signed out or before consent; those links are sent after sign-in / consent. The "Total downloads" counter has its own list so nothing is counted twice.
- Queue header: the Auto / Manual pill calls `QueueManager.onModeChanged()` (READY items start at once in Auto), and choosing a quality in the header menu calls `applyDefaultQuality()` and `onModeChanged()`, same as Settings.

### Admin app
- The real app logo (gear and wrench) now shows inside the app (sign-in and PIN screens) instead of a generic Material icon.
- Live user list restarts itself after an error (Firestore ends a listener after an error), friendly error messages (permission denied, offline, quota, missing index), empty state in Users, all state updates are atomic.
- Sign-out clears every piece of user data from memory.
- PIN check uses a constant-time compare.
- Log classification: "storage" and "disk" no longer turn any download error into a Storage Issue.

### Build and rules
- Duplicate `lint { }` block removed from `app/build.gradle.kts`.
- Versions in sync: user app 2.1.0, admin 1.1.0, `LegalDocs` says "StreamSnap 2.1.0" (the consent `VERSION` is unchanged, nobody is asked again).
- `firestore.rules`: every field of users (including presence fields), notifications, events_links, events_screens, events_errors and config/app is checked for type and size, unknown fields are refused, `totalDownloads` can never go down, `config` is writable only as the `app` document. No composite index is needed.

## Earlier in v2.1

### User app
- Splash removed. First screen is sign-in. Permissions (notifications, storage) are asked after sign-in and consent.
- Sign-in: "Continue as Guest" (anonymous account, upgrade later in Profile), Google branded button, no language chips, country auto-filled from the phone prefix (+92, +1, +44, +971, +91, +966 ...).
- Consent screen: no language chips. Consent version 2 (online status, error reports, admin messages are now written in the documents).
- Queue: real storage meter (StatFs + real folder scan, refreshes every 3 s and when a download finishes or is removed), compact add card, announcement banner in the colour chosen by the admin (dismissible).
- Browser: flat top bar (Cancel, centred domain pill with lock, reload / close), bottom bar (back, forward, share, save login, open in browser).
- Admin messages: in-app alert plus phone notification titled "Message from StreamSnap Team".
- Presence: isOnline, lastActive, currentActivity written to Firestore (heartbeat every 2 minutes while the app is open).
- Crash reports are saved on the phone and sent on the next start.

## Admin app
- Branded Google button, copy button on every link, new Logs tab (Download Error, Network Failure, Engine Failure, Storage Issue, App Crash) with a copy button on every error.
- Announcement publisher with colour palette and live preview, "Send notification" to one user, live online / offline status with current activity.
- New launcher icon (blue gear with yellow wrench) in all densities, adaptive on Android 8+.
- App lock: 4-digit PIN (required on first sign-in), fingerprint, auto-lock timeout, screenshots blocked.

## Do this once
1. Firebase console > Authentication > Sign-in method > enable **Anonymous**.
2. Publish the new `firestore.rules`.
