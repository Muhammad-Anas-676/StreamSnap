package com.anas.streamsnap.core.cloud

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.anas.streamsnap.core.t
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

/** Sign-in with email/password or Google. Results are returned as Kotlin [Result]s with readable errors. */
object AuthRepo {
    val user: FirebaseUser? get() = if (Cloud.ready) Cloud.auth().currentUser else null

    val isGuest: Boolean get() = user?.isAnonymous == true

    val provider: String
        get() = if (isGuest) "guest" else when (user?.providerData?.firstOrNull { it.providerId != "firebase" }?.providerId) {
            "google.com" -> "google"
            "password" -> "email"
            else -> "email"
        }

    suspend fun signIn(email: String, password: String): Result<FirebaseUser> = try {
        val r = Cloud.auth().signInWithEmailAndPassword(email.trim(), password).await()
        Result.success(r.user!!)
    } catch (e: Throwable) {
        Result.failure(Exception(friendly(e)))
    }

    suspend fun signUp(email: String, password: String, name: String): Result<FirebaseUser> = try {
        val r = Cloud.auth().createUserWithEmailAndPassword(email.trim(), password).await()
        val u = r.user!!
        u.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
        Result.success(u)
    } catch (e: Throwable) {
        Result.failure(Exception(friendly(e)))
    }

    /** Guest session: an anonymous Firebase account that can be upgraded later with [linkEmail] or [linkGoogle]. */
    suspend fun signInGuest(): Result<FirebaseUser> = try {
        Result.success(Cloud.auth().signInAnonymously().await().user!!)
    } catch (e: Throwable) {
        Result.failure(Exception(friendly(e)))
    }

    /** Turns the guest account into an email account (same user id, so the history stays). */
    suspend fun linkEmail(email: String, password: String, name: String): Result<FirebaseUser> = try {
        val u = Cloud.auth().currentUser ?: throw IllegalStateException("Not signed in")
        val r = u.linkWithCredential(EmailAuthProvider.getCredential(email.trim(), password)).await()
        val nu = r.user ?: u
        nu.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
        Result.success(nu)
    } catch (e: Throwable) {
        Result.failure(Exception(friendly(e)))
    }

    /** Turns the guest account into a Google account. */
    suspend fun linkGoogle(activity: Activity): Result<FirebaseUser> = try {
        val u = Cloud.auth().currentUser ?: throw IllegalStateException("Not signed in")
        val id = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        if (id == 0) throw IllegalStateException("google-services.json is missing the Google sign-in client")
        val option = GetGoogleIdOption.Builder().setServerClientId(activity.getString(id)).setFilterByAuthorizedAccounts(false).build()
        val res = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val cred = res.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val g = GoogleIdTokenCredential.createFrom(cred.data)
            val r = u.linkWithCredential(GoogleAuthProvider.getCredential(g.idToken, null)).await()
            Result.success(r.user ?: u)
        } else {
            Result.failure(Exception(t("Google sign-in failed", "Google sign-in nakam hua", "گوگل سائن اِن ناکام ہوا")))
        }
    } catch (e: GetCredentialCancellationException) {
        Result.failure(Exception(""))
    } catch (e: Throwable) {
        Result.failure(Exception(friendly(e)))
    }

    suspend fun sendReset(email: String): Result<Unit> = try {
        Cloud.auth().sendPasswordResetEmail(email.trim()).await()
        Result.success(Unit)
    } catch (e: Throwable) {
        Result.failure(Exception(friendly(e)))
    }

    /** Google account picker through Credential Manager. The web client id comes from google-services.json. */
    suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser> = try {
        val id = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        if (id == 0) throw IllegalStateException("google-services.json is missing the Google sign-in client")
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(activity.getString(id))
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val res = CredentialManager.create(activity).getCredential(activity, request)
        val cred = res.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val g = GoogleIdTokenCredential.createFrom(cred.data)
            val r = Cloud.auth().signInWithCredential(GoogleAuthProvider.getCredential(g.idToken, null)).await()
            Result.success(r.user!!)
        } else {
            Result.failure(Exception(t("Google sign-in failed", "Google sign-in nakam hua", "گوگل سائن اِن ناکام ہوا")))
        }
    } catch (e: GetCredentialCancellationException) {
        Result.failure(Exception(""))
    } catch (e: Throwable) {
        Result.failure(Exception(friendly(e)))
    }

    suspend fun signOut(ctx: Context) {
        try { Cloud.auth().signOut() } catch (_: Throwable) { }
        try { CredentialManager.create(ctx).clearCredentialState(ClearCredentialStateRequest()) } catch (_: Throwable) { }
    }

    fun friendly(e: Throwable): String {
        if (e is FirebaseNetworkException) {
            return t("No internet connection. Check your network and try again.",
                "Internet nahi hai. Network check karke dobara koshish karein.",
                "انٹرنیٹ نہیں ہے۔ نیٹ ورک چیک کر کے دوبارہ کوشش کریں۔")
        }
        val code = (e as? FirebaseAuthException)?.errorCode ?: ""
        return when (code) {
            "ERROR_INVALID_EMAIL" -> t("That email address is not valid.", "Yeh email address durust nahi.", "یہ ای میل ایڈریس درست نہیں۔")
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" ->
                t("Wrong email or password.", "Email ya password ghalat hai.", "ای میل یا پاس ورڈ غلط ہے۔")
            "ERROR_EMAIL_ALREADY_IN_USE" -> t("This email already has an account. Try signing in.", "Is email ka account pehle se hai. Sign in karein.", "اس ای میل کا اکاؤنٹ پہلے سے موجود ہے۔ سائن اِن کریں۔")
            "ERROR_WEAK_PASSWORD" -> t("Password is too weak. Use at least 6 characters.", "Password kamzor hai. Kam az kam 6 huroof rakhein.", "پاس ورڈ کمزور ہے۔ کم از کم 6 حروف رکھیں۔")
            "ERROR_USER_DISABLED" -> t("This account has been disabled.", "Yeh account band kar diya gaya hai.", "یہ اکاؤنٹ بند کر دیا گیا ہے۔")
            "ERROR_CREDENTIAL_ALREADY_IN_USE" -> t("That account already exists. Sign out and sign in with it instead.", "Yeh account pehle se maujood hai. Sign out karke usi se sign in karein.", "یہ اکاؤنٹ پہلے سے موجود ہے۔ سائن آؤٹ کر کے اسی سے سائن اِن کریں۔")
            "ERROR_OPERATION_NOT_ALLOWED", "ERROR_ADMIN_RESTRICTED_OPERATION" -> t("Guest sign-in is not turned on yet. Please try another way.", "Guest sign-in abhi on nahi hai. Koi aur tareeqa try karein.", "گیسٹ سائن اِن ابھی آن نہیں ہے۔ کوئی اور طریقہ آزمائیں۔")
            "ERROR_TOO_MANY_REQUESTS" -> t("Too many attempts. Please wait a while and try again.", "Bohat zyada koshishein. Thori der baad dobara try karein.", "بہت زیادہ کوششیں۔ تھوڑی دیر بعد دوبارہ کوشش کریں۔")
            else -> e.message?.takeIf { it.isNotBlank() && it.length < 140 }
                ?: t("Something went wrong. Please try again.", "Kuch masla aa gaya. Dobara koshish karein.", "کچھ مسئلہ آ گیا۔ دوبارہ کوشش کریں۔")
        }
    }
}
