# Release build uses R8. Keep rules for the download engine (python/ffmpeg via JNI + reflection),
# the app's own code (it uses reflection in a few places) and Firebase / Credential Manager.
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**
-keep class com.anas.streamsnap.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
