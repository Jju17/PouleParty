package dev.rahier.pouleparty.ui.challenges

import android.content.SharedPreferences

/**
 * In-memory [SharedPreferences] that keeps the JVM unit-test deterministic.
 * Only the string + remove paths used by [PendingChallengeStore] are
 * implemented; every other accessor falls back to defaults.
 */
class FakePreferences : SharedPreferences {
    private val store = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = store.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? =
        store[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST")
        (store[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = store[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = store[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = store[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        store[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = store.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = Editor()

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = values
        }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }
        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) removals.add(key)
        }
        override fun clear(): SharedPreferences.Editor = apply { clear = true }
        override fun commit(): Boolean {
            applyChanges()
            return true
        }
        override fun apply() {
            applyChanges()
        }
        private fun applyChanges() {
            if (clear) store.clear()
            removals.forEach { store.remove(it) }
            pending.forEach { (key, value) ->
                if (value == null) store.remove(key) else store[key] = value
            }
            pending.clear()
            removals.clear()
            clear = false
        }
    }
}
