package com.anas.streamsnap.core.legal

import com.anas.streamsnap.core.CoreLang

class LegalSection(val heading: String, val body: String)

/**
 * The legal documents shown in Settings > Legal & About and on the consent screen.
 * Every document exists in English (default), Roman Urdu and Urdu and follows the language chosen in the app.
 *
 * Bump [VERSION] whenever any text changes in a way users must agree to again:
 * the app then shows the consent screen once more.
 */
object LegalDocs {
    const val VERSION = 2
    const val UPDATED = "24 November 2026"

    /** Optional support address shown at the end of the documents. Leave empty to hide it. */
    const val SUPPORT_EMAIL = ""

    /** Documents the user must accept on the consent screen. */
    val consentIds = listOf("privacy", "terms", "disclaimer", "monitoring")

    /** Documents listed in Legal & About (the About text is shown first). */
    val allIds = listOf("about", "privacy", "terms", "disclaimer", "monitoring")

    private fun s(h: String, b: String) = LegalSection(h, b)

    fun title(id: String): String = when (CoreLang.lang) {
        "ro" -> when (id) {
            "privacy" -> "Privacy Policy"
            "terms" -> "Terms of Service"
            "disclaimer" -> "Disclaimer aur Copyright"
            "monitoring" -> "Data aur Activity Notice"
            else -> "StreamSnap ke bare mein"
        }
        "ur" -> when (id) {
            "privacy" -> "پرائیویسی پالیسی"
            "terms" -> "سروس کی شرائط"
            "disclaimer" -> "دستبرداری اور کاپی رائٹ"
            "monitoring" -> "ڈیٹا اور سرگرمی کا نوٹس"
            else -> "اسٹریم اسنیپ کے بارے میں"
        }
        else -> when (id) {
            "privacy" -> "Privacy Policy"
            "terms" -> "Terms of Service"
            "disclaimer" -> "Disclaimer and Copyright"
            "monitoring" -> "Data and Activity Notice"
            else -> "About StreamSnap"
        }
    }

    fun summary(id: String): String = when (CoreLang.lang) {
        "ro" -> when (id) {
            "privacy" -> "Hum kya jama karte hain aur kyun"
            "terms" -> "App istemal karne ke qawaid"
            "disclaimer" -> "Aapki zimmedari aur copyright"
            "monitoring" -> "Admin kya dekh sakta hai"
            else -> "App, developer aur credits"
        }
        "ur" -> when (id) {
            "privacy" -> "ہم کیا جمع کرتے ہیں اور کیوں"
            "terms" -> "ایپ استعمال کرنے کے قواعد"
            "disclaimer" -> "آپ کی ذمہ داری اور کاپی رائٹ"
            "monitoring" -> "ایڈمن کیا دیکھ سکتا ہے"
            else -> "ایپ، ڈیولپر اور کریڈٹس"
        }
        else -> when (id) {
            "privacy" -> "What we collect and why"
            "terms" -> "Rules for using the app"
            "disclaimer" -> "Your responsibility and copyright"
            "monitoring" -> "What the admin can see"
            else -> "The app, the developer and credits"
        }
    }

    fun sections(id: String): List<LegalSection> = when (CoreLang.lang) {
        "ro" -> ro(id)
        "ur" -> ur(id)
        else -> en(id)
    }

