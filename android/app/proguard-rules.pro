# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK ProGuard configuration.

# ── App models (Firebase Firestore deserialization) ──
# Every class decoded with `toObject` keeps its no-argument constructor and fields.
-keep class dev.rahier.pouleparty.model.** { *; }
-keep class dev.rahier.pouleparty.powerups.model.** { *; }

# ── Firebase ──
# Only the Firebase modules the app imports; add a module here when the app starts using it.
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.analytics.** { *; }
-keep class com.google.firebase.messaging.** { *; }
-keep class com.google.firebase.functions.** { *; }
-keep class com.google.firebase.appcheck.** { *; }
-keep class com.google.firebase.crashlytics.** { *; }
-keep class com.google.firebase.FirebaseApp { *; }
-keep class com.google.firebase.Timestamp { *; }
# Firebase internals reference optional dependencies; a narrow list leaves R8 chasing missing classes.
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
