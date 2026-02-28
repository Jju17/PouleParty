# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK ProGuard configuration.

# ── App models (Firebase Firestore deserialization) ──
-keep class dev.rahier.pouleparty.model.** { *; }

# ── Firebase ──
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keepattributes Signature
-keepattributes *Annotation*

# ── Google Play Services / Maps ──
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ── Hilt / Dagger ──
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-dontwarn dagger.hilt.**

# ── Jetpack Compose ──
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ── Kotlin Coroutines ──
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# ── Keep enum values (used by Firestore) ──
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Keep Parcelable implementations ──
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