    // ------------------------------------------------------------------------------------ English
    private fun en(id: String): List<LegalSection> = when (id) {
        "privacy" -> listOf(
            s("1. Who we are", "StreamSnap is a personal video-saving app made by Anas (the developer). This policy explains what information the app and its private admin tool collect, why they collect it, and the choices you have."),
            s("2. Information we collect", "Account: your name, email address, phone number and country (entered by you or supplied by Google Sign-In), the link to your Google profile photo if you sign in with Google, and your sign-in method.\n\nUsage: the links you add to the app, the website each link belongs to, the time you added it, the quality chosen, and whether the download finished or failed.\n\nScreen activity: which screens of the app you open, when each visit started and how long you stayed.\n\nDevice and app: device make and model, Android version, app version and the time you were last active.\n\nLive status and errors: whether you are online, the screen you are using, the text of errors from failed downloads and crash reports of the app.\n\nGuest accounts: if you continue as a guest, an anonymous account is created for you.\n\nConsent: which version of these documents you accepted, and when."),
            s("3. What we do not collect", "We do not collect or store your account password (Google Firebase Authentication handles it), the cookies or saved logins of websites you use in the in-app browser, your browsing history, the pages you view, the contents of your downloaded files, your contacts, messages, microphone, camera or precise location."),
            s("4. Why we use it", "To create and secure your account, keep the service working, find and fix problems (for example a site that stops downloading), prevent misuse of the app, understand which features are used and improve the app."),
            s("5. Where it is stored", "Account and activity data is stored with Google Firebase (Authentication and Cloud Firestore). Google may process it on servers outside Pakistan. Your downloaded files, settings and profile picture stay on your phone."),
            s("6. Who can see it", "Only the developer, through a private admin app protected by sign-in. We do not sell your data and we do not share it with advertisers."),
            s("7. How long we keep it", "Until you ask us to delete it or your account is removed. When an account is deleted, its personal data is removed within a reasonable time."),
            s("8. Your choices", "You can view and edit your name, phone and country in Profile, change the language and theme at any time, and sign out whenever you like. To delete your account and data, contact the developer."),
            s("9. Security", "Data travels over encrypted connections and access to stored data is restricted by Firebase security rules. No system is perfectly secure, so please use a strong password and keep your phone locked."),
            s("10. Children", "StreamSnap is not meant for children under 13. Please do not use it if you are under 13."),
            s("11. Changes and contact", "If this policy changes in a way that matters, the app will ask you to accept the new version. Contact: the developer, Anas, through the app's distribution page." + support("en")),
        )
        "terms" -> listOf(
            s("1. Acceptance", "By creating an account or using StreamSnap you agree to these Terms, the Privacy Policy, the Disclaimer and the Data and Activity Notice. If you do not agree, do not use the app."),
            s("2. Your account", "You must give correct details and keep your password private. You are responsible for everything done through your account."),
            s("3. Permitted use", "StreamSnap is for saving content for your own personal, offline use, and only content you own, that is in the public domain, that is licensed for download, or that you have permission to save."),
            s("4. What you must not do", "Do not use the app to break any law or the rules of a website; to copy, share or sell content you have no right to; to bypass paid, private or protected content; to harm, overload or attack any service; or to reverse engineer, resell or misuse the app."),
            s("5. Content and copyright", "You are solely responsible for the content you save and how you use it. The developer does not host, store or provide any video, audio or image content."),
            s("6. Third-party sites and tools", "The app works with websites and open-source tools that the developer does not control. They can change or stop working at any time."),
            s("7. Availability and updates", "The app is provided as is. Features may change, and some sites may not work. The app may update its download engine in the background."),
            s("8. Suspension", "The developer may suspend or remove an account that breaks these Terms, abuses the service or puts it at risk."),
            s("9. No warranty", "The app is provided without any warranty, express or implied, including fitness for a particular purpose and uninterrupted or error-free operation."),
            s("10. Limitation of liability", "To the fullest extent allowed by law, the developer is not liable for any loss, damage or legal claim arising from your use of the app or the content you save."),
            s("11. Changes", "These Terms may be updated. When they change in a way that matters, you will be asked to accept the new version to continue."),
            s("12. Governing law", "These Terms are governed by the laws of Pakistan. Courts in Pakistan have jurisdiction over any dispute." + support("en")),
        )
        "disclaimer" -> listOf(
            s("1. Use at your own risk", "You decide what to download. The developer gives no guarantee that any download will work, be complete, be legal in your country or be safe."),
            s("2. Copyright and permission", "Most videos and music are protected by copyright. Download only what you have the right to download. Respecting the rights of creators is your responsibility."),
            s("3. Website rules", "Websites such as YouTube, Instagram, Facebook and TikTok have their own terms. Downloading from them may be against those terms. You accept any consequences of using the app with them."),
            s("4. No affiliation", "StreamSnap is an independent app. It is not affiliated with, endorsed by or sponsored by YouTube, Google, Meta, TikTok, X or any other website. All names and logos belong to their owners."),
            s("5. Availability of sites", "Websites change often. A site that works today may stop working tomorrow, and the developer cannot promise a fix by a given date."),
            s("6. Downloaded files", "Files are saved on your phone. You are responsible for storing, sharing and deleting them. Check where a file came from before you open it."),
            s("7. In-app browser", "The browser is a convenience for finding links. Sites you visit in it are not checked or controlled by the developer. Take the same care you would in any browser."),
            s("8. Not professional advice", "Nothing in the app or in these documents is legal advice. If you are unsure whether you may download something, ask a qualified person."),
        )
        "monitoring" -> listOf(
            s("Why this notice exists", "To keep the service secure and working, the developer can see certain activity from your account. This notice lists it plainly so there are no surprises. By accepting it you agree to this monitoring."),
            s("What is recorded", "Links: the full link you add, the website it belongs to, the time, the quality chosen and the result (added, done or failed).\n\nScreens: the name of each screen you open (Queue, History, Browser, Profile, Settings, Legal), the exact time each visit started and how long you stayed. The timer stops when the app goes to the background.\n\nAccount: name, email, phone, country, sign-in method, join date, last active time and total downloads.\n\nDevice: make, model, Android version and app version.\n\nLive status: whether you are online now, the time of your last activity and what you are doing (for example Viewing Queue or Browsing Web).\n\nError reports: the error text of failed downloads (it can contain the link you tried) and technical crash reports of the app.\n\nMessages: the developer can send you a notification. It is shown inside the app and as a phone notification titled Message from StreamSnap Team."),
            s("What is never recorded", "Passwords, website cookies and saved logins, your browsing history, the pages you open in the in-app browser, the contents of your files, contacts, messages, camera, microphone and location."),
            s("Who can see it", "Only the developer, in a private admin app that needs a protected sign-in."),
            s("What it is used for", "Finding broken sites, fixing errors, stopping abuse, understanding which screens and features are used, and improving the app. It is not sold or shared for advertising."),
            s("If you disagree", "Do not accept, or stop using the app. You can ask the developer to delete your account and the data linked to it."),
        )
        else -> listOf(
            s("StreamSnap", "Capture any stream, instantly. StreamSnap saves videos and audio from many websites to your phone, with a queue, history, a built-in browser and quality choices."),
            s("Version", "StreamSnap 2.1.0"),
            s("Developer", "Made by Anas, a solo developer from Karachi, Pakistan."),
            s("Built with", "yt-dlp (download engine), FFmpeg (conversion), Media3 (player), Coil (images), Google Firebase (accounts) and Jetpack Compose with Material 3. The script font Dancing Script is used under the SIL Open Font License."),
            s("Thank you", "Thank you for using StreamSnap. Please use it responsibly and respect the work of creators."),
        )
    }

