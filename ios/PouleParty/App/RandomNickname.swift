//
//  RandomNickname.swift
//  PouleParty
//

import Foundation

enum RandomNickname {
    private static let adjectives = [
        "Brave", "Quick", "Sneaky", "Wild", "Bold", "Sly", "Swift", "Fierce",
        "Clever", "Speedy", "Sharp", "Lucky", "Witty", "Funky", "Crazy", "Spicy",
        "Smooth", "Cosmic", "Mighty", "Tiny", "Mega", "Super", "Ninja", "Stealth",
        "Shadow", "Lightning", "Thunder", "Mystic", "Royal", "Lazy", "Cosy",
        "Feathered", "Fluffy", "Grumpy", "Happy", "Jolly", "Loud", "Rapid",
    ]
    private static let nouns = [
        "Chicken", "Hunter", "Fox", "Wolf", "Bear", "Eagle", "Tiger", "Lion",
        "Panda", "Penguin", "Falcon", "Hawk", "Shark", "Dragon", "Phoenix",
        "Knight", "Ghost", "Wizard", "Ranger", "Pilot", "Pirate", "Hero",
        "Captain", "Rebel", "Rocket", "Comet", "Star", "Storm", "Bolt", "Coop",
        "Beak", "Feather", "Wing", "Egg", "Hen", "Rooster",
    ]

    /// Returns a fresh "AdjectiveNoun##" pseudonym for users who skip the
    /// nickname step. Capped at `AppConstants.nicknameMaxLength` chars to
    /// match the manual input contract (and the in-game label budget).
    static func generate() -> String {
        let adj = adjectives.randomElement() ?? "Brave"
        let noun = nouns.randomElement() ?? "Chicken"
        let suffix = Int.random(in: 0...99)
        let candidate = "\(adj)\(noun)\(suffix)"
        if candidate.count <= AppConstants.nicknameMaxLength { return candidate }
        return String(candidate.prefix(AppConstants.nicknameMaxLength))
    }
}
