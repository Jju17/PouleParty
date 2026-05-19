package dev.rahier.pouleparty.ui.challenges

import android.content.SharedPreferences
import dev.rahier.pouleparty.AppConstants

/**
 * Persistent "I did it locally, not yet sent for validation" set, scoped
 * per game id. Mirrors iOS `PendingChallengeStore`. Each game's set is
 * stored under `pendingChallenges_<gameId>` as a comma-joined list of
 * challenge ids. Survives kill-app; cleared on game end.
 */
class PendingChallengeStore(private val prefs: SharedPreferences) {

    fun ids(gameId: String): Set<String> {
        val raw = prefs.getString(keyFor(gameId), null) ?: return emptySet()
        if (raw.isEmpty()) return emptySet()
        return raw.split(',').filter { it.isNotEmpty() }.toSet()
    }

    fun add(challengeId: String, gameId: String) {
        val current = ids(gameId).toMutableSet()
        if (!current.add(challengeId)) return
        prefs.edit().putString(keyFor(gameId), current.sorted().joinToString(",")).apply()
    }

    fun remove(challengeId: String, gameId: String) {
        val current = ids(gameId).toMutableSet()
        if (!current.remove(challengeId)) return
        val editor = prefs.edit()
        if (current.isEmpty()) {
            editor.remove(keyFor(gameId))
        } else {
            editor.putString(keyFor(gameId), current.sorted().joinToString(","))
        }
        editor.apply()
    }

    fun clear(gameId: String) {
        prefs.edit().remove(keyFor(gameId)).apply()
    }

    private fun keyFor(gameId: String) = "${AppConstants.PREF_PENDING_CHALLENGES}_$gameId"
}