    // ------------------------------------------------------------------------------------ Roman Urdu
    private fun ro(id: String): List<LegalSection> = when (id) {
        "privacy" -> listOf(
            s("1. Hum kaun hain", "StreamSnap ek personal video-saving app hai jo Anas (developer) ne banayi hai. Yeh policy batati hai ke app aur uska private admin tool kya maloomat jama karte hain, kyun karte hain, aur aapke paas kya ikhtiyar hain."),
            s("2. Hum kya maloomat jama karte hain", "Account: aapka naam, email, phone number aur mulk (aap ne likha ya Google Sign-In se mila), Google se login par Google profile photo ka link, aur sign-in ka tareeqa.\n\nIstemal: jo links aap app mein dalte hain, har link ki website, kis waqt dala, kaunsi quality chuni, aur download mukammal hui ya nakam.\n\nScreen activity: app ki kaunsi screens kholi, har visit kab shuru hui aur kitni der rukay.\n\nDevice aur app: device ki company aur model, Android version, app version aur aakhri active waqt.\n\nLive status aur errors: aap online hain ya nahi, aap kaunsi screen istemal kar rahe hain, nakam downloads ke error ka text aur app ke crash reports.\n\nGuest account: agar aap guest ke tor par jaari rakhein to aapke liye ek anonymous account ban jata hai.\n\nRazamandi: in documents ka kaunsa version aap ne manzoor kiya aur kab."),
            s("3. Hum kya jama nahi karte", "Hum aapka account password jama ya store nahi karte (yeh Google Firebase Authentication sambhalta hai). In-app browser mein istemal hone wali websites ki cookies ya saved login, browsing history, dekhe gaye pages, download ki hui files ka mawad, contacts, messages, microphone, camera ya sahi location bhi jama nahi hoti."),
            s("4. Kyun istemal karte hain", "Account banane aur mehfooz rakhne, service chalti rakhne, masail dhoondne aur theek karne (masalan koi site download na kare), app ke ghalat istemal ko rokne, yeh samajhne ke kaunse features chalte hain, aur app behtar banane ke liye."),
            s("5. Kahan store hota hai", "Account aur activity data Google Firebase (Authentication aur Cloud Firestore) mein store hota hai. Google ise Pakistan se bahar ke servers par process kar sakta hai. Aapki downloads, settings aur profile picture aapke phone mein hi rehti hain."),
            s("6. Kaun dekh sakta hai", "Sirf developer, ek private admin app ke zariye jo sign-in se mehfooz hai. Hum aapka data bechte nahi aur ishtehar walon se share nahi karte."),
            s("7. Kitni der rakhte hain", "Jab tak aap delete karne ko na kahein ya account hata diya jaye. Account delete hone par zaati data munasib muddat mein hata diya jata hai."),
            s("8. Aapke ikhtiyar", "Aap Profile mein naam, phone aur mulk dekh aur badal sakte hain, zubaan aur theme kabhi bhi badal sakte hain, aur jab chahein sign out kar sakte hain. Account aur data delete karwane ke liye developer se rabta karein."),
            s("9. Hifazat", "Data encrypted connection par jata hai aur stored data tak rasai Firebase security rules se mehdood hai. Koi system mukammal mehfooz nahi hota, is liye mazboot password rakhein aur phone lock rakhein."),
            s("10. Bachay", "StreamSnap 13 saal se kam umar ke bachon ke liye nahi hai. Agar aap 13 saal se chhote hain to istemal na karein."),
            s("11. Tabdeeliyan aur rabta", "Agar yeh policy ahem tor par badli to app naya version manzoor karne ko kahegi. Rabta: developer Anas, app ke distribution page ke zariye." + support("ro")),
        )
        "terms" -> listOf(
            s("1. Manzoori", "Account banane ya StreamSnap istemal karne se aap in Terms, Privacy Policy, Disclaimer aur Data and Activity Notice se ittefaq karte hain. Agar ittefaq nahi to app istemal na karein."),
            s("2. Aapka account", "Aap ko durust maloomat deni hain aur password raaz mein rakhna hai. Aapke account se jo kuch hota hai uske zimmedar aap hain."),
            s("3. Jaiz istemal", "StreamSnap sirf apne zaati, offline istemal ke liye content save karne ke liye hai, aur sirf woh content jo aapka apna ho, public domain mein ho, download ke liye licensed ho, ya jis ki aap ko ijazat ho."),
            s("4. Kya nahi karna", "App ko kisi qanoon ya website ke qawaid todne, haq ke baghair content copy, share ya bechne, paid, private ya mehfooz content bypass karne, kisi service ko nuqsan, overload ya hamla karne, ya app ko reverse engineer, resell ya ghalat istemal karne ke liye istemal na karein."),
            s("5. Content aur copyright", "Jo content aap save karte hain aur jaise istemal karte hain uske sirf aap zimmedar hain. Developer koi video, audio ya image content host, store ya faraham nahi karta."),
            s("6. Teesri party ki sites aur tools", "App aisi websites aur open-source tools ke sath chalti hai jin par developer ka control nahi. Woh kabhi bhi badal sakti hain ya kaam karna band kar sakti hain."),
            s("7. Dastiyabi aur updates", "App jaisi hai waisi di jati hai. Features badal sakte hain aur kuch sites kaam na karein. App background mein apna download engine update kar sakti hai."),
            s("8. Muattali", "Developer aisa account muattal ya khatam kar sakta hai jo in Terms ko tore, service ka ghalat istemal kare ya use khatre mein dale."),
            s("9. Koi warranty nahi", "App kisi bhi qism ki warranty ke baghair di jati hai, jis mein kisi khaas maqsad ki munasibat aur be-rukawat ya ghalti se paak chalne ki zamanat shamil hai."),
            s("10. Zimmedari ki had", "Qanoon ki ijazat ki hadd tak, app ya save kiye gaye content ke istemal se hone wale kisi nuqsan ya qanooni daawe ka developer zimmedar nahi."),
            s("11. Tabdeeliyan", "In Terms ko update kiya ja sakta hai. Ahem tabdeeli par jaari rakhne ke liye aap se naya version manzoor karwaya jayega."),
            s("12. Hakim qanoon", "In Terms par Pakistan ke qawaneen laagu hote hain. Kisi bhi jhagre ka ikhtiyar Pakistan ki adaalaton ko hai." + support("ro")),
        )
        "disclaimer" -> listOf(
            s("1. Apni zimmedari par", "Kya download karna hai, yeh aap tay karte hain. Developer is ki zamanat nahi deta ke koi download chalegi, mukammal hogi, aapke mulk mein jaiz hogi ya mehfooz hogi."),
            s("2. Copyright aur ijazat", "Zyadatar videos aur music copyright se mehfooz hain. Sirf woh download karein jis ka aap ko haq ho. Banane walon ke haq ka ehtram aapki zimmedari hai."),
            s("3. Websites ke qawaid", "YouTube, Instagram, Facebook aur TikTok jaisi websites ke apne terms hain. Unse download karna un terms ke khilaf ho sakta hai. App ke istemal ke nataij aap qubool karte hain."),
            s("4. Koi talluq nahi", "StreamSnap ek azad app hai. Iska YouTube, Google, Meta, TikTok, X ya kisi aur website se koi talluq, tasdeeq ya sponsorship nahi. Tamam naam aur logo unke maalikon ke hain."),
            s("5. Sites ki dastiyabi", "Websites aksar badalti hain. Aaj chalne wali site kal band ho sakti hai aur developer kisi tareekh tak fix ka wada nahi kar sakta."),
            s("6. Download ki hui files", "Files aapke phone mein save hoti hain. Unhein rakhna, share karna aur delete karna aapki zimmedari hai. Kholne se pehle dekh lein ke file kahan se aayi."),
            s("7. In-app browser", "Browser links dhoondne ki sahulat hai. Is mein khuli sites ko developer check ya control nahi karta. Wohi ehtiyat karein jo kisi bhi browser mein karte hain."),
            s("8. Pesha-warana mashwara nahi", "App ya in documents mein kuch bhi qanooni mashwara nahi. Agar yaqeen na ho ke kuch download karna jaiz hai ya nahi to kisi qabil shakhs se poochein."),
        )
        "monitoring" -> listOf(
            s("Yeh notice kyun hai", "Service ko mehfooz aur chalta rakhne ke liye developer aapke account ki kuch activity dekh sakta hai. Yeh notice use saaf likhta hai taake koi hairani na ho. Ise manzoor karne ka matlab hai ke aap is monitoring se ittefaq karte hain."),
            s("Kya record hota hai", "Links: aapka dala hua poora link, uski website, waqt, chuni hui quality aur nateeja (added, done ya failed).\n\nScreens: har kholi hui screen ka naam (Queue, History, Browser, Profile, Settings, Legal), har visit ka sahi shuru hone ka waqt aur kitni der rukay. App background mein jaye to timer ruk jata hai.\n\nAccount: naam, email, phone, mulk, sign-in ka tareeqa, join date, aakhri active waqt aur total downloads.\n\nDevice: company, model, Android version aur app version.\n\nLive status: aap abhi online hain ya nahi, aakhri activity ka waqt aur aap kya kar rahe hain (masalan Viewing Queue ya Browsing Web).\n\nError reports: nakam downloads ke error ka text (is mein aapka aazmaya hua link ho sakta hai) aur app ke technical crash reports.\n\nPaighamat: developer aapko notification bhej sakta hai. Yeh app ke andar aur phone notification mein Message from StreamSnap Team ke unwan se nazar aata hai."),
            s("Kya kabhi record nahi hota", "Password, websites ki cookies aur saved login, browsing history, in-app browser mein khule pages, files ka mawad, contacts, messages, camera, microphone aur location."),
            s("Kaun dekh sakta hai", "Sirf developer, ek private admin app mein jis ke liye mehfooz sign-in chahiye."),
            s("Kis kaam mein aata hai", "Kharab sites dhoondne, errors theek karne, ghalat istemal rokne, yeh samajhne ke kaunsi screens aur features chalte hain, aur app behtar karne ke liye. Isay becha ya ishtehar ke liye share nahi kiya jata."),
            s("Agar aap ittefaq nahi karte", "Manzoor na karein ya app istemal karna band kar dein. Aap developer se account aur us se jura data delete karne ko keh sakte hain."),
        )
        else -> listOf(
            s("StreamSnap", "Capture any stream, instantly. StreamSnap bohat si websites se video aur audio aapke phone mein save karti hai, queue, history, built-in browser aur quality ke intikhab ke sath."),
            s("Version", "StreamSnap 2.1.0"),
            s("Developer", "Made by Anas, Karachi, Pakistan ke ek akele developer."),
            s("Kis se bana", "yt-dlp (download engine), FFmpeg (conversion), Media3 (player), Coil (images), Google Firebase (accounts) aur Jetpack Compose ke sath Material 3. Script font Dancing Script SIL Open Font License ke tahat istemal hua hai."),
            s("Shukriya", "StreamSnap istemal karne ka shukriya. Zimmedari se istemal karein aur banane walon ki mehnat ka ehtram karein."),
        )
    }

