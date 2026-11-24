# StreamSnap 2.0: Firebase setup (sirf phone se)

Build ke liye Firebase ke 2 `google-services.json` files chahiye (user app + admin app). Neeche ke steps ek baar karne hain.

## 1. Firebase project
1. Browser mein https://console.firebase.google.com kholein, **Add project** (naam: StreamSnap). Google Analytics band rakh sakte ho.
2. Plan **Spark (free)** hi rahega. Billing add nahi karni.

## 2. Signing fingerprint (SHA-1)
GitHub Actions ka build log kholein, step **"Print the signing fingerprint"** mein `SHA1: AA:BB:...` likha hota hai. Isay copy karlo. (Release aur debug dono APK isi key se sign hote hain, is liye ek SHA-1 kaafi hai.)

## 3. Do Android apps register karein
Project overview > **Add app > Android**:
- App 1: package `com.anas.streamsnap`, SHA-1 paste karein, **google-services.json** download karein.
- App 2: package `com.anas.streamsnap.admin`, wohi SHA-1, **google-services.json** download karein.

## 4. Authentication
Build > Authentication > Get started > Sign-in method:
- **Email/Password** enable karein.
- **Google** enable karein (support email chunein).
- **Anonymous** enable karein (yeh "Continue as Guest" ke liye zaroori hai).

Google enable karne ke baad dono `google-services.json` **dobara download** karein (taake Google sign-in ka client id unmein aa jaye).

## 5. Firestore
Build > Firestore Database > Create database > **Production mode**, region apke qareeb (masalan `asia-south1`).
Phir **Rules** tab mein is repo ki `firestore.rules` ka poora text paste karke **Publish** karein.

## 6. GitHub Secrets
Repo > Settings > Secrets and variables > Actions > New secret. Do secrets banayein:

| Secret | Value |
|---|---|
| `GOOGLE_SERVICES_APP_B64` | user app wali json ka base64 |
| `GOOGLE_SERVICES_ADMIN_B64` | admin app wali json ka base64 |

Termux mein base64 nikalne ke liye:
```
base64 -w0 google-services.json
```
Output poora copy karke secret mein paste karein. (Json files ko repo mein commit na karein, `.gitignore` unhein rokta hai.)

## 7. Build
Code push karein (ya Actions > Run workflow). Build ke baad **Artifacts > StreamSnap-APKs** mein 4 APK milte hain:
`StreamSnap-user-release.apk`, `StreamSnap-user-debug.apk`, `StreamSnap-admin-release.apk`, `StreamSnap-admin-debug.apk`.

## 8. Pehla admin
1. Firebase console > Authentication > Users > **Add user**: apna email aur password.
2. Admin APK install karein aur isi email/password se sign in karein. App "Not an admin" dikhayegi aur aapka **UID** dikhayegi. **Copy my UID** dabayein.
3. Firebase console > Firestore > **Start collection** `admins` > Document ID mein wohi UID paste karein > koi ek field (masalan `role` = `owner`) > Save.
4. Admin app dobara kholein. Dashboard khul jayega.

## 9. Support email (optional)
`core/src/main/java/com/anas/streamsnap/core/legal/LegalDocs.kt` mein `SUPPORT_EMAIL = ""` ke andar apna email likh dein, to wo legal documents ke aakhir mein dikhne lagega.

## Naye features ke liye (v2.1)
- **Guest login**: Authentication > Sign-in method > **Anonymous** on hona chahiye, warna guest button error dega.
- **Rules dobara publish karein**: `firestore.rules` mein naye paths hain (`users/{uid}/notifications`, `events_errors`). Purani rules rahengi to notifications aur error logs nahi chalenge.
- **Admin app lock**: pehli baar admin sign in karne ke baad 4-digit PIN banana lazmi hai. Fingerprint More > Security mein on hota hai.
- **Online status**: user app har 2 minute mein ek write karti hai jab tak wo foreground mein ho (100 users ke liye Spark limit mein hai).
- **Consent version 2**: privacy documents mein online status, error reports aur admin messages likhe gaye hain, is liye sab users se dobara manzoori li jayegi.

## Zaroori baatein
- **Spark limits**: 50,000 reads aur 20,000 writes roz. 100 users ke liye kaafi hai. Admin dashboard har refresh par data parhta hai, is liye zyada baar refresh na karein.
- **Ban** user ke agle app launch par lagta hai (app apna status Firestore se check karti hai) aur rules naye events likhne se bhi rok dete hain.
- **Privacy documents**: legal text main ne likha hai, lekin yeh qanooni mashwara nahi. Public release se pehle kisi qabil shakhs se check karwa lein. Text badalne par `LegalDocs.VERSION` ek barha dein, sab users se dobara manzoori li jayegi.
- Agar `google-services.json` ke baghair build karein to app chalti hai lekin login/cloud band rehta hai (Profile mein notice dikhta hai).
