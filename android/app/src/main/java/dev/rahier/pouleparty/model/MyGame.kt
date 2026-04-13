package dev.rahier.pouleparty.model

/**
 * A game shown in the "My Games" list, tagged with the user's role (creator or hunter).
 */
enum class MyGameRole { CREATOR, HUNTER }

data class MyGame(
    val game: Game,
    val role: MyGameRole
)
