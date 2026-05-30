//
//  AdminCode.swift
//  PouleParty
//
//  Hardcoded code that gates the admin-mode wizard (lifts the maxPlayers
//  cap from 5 to 500). Obfuscation only — see PP-9 / PP-45: the worst
//  scenario if a user guesses the code is creating a >5-player party,
//  which has no business consequence.
//

enum AdminCode {
    static let value = "jujurahier"
}

/// Compiled fallback for the QA-debug code, entered via the same Create
/// Party long-press modal as the admin code. A game created with this code
/// gets `Game.isDebugGame = true` (short timing + the on-map QA panel).
/// Overridable at runtime via Remote Config key `qa_debug_code`; setting
/// the Remote Config value to an empty string disables debug-game creation
/// entirely (the match check requires a non-empty code).
enum DebugCode {
    static let value = "qadebug"
}
