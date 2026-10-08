package dev.rahier.pouleparty.model

object AdminCode {
    const val VALUE = "jujurahier"
}

/**
 * Compiled fallback for the QA-debug code, entered via the same Create
 * Party long-press modal as the admin code. A game created with this code
 * gets `Game.isDebugGame = true` (short timing + the on-map QA panel).
 * Overridable at runtime via Remote Config key `qa_debug_code`; setting the
 * Remote Config value to an empty string disables debug-game creation
 * entirely (the match check requires a non-empty code).
 */
object DebugCode {
    const val VALUE = "qadebug"
}
