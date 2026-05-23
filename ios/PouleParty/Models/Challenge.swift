//
//  Challenge.swift
//  PouleParty
//
//  A challenge hunters can complete during a game to score points.
//  Managed from the Firebase Console — clients are read-only.
//

import FirebaseFirestore
import Foundation

struct Challenge: Codable, Equatable, Identifiable {
    @DocumentID var firestoreId: String?
    var points: Int = 0
    var lastUpdated: Timestamp?
    var type: ChallengeType = .oneShot
    var location: GeoPoint? = nil
    var proximityRadiusMeters: Int? = nil
    var partner: String? = nil
    var level: Int = 1
    // `0` is a sentinel for "not yet numbered". `migrateChallengesV2`
    // assigns the first free integer within `level` to every doc
    // missing or set to 0; new challenges created via the Console must
    // ship with `number > 0`.
    var number: Int = 0
    var titleByLocale: [String: String] = [:]
    var bodyByLocale: [String: String] = [:]

    var id: String { firestoreId ?? "challenge::\(level)::\(number)::\(points)" }

    /// Locale → text with a 2-level cascade: requested locale, then
    /// `"fr"` (the D-Day FR-first audience). Empty strings count as
    /// missing so a partially-populated doc falls through cleanly.
    /// Returns `""` when both are missing — that surfaces the bug
    /// immediately in the UI so the admin populates the maps.
    func localizedTitle(_ locale: String) -> String {
        if let v = titleByLocale[locale], !v.isEmpty { return v }
        if let v = titleByLocale["fr"], !v.isEmpty { return v }
        return ""
    }

    func localizedBody(_ locale: String) -> String {
        if let v = bodyByLocale[locale], !v.isEmpty { return v }
        if let v = bodyByLocale["fr"], !v.isEmpty { return v }
        return ""
    }

    enum ChallengeType: String, Codable, CaseIterable, Equatable {
        case oneShot
        case repeatable

        init(from decoder: Decoder) throws {
            let raw = try decoder.singleValueContainer().decode(String.self)
            self = ChallengeType(rawValue: raw) ?? .oneShot
        }
    }

    enum CodingKeys: String, CodingKey {
        case firestoreId
        case points
        case lastUpdated
        case type
        case location
        case proximityRadiusMeters
        case partner
        case level
        case number
        case titleByLocale
        case bodyByLocale
    }

    init(
        firestoreId: String? = nil,
        points: Int = 0,
        lastUpdated: Timestamp? = nil,
        type: ChallengeType = .oneShot,
        location: GeoPoint? = nil,
        proximityRadiusMeters: Int? = nil,
        partner: String? = nil,
        level: Int = 1,
        number: Int = 0,
        titleByLocale: [String: String] = [:],
        bodyByLocale: [String: String] = [:]
    ) {
        self.firestoreId = firestoreId
        self.points = points
        self.lastUpdated = lastUpdated
        self.type = type
        self.location = location
        self.proximityRadiusMeters = proximityRadiusMeters
        self.partner = partner
        self.level = level
        self.number = number
        self.titleByLocale = titleByLocale
        self.bodyByLocale = bodyByLocale
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        firestoreId = try c.decodeIfPresent(String.self, forKey: .firestoreId)
        points = try c.decodeIfPresent(Int.self, forKey: .points) ?? 0
        lastUpdated = try c.decodeIfPresent(Timestamp.self, forKey: .lastUpdated)
        type = try c.decodeIfPresent(ChallengeType.self, forKey: .type) ?? .oneShot
        location = try c.decodeIfPresent(GeoPoint.self, forKey: .location)
        proximityRadiusMeters = try c.decodeIfPresent(Int.self, forKey: .proximityRadiusMeters)
        partner = try c.decodeIfPresent(String.self, forKey: .partner)
        level = try c.decodeIfPresent(Int.self, forKey: .level) ?? 1
        number = try c.decodeIfPresent(Int.self, forKey: .number) ?? 0
        titleByLocale = try c.decodeIfPresent([String: String].self, forKey: .titleByLocale) ?? [:]
        bodyByLocale = try c.decodeIfPresent([String: String].self, forKey: .bodyByLocale) ?? [:]
    }
}

extension Challenge {
    static var mock: Challenge {
        Challenge(
            firestoreId: "mock-challenge",
            points: 50,
            lastUpdated: Timestamp(date: .now),
            type: .oneShot,
            level: 1,
            number: 1,
            titleByLocale: [
                "fr": "Grimper sur le toit le plus haut",
                "en": "Climb the highest roof",
                "nl": "Klim op het hoogste dak",
            ],
            bodyByLocale: [
                "fr": "Selfie sur le plus haut toit que tu trouves.",
                "en": "Take a selfie on top of the tallest rooftop you can find.",
                "nl": "Maak een selfie op het hoogste dak dat je kunt vinden.",
            ]
        )
    }
}
