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
    var title: String = ""
    var body: String = ""
    var points: Int = 0
    var lastUpdated: Timestamp?
    var type: ChallengeType = .oneShot
    var location: GeoPoint? = nil
    var proximityRadiusMeters: Int? = nil
    var partner: String? = nil

    var id: String { firestoreId ?? "challenge::\(title)::\(points)" }

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
        case title
        case body
        case points
        case lastUpdated
        case type
        case location
        case proximityRadiusMeters
        case partner
    }

    init(
        firestoreId: String? = nil,
        title: String = "",
        body: String = "",
        points: Int = 0,
        lastUpdated: Timestamp? = nil,
        type: ChallengeType = .oneShot,
        location: GeoPoint? = nil,
        proximityRadiusMeters: Int? = nil,
        partner: String? = nil
    ) {
        self.firestoreId = firestoreId
        self.title = title
        self.body = body
        self.points = points
        self.lastUpdated = lastUpdated
        self.type = type
        self.location = location
        self.proximityRadiusMeters = proximityRadiusMeters
        self.partner = partner
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        firestoreId = try c.decodeIfPresent(String.self, forKey: .firestoreId)
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        body = try c.decodeIfPresent(String.self, forKey: .body) ?? ""
        points = try c.decodeIfPresent(Int.self, forKey: .points) ?? 0
        lastUpdated = try c.decodeIfPresent(Timestamp.self, forKey: .lastUpdated)
        type = try c.decodeIfPresent(ChallengeType.self, forKey: .type) ?? .oneShot
        location = try c.decodeIfPresent(GeoPoint.self, forKey: .location)
        proximityRadiusMeters = try c.decodeIfPresent(Int.self, forKey: .proximityRadiusMeters)
        partner = try c.decodeIfPresent(String.self, forKey: .partner)
    }
}

extension Challenge {
    static var mock: Challenge {
        Challenge(
            firestoreId: "mock-challenge",
            title: "Climb the highest roof",
            body: "Take a selfie on top of the tallest rooftop you can find.",
            points: 50,
            lastUpdated: Timestamp(date: .now),
            type: .oneShot
        )
    }
}
