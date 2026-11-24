# StreamSnap 2.1.0

Do apps ek hi project mein:

| Module | App | Package |
|---|---|---|
| `:app` | StreamSnap (user app) | `com.anas.streamsnap` |
| `:admin` | StreamSnap Admin (sirf developer) | `com.anas.streamsnap.admin` |
| `:core` | shared: theme, "Made by Anas", Firebase, legal documents | library |

## Kya naya hai
- Onboarding hata di gayi: app seedha Sign-in par khulti hai (ya Lock par agar App Lock on ho).
- Login (Google + email/password), consent screen (Privacy, Terms, Disclaimer, Data and Activity Notice), ban / maintenance / update gate.
- 4 tabs: Queue, History, Browser, Profile. Settings Profile ke gear icon se khulti hai.
- Theme: Light (default), Dark, AMOLED, System + 10 accent colours (DataStore). Module: `core/.../theme`.
- 3 languages: English (default), Roman Urdu, Urdu. Legal documents bhi usi zubaan mein.
- Settings: Appearance sab se upar, quality ke 3 modes radio ki tarah, Parallel aur Notifications expandable, real-time storage, Legal and About.
- Hata diya: Activity Logs screen, Proxy/Cookies/Import cookies, Auto keyboard, Engine section (engine update chupa kar chalta hai, Browser ka "Save login" bhi).
- Security: cleartext off, backup off, R8 on (release), PIN PBKDF2 + lockout, Firestore rules.
- Admin app: dashboard + charts, users (search, detail, ban), screen time (har visit ka exact waqt aur muddat), control (maintenance, announcement, minimum version).

## Build
GitHub Actions (`.github/workflows/build-apk.yml`) 4 APK banata hai. Firebase setup ke liye `SETUP.md` dekhein.
