package dev.rahier.pouleparty.model

data class ChallengeCompletion(
    val hunterId: String = "",
    val validatedChallengeIds: List<String> = emptyList(),
    val repeatableCounts: Map<String, Int> = emptyMap(),
    val totalPoints: Int = 0,
    val teamName: String = ""
)
