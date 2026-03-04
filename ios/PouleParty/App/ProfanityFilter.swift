//
//  ProfanityFilter.swift
//  PouleParty
//

import Foundation

enum ProfanityFilter {

    /// Blocked words (FR + EN). Checked as whole-word substrings (case-insensitive).
    private static let blockedWords: Set<String> = [
        // English
        "fuck", "shit", "ass", "asshole", "bitch", "bastard", "dick", "cock",
        "pussy", "cunt", "whore", "slut", "fag", "faggot", "nigger", "nigga",
        "retard", "rape", "rapist", "nazi", "penis", "vagina", "dildo",
        "wanker", "twat", "bollocks", "prick", "motherfucker",
        // French
        "merde", "putain", "salope", "pute", "connard", "connasse", "enculer",
        "encule", "enculé", "nique", "ntm", "fdp", "pd", "tapette", "gouine",
        "batard", "bâtard", "bordel", "bite", "couille", "branleur",
        "branleuse", "tg", "negro", "nègre", "pédé", "pédale",
    ]

    /// Returns `true` when the nickname contains a blocked word.
    static func containsProfanity(_ text: String) -> Bool {
        let lowered = text.lowercased()
            .folding(options: .diacriticInsensitive, locale: .current)

        // Also strip common leetspeak substitutions
        let cleaned = lowered
            .replacingOccurrences(of: "0", with: "o")
            .replacingOccurrences(of: "1", with: "i")
            .replacingOccurrences(of: "3", with: "e")
            .replacingOccurrences(of: "4", with: "a")
            .replacingOccurrences(of: "5", with: "s")
            .replacingOccurrences(of: "@", with: "a")
            .replacingOccurrences(of: "$", with: "s")

        for word in blockedWords {
            let normalizedWord = word.lowercased()
                .folding(options: .diacriticInsensitive, locale: .current)
            if cleaned.contains(normalizedWord) {
                return true
            }
        }
        return false
    }
}
