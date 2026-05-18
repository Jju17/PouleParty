# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK ProGuard configuration.

# ── App models (Firebase Firestore deserialization) ──
# Any data class passed to `DocumentSnapshot.toObject(T::class.java)` needs
# its no-arg constructor preserved or Firestore's CustomClassMapper throws
# "Class <obfuscated> does not define a no-argument constructor" (see the
# 1.9.0 Crashlytics issue b050a0b8 on PowerUp in release builds — rule
# covered `model.**` but `powerups.model.PowerUp` was outside that scope).
# Keep every model package used by toObject.
-keep class dev.rahier.pouleparty.model.** { *; }
-keep class dev.rahier.pouleparty.powerups.model.** { *; }

# ── Firebase ──
# AND-L4 (store-audit 2026-05-18): narrowed from the catch-all
# `com.google.firebase.**` to the specific submodules we actually import
# (grepped from `com.google.firebase.*` imports under /java). The Firebase
# SDK ships its own consumer ProGuard rules per module — keeping a narrow
# list here is a defense-in-depth for the data classes we hand to
# `DocumentSnapshot.toObject` and the callable response payloads that
# Firestore/Functions deserialize via reflection. If a new Firebase
# module is added, append its package here.
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.analytics.** { *; }
-keep class com.google.firebase.messaging.** { *; }
-keep class com.google.firebase.functions.** { *; }
-keep class com.google.firebase.appcheck.** { *; }
-keep class com.google.firebase.crashlytics.** { *; }
-keep class com.google.firebase.FirebaseApp { *; }
-keep class com.google.firebase.Timestamp { *; }
# Keep the broad `dontwarn` — Firebase internals chain to optional deps
# (gms-tasks, datastore protos, etc.) and a narrow list leaves R8 chasing
# missing classes on unrelated build paths.
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
