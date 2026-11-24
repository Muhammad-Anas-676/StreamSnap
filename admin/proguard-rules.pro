-keep class com.anas.streamsnap.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
# Fingerprint lock: androidx.biometric shows its prompt through a fragment, so R8 must not strip or rename them.
-keep class androidx.biometric.** { *; }
-keep class androidx.fragment.** { *; }
-dontwarn androidx.biometric.**
