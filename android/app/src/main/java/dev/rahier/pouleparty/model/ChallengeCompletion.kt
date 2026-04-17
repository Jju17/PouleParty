package dev.rahier.pouleparty.model

/**
 * Per-hunter record of completed challenges inside a specific game.
 * Stored in `/games/{gameId}/challengeCompletions/{hunterId}`.
 *
 * The document id matches the hunter's user id.
 */
data class ChallengeCompletion(
    val hunterId: String = "",
    val completedChallengeIds: List<String> = emptyList(),
    val totalPoints: Int = 0,
    val teamName: String = ""
)