    // ------------------------------------------------------------------------------------ Urdu
    private fun ur(id: String): List<LegalSection> = when (id) {
        "privacy" -> listOf(
            s("1. ہم کون ہیں", "اسٹریم اسنیپ ایک ذاتی ویڈیو محفوظ کرنے والی ایپ ہے جو انس (ڈیولپر) نے بنائی ہے۔ یہ پالیسی بتاتی ہے کہ ایپ اور اس کا نجی ایڈمن ٹول کیا معلومات جمع کرتے ہیں، کیوں کرتے ہیں، اور آپ کے پاس کیا اختیارات ہیں۔"),
            s("2. ہم کیا معلومات جمع کرتے ہیں", "اکاؤنٹ: آپ کا نام، ای میل، فون نمبر اور ملک (آپ نے لکھا یا گوگل سائن اِن سے ملا)، گوگل سے لاگ اِن پر گوگل پروفائل فوٹو کا لنک، اور سائن اِن کا طریقہ۔\n\nاستعمال: جو لنک آپ ایپ میں ڈالتے ہیں، ہر لنک کی ویب سائٹ، کس وقت ڈالا، کون سی کوالٹی چنی، اور ڈاؤن لوڈ مکمل ہوئی یا ناکام۔\n\nاسکرین سرگرمی: ایپ کی کون سی اسکرینیں کھولیں، ہر وزٹ کب شروع ہوئی اور کتنی دیر رکے۔\n\nڈیوائس اور ایپ: ڈیوائس کی کمپنی اور ماڈل، اینڈرائیڈ ورژن، ایپ ورژن اور آخری بار فعال ہونے کا وقت۔\n\nلائیو اسٹیٹس اور ایررز: آپ آن لائن ہیں یا نہیں، آپ کون سی اسکرین استعمال کر رہے ہیں، ناکام ڈاؤن لوڈز کے ایرر کا متن اور ایپ کی کریش رپورٹس۔\n\nگیسٹ اکاؤنٹ: اگر آپ گیسٹ کے طور پر جاری رکھیں تو آپ کے لیے ایک گمنام اکاؤنٹ بن جاتا ہے۔\n\nرضامندی: ان دستاویزات کا کون سا ورژن آپ نے قبول کیا اور کب۔"),
            s("3. ہم کیا جمع نہیں کرتے", "ہم آپ کا اکاؤنٹ پاس ورڈ جمع یا محفوظ نہیں کرتے (یہ گوگل فائر بیس آتھنٹیکیشن سنبھالتا ہے)۔ ان ایپ براؤزر میں استعمال ہونے والی ویب سائٹس کی کوکیز یا محفوظ لاگ اِن، براؤزنگ ہسٹری، دیکھے گئے صفحات، ڈاؤن لوڈ کی ہوئی فائلوں کا مواد، رابطے، پیغامات، مائیکروفون، کیمرہ یا درست مقام بھی جمع نہیں ہوتے۔"),
            s("4. کیوں استعمال کرتے ہیں", "اکاؤنٹ بنانے اور محفوظ رکھنے، سروس چلتی رکھنے، مسائل ڈھونڈنے اور ٹھیک کرنے (مثلاً کوئی سائٹ ڈاؤن لوڈ نہ کرے)، ایپ کے غلط استعمال کو روکنے، یہ سمجھنے کہ کون سے فیچرز استعمال ہوتے ہیں، اور ایپ بہتر بنانے کے لیے۔"),
            s("5. کہاں محفوظ ہوتا ہے", "اکاؤنٹ اور سرگرمی کا ڈیٹا گوگل فائر بیس (آتھنٹیکیشن اور کلاؤڈ فائر اسٹور) میں محفوظ ہوتا ہے۔ گوگل اسے پاکستان سے باہر کے سرورز پر پراسیس کر سکتا ہے۔ آپ کی ڈاؤن لوڈ فائلیں، سیٹنگز اور پروفائل تصویر آپ کے فون میں ہی رہتی ہیں۔"),
            s("6. کون دیکھ سکتا ہے", "صرف ڈیولپر، ایک نجی ایڈمن ایپ کے ذریعے جو سائن اِن سے محفوظ ہے۔ ہم آپ کا ڈیٹا بیچتے نہیں اور اشتہار والوں سے شیئر نہیں کرتے۔"),
            s("7. کتنی دیر رکھتے ہیں", "جب تک آپ حذف کرنے کو نہ کہیں یا اکاؤنٹ ہٹا دیا جائے۔ اکاؤنٹ حذف ہونے پر ذاتی ڈیٹا مناسب مدت میں ہٹا دیا جاتا ہے۔"),
            s("8. آپ کے اختیارات", "آپ پروفائل میں نام، فون اور ملک دیکھ اور بدل سکتے ہیں، زبان اور تھیم کبھی بھی بدل سکتے ہیں، اور جب چاہیں سائن آؤٹ کر سکتے ہیں۔ اکاؤنٹ اور ڈیٹا حذف کروانے کے لیے ڈیولپر سے رابطہ کریں۔"),
            s("9. حفاظت", "ڈیٹا انکرپٹڈ کنکشن پر جاتا ہے اور محفوظ ڈیٹا تک رسائی فائر بیس سیکیورٹی رولز سے محدود ہے۔ کوئی نظام مکمل محفوظ نہیں ہوتا، اس لیے مضبوط پاس ورڈ رکھیں اور فون لاک رکھیں۔"),
            s("10. بچے", "اسٹریم اسنیپ 13 سال سے کم عمر بچوں کے لیے نہیں ہے۔ اگر آپ 13 سال سے چھوٹے ہیں تو استعمال نہ کریں۔"),
            s("11. تبدیلیاں اور رابطہ", "اگر یہ پالیسی اہم طور پر بدلی تو ایپ نیا ورژن قبول کرنے کو کہے گی۔ رابطہ: ڈیولپر انس، ایپ کے ڈسٹری بیوشن پیج کے ذریعے۔" + support("ur")),
        )
        "terms" -> listOf(
            s("1. منظوری", "اکاؤنٹ بنانے یا اسٹریم اسنیپ استعمال کرنے سے آپ ان شرائط، پرائیویسی پالیسی، دستبرداری اور ڈیٹا و سرگرمی نوٹس سے اتفاق کرتے ہیں۔ اگر اتفاق نہیں تو ایپ استعمال نہ کریں۔"),
            s("2. آپ کا اکاؤنٹ", "آپ کو درست معلومات دینی ہیں اور پاس ورڈ راز میں رکھنا ہے۔ آپ کے اکاؤنٹ سے جو کچھ ہوتا ہے اس کے ذمہ دار آپ ہیں۔"),
            s("3. جائز استعمال", "اسٹریم اسنیپ صرف اپنے ذاتی، آف لائن استعمال کے لیے مواد محفوظ کرنے کے لیے ہے، اور صرف وہ مواد جو آپ کا اپنا ہو، پبلک ڈومین میں ہو، ڈاؤن لوڈ کے لیے لائسنس شدہ ہو، یا جس کی آپ کو اجازت ہو۔"),
            s("4. کیا نہیں کرنا", "ایپ کو کسی قانون یا ویب سائٹ کے قواعد توڑنے، حق کے بغیر مواد کاپی، شیئر یا بیچنے، ادا شدہ، نجی یا محفوظ مواد بائی پاس کرنے، کسی سروس کو نقصان، اوور لوڈ یا حملہ کرنے، یا ایپ کو ریورس انجینئر، ری سیل یا غلط استعمال کرنے کے لیے استعمال نہ کریں۔"),
            s("5. مواد اور کاپی رائٹ", "جو مواد آپ محفوظ کرتے ہیں اور جیسے استعمال کرتے ہیں اس کے صرف آپ ذمہ دار ہیں۔ ڈیولپر کوئی ویڈیو، آڈیو یا تصویری مواد ہوسٹ، اسٹور یا فراہم نہیں کرتا۔"),
            s("6. تیسرے فریق کی سائٹس اور ٹولز", "ایپ ایسی ویب سائٹس اور اوپن سورس ٹولز کے ساتھ چلتی ہے جن پر ڈیولپر کا کنٹرول نہیں۔ وہ کبھی بھی بدل سکتی ہیں یا کام کرنا بند کر سکتی ہیں۔"),
            s("7. دستیابی اور اپڈیٹس", "ایپ جیسی ہے ویسی دی جاتی ہے۔ فیچرز بدل سکتے ہیں اور کچھ سائٹس کام نہ کریں۔ ایپ پس منظر میں اپنا ڈاؤن لوڈ انجن اپڈیٹ کر سکتی ہے۔"),
            s("8. معطلی", "ڈیولپر ایسا اکاؤنٹ معطل یا ختم کر سکتا ہے جو ان شرائط کو توڑے، سروس کا غلط استعمال کرے یا اسے خطرے میں ڈالے۔"),
            s("9. کوئی وارنٹی نہیں", "ایپ کسی بھی قسم کی وارنٹی کے بغیر دی جاتی ہے، جس میں کسی خاص مقصد کی مناسبت اور بلا رکاوٹ یا غلطی سے پاک چلنے کی ضمانت شامل ہے۔"),
            s("10. ذمہ داری کی حد", "قانون کی اجازت کی حد تک، ایپ یا محفوظ کیے گئے مواد کے استعمال سے ہونے والے کسی نقصان یا قانونی دعوے کا ڈیولپر ذمہ دار نہیں۔"),
            s("11. تبدیلیاں", "ان شرائط کو اپڈیٹ کیا جا سکتا ہے۔ اہم تبدیلی پر جاری رکھنے کے لیے آپ سے نیا ورژن قبول کروایا جائے گا۔"),
            s("12. حاکم قانون", "ان شرائط پر پاکستان کے قوانین لاگو ہوتے ہیں۔ کسی بھی جھگڑے کا اختیار پاکستان کی عدالتوں کو ہے۔" + support("ur")),
        )
        "disclaimer" -> listOf(
            s("1. اپنی ذمہ داری پر", "کیا ڈاؤن لوڈ کرنا ہے، یہ آپ طے کرتے ہیں۔ ڈیولپر اس کی ضمانت نہیں دیتا کہ کوئی ڈاؤن لوڈ چلے گی، مکمل ہوگی، آپ کے ملک میں جائز ہوگی یا محفوظ ہوگی۔"),
            s("2. کاپی رائٹ اور اجازت", "زیادہ تر ویڈیوز اور موسیقی کاپی رائٹ سے محفوظ ہیں۔ صرف وہ ڈاؤن لوڈ کریں جس کا آپ کو حق ہو۔ بنانے والوں کے حق کا احترام آپ کی ذمہ داری ہے۔"),
            s("3. ویب سائٹس کے قواعد", "یوٹیوب، انسٹاگرام، فیس بک اور ٹک ٹاک جیسی ویب سائٹس کے اپنے شرائط ہیں۔ ان سے ڈاؤن لوڈ کرنا ان شرائط کے خلاف ہو سکتا ہے۔ ایپ کے استعمال کے نتائج آپ قبول کرتے ہیں۔"),
            s("4. کوئی تعلق نہیں", "اسٹریم اسنیپ ایک آزاد ایپ ہے۔ اس کا یوٹیوب، گوگل، میٹا، ٹک ٹاک، ایکس یا کسی اور ویب سائٹ سے کوئی تعلق، تصدیق یا اسپانسرشپ نہیں۔ تمام نام اور لوگو ان کے مالکان کے ہیں۔"),
            s("5. سائٹس کی دستیابی", "ویب سائٹس اکثر بدلتی ہیں۔ آج چلنے والی سائٹ کل بند ہو سکتی ہے اور ڈیولپر کسی تاریخ تک فکس کا وعدہ نہیں کر سکتا۔"),
            s("6. ڈاؤن لوڈ کی ہوئی فائلیں", "فائلیں آپ کے فون میں محفوظ ہوتی ہیں۔ انہیں رکھنا، شیئر کرنا اور حذف کرنا آپ کی ذمہ داری ہے۔ کھولنے سے پہلے دیکھ لیں کہ فائل کہاں سے آئی۔"),
            s("7. ان ایپ براؤزر", "براؤزر لنک ڈھونڈنے کی سہولت ہے۔ اس میں کھلی سائٹس کو ڈیولپر چیک یا کنٹرول نہیں کرتا۔ وہی احتیاط کریں جو کسی بھی براؤزر میں کرتے ہیں۔"),
            s("8. پیشہ ورانہ مشورہ نہیں", "ایپ یا ان دستاویزات میں کچھ بھی قانونی مشورہ نہیں۔ اگر یقین نہ ہو کہ کچھ ڈاؤن لوڈ کرنا جائز ہے یا نہیں تو کسی قابل شخص سے پوچھیں۔"),
        )
        "monitoring" -> listOf(
            s("یہ نوٹس کیوں ہے", "سروس کو محفوظ اور چلتا رکھنے کے لیے ڈیولپر آپ کے اکاؤنٹ کی کچھ سرگرمی دیکھ سکتا ہے۔ یہ نوٹس اسے صاف لکھتا ہے تاکہ کوئی حیرانی نہ ہو۔ اسے قبول کرنے کا مطلب ہے کہ آپ اس نگرانی سے اتفاق کرتے ہیں۔"),
            s("کیا ریکارڈ ہوتا ہے", "لنکس: آپ کا ڈالا ہوا پورا لنک، اس کی ویب سائٹ، وقت، چنی ہوئی کوالٹی اور نتیجہ (شامل، مکمل یا ناکام)۔\n\nاسکرینیں: ہر کھولی ہوئی اسکرین کا نام (Queue، History، Browser، Profile، Settings، Legal)، ہر وزٹ کے شروع ہونے کا درست وقت اور کتنی دیر رکے۔ ایپ پس منظر میں جائے تو ٹائمر رک جاتا ہے۔\n\nاکاؤنٹ: نام، ای میل، فون، ملک، سائن اِن کا طریقہ، شمولیت کی تاریخ، آخری فعال وقت اور کل ڈاؤن لوڈز۔\n\nڈیوائس: کمپنی، ماڈل، اینڈرائیڈ ورژن اور ایپ ورژن۔\n\nلائیو اسٹیٹس: آپ ابھی آن لائن ہیں یا نہیں، آخری سرگرمی کا وقت اور آپ کیا کر رہے ہیں (مثلاً Viewing Queue یا Browsing Web)۔\n\nایرر رپورٹس: ناکام ڈاؤن لوڈز کے ایرر کا متن (اس میں آپ کا آزمایا ہوا لنک ہو سکتا ہے) اور ایپ کی تکنیکی کریش رپورٹس۔\n\nپیغامات: ڈیولپر آپ کو نوٹیفیکیشن بھیج سکتا ہے۔ یہ ایپ کے اندر اور فون نوٹیفیکیشن میں Message from StreamSnap Team کے عنوان سے نظر آتا ہے۔"),
            s("کیا کبھی ریکارڈ نہیں ہوتا", "پاس ورڈ، ویب سائٹس کی کوکیز اور محفوظ لاگ اِن، براؤزنگ ہسٹری، ان ایپ براؤزر میں کھلے صفحات، فائلوں کا مواد، رابطے، پیغامات، کیمرہ، مائیکروفون اور مقام۔"),
            s("کون دیکھ سکتا ہے", "صرف ڈیولپر، ایک نجی ایڈمن ایپ میں جس کے لیے محفوظ سائن اِن چاہیے۔"),
            s("کس کام آتا ہے", "خراب سائٹس ڈھونڈنے، غلطیاں ٹھیک کرنے، غلط استعمال روکنے، یہ سمجھنے کہ کون سی اسکرینیں اور فیچرز استعمال ہوتے ہیں، اور ایپ بہتر کرنے کے لیے۔ اسے بیچا یا اشتہار کے لیے شیئر نہیں کیا جاتا۔"),
            s("اگر آپ اتفاق نہیں کرتے", "قبول نہ کریں یا ایپ استعمال کرنا بند کر دیں۔ آپ ڈیولپر سے اکاؤنٹ اور اس سے جڑا ڈیٹا حذف کرنے کو کہہ سکتے ہیں۔"),
        )
        else -> listOf(
            s("اسٹریم اسنیپ", "Capture any stream, instantly. اسٹریم اسنیپ بہت سی ویب سائٹس سے ویڈیو اور آڈیو آپ کے فون میں محفوظ کرتی ہے، قطار، ہسٹری، بلٹ اِن براؤزر اور کوالٹی کے انتخاب کے ساتھ۔"),
            s("ورژن", "StreamSnap 2.1.0"),
            s("ڈیولپر", "Made by Anas، کراچی، پاکستان کے ایک اکیلے ڈیولپر۔"),
            s("کس سے بنی", "yt-dlp (ڈاؤن لوڈ انجن)، FFmpeg (کنورژن)، Media3 (پلیئر)، Coil (تصاویر)، گوگل فائر بیس (اکاؤنٹس) اور جیٹ پیک کمپوز کے ساتھ میٹیریل 3۔ اسکرپٹ فونٹ Dancing Script، SIL اوپن فونٹ لائسنس کے تحت استعمال ہوا ہے۔"),
            s("شکریہ", "اسٹریم اسنیپ استعمال کرنے کا شکریہ۔ ذمہ داری سے استعمال کریں اور بنانے والوں کی محنت کا احترام کریں۔"),
        )
    }

    private fun support(lang: String): String {
        if (SUPPORT_EMAIL.isBlank()) return ""
        return when (lang) {
            "ro" -> " Email: $SUPPORT_EMAIL"
            "ur" -> " ای میل: $SUPPORT_EMAIL"
            else -> " Email: $SUPPORT_EMAIL"
        }
    }
}
