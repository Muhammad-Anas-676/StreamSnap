# StreamSnap v2.1 (user app) and Admin v1.1

## User app
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
