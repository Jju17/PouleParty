package dev.rahier.pouleparty.util

import android.content.SharedPreferences

/**
 * Returns the trimmed string value for [key], or empty string when missing.
 * Mirrors the `(prefs.getString(key, "") ?: "").trim()` idiom used across ViewModels.
 */
fun SharedPreferences.getTrimmedString(key: String): String =
    (getString(key, "") ?: "").trim()
