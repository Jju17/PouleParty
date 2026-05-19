//
//  ChallengeModelTests.swift
//  PoulePartyTests
//

import FirebaseFirestore
import Foundation
import Testing
@testable import PouleParty

struct ChallengeModelTests {

    private func decode(_ payload: [String: Any]) throws -> Challenge {
        try Firestore.Decoder().decode(Challenge.self, from: payload)
    }

    @Test func decodesLegacyDocWithoutType() throws {
        let challenge = try decode([
            "title": "Drink a beer",
            "body": "At any partner bar.",
            "points": 10,
        ])
        #expect(challenge.title == "Drink a beer")
        #expect(challenge.body == "At any partner bar.")
        #expect(challenge.points == 10)
        #expect(challenge.type == .oneShot)
        #expect(challenge.location == nil)
        #expect(challenge.proximityRadiusMeters == nil)
        #expect(challenge.partner == nil)
    }

    @Test func decodesEmptyDocument() throws {
        let challenge = try decode([:])
        #expect(challenge.title == "")
        #expect(challenge.body == "")
        #expect(challenge.points == 0)
        #expect(challenge.type == .oneShot)
        #expect(challenge.location == nil)
        #expect(challenge.proximityRadiusMeters == nil)
        #expect(challenge.partner == nil)
        #expect(challenge.lastUpdated == nil)
    }

    @Test func decodesOneShotTypeExplicitly() throws {
        let challenge = try decode([
            "title": "Street stunt",
            "type": "oneShot",
            "points": 50,
        ])
        #expect(challenge.type == .oneShot)
    }

    @Test func decodesRepeatableType() throws {
        let challenge = try decode([
            "title": "Bar partner",
            "type": "repeatable",
            "points": 5,
        ])
        #expect(challenge.type == .repeatable)
    }

    @Test func unknownTypeFallsBackToOneShot() throws {
        let challenge = try decode([
            "title": "Future type",
            "type": "dailyOnce",
            "points": 30,
        ])
        #expect(challenge.type == .oneShot)
    }

    @Test func decodesLocationAndProximity() throws {
        let challenge = try decode([
            "title": "Bar Le Cirio",
            "type": "repeatable",
            "points": 5,
            "location": GeoPoint(latitude: 50.8503, longitude: 4.3517),
            "proximityRadiusMeters": 50,
            "partner": "InBev",
        ])
        #expect(challenge.location?.latitude == 50.8503)
        #expect(challenge.location?.longitude == 4.3517)
        #expect(challenge.proximityRadiusMeters == 50)
        #expect(challenge.partner == "InBev")
    }

    @Test func proximityRadiusCanBeNilExplicitly() throws {
        // `nil` means "no proximity check at submission time" — distinct
        // from the default 100 m the migration sets.
        let challenge = try decode([
            "title": "Anywhere challenge",
            "type": "oneShot",
            "location": GeoPoint(latitude: 0, longitude: 0),
        ])
        #expect(challenge.proximityRadiusMeters == nil)
    }

    @Test func roundTripPreservesAllFields() throws {
        let original = Challenge(
            firestoreId: "challenge-id",
            title: "Sing the anthem",
            body: "At the Grand-Place.",
            points: 150,
            lastUpdated: Timestamp(seconds: 1_700_000_000, nanoseconds: 0),
            type: .repeatable,
            location: GeoPoint(latitude: 50.8467, longitude: 4.3525),
            proximityRadiusMeters: 25,
            partner: "Brussels Tourism"
        )
        let encoded = try Firestore.Encoder().encode(original)
        let decoded = try Firestore.Decoder().decode(Challenge.self, from: encoded)
        #expect(decoded.title == original.title)
        #expect(decoded.body == original.body)
        #expect(decoded.points == original.points)
        #expect(decoded.lastUpdated == original.lastUpdated)
        #expect(decoded.type == original.type)
        #expect(decoded.location?.latitude == original.location?.latitude)
        #expect(decoded.location?.longitude == original.location?.longitude)
        #expect(decoded.proximityRadiusMeters == original.proximityRadiusMeters)
        #expect(decoded.partner == original.partner)
    }

    @Test func defaultMemberwiseInitMatchesDecodedDefaults() {
        let memberwise = Challenge()
        #expect(memberwise.type == .oneShot)
        #expect(memberwise.location == nil)
        #expect(memberwise.proximityRadiusMeters == nil)
        #expect(memberwise.partner == nil)
    }

    @Test func challengeTypeRawValues() {
        // Raw strings are the on-disk contract shared with Android.
        #expect(Challenge.ChallengeType.oneShot.rawValue == "oneShot")
        #expect(Challenge.ChallengeType.repeatable.rawValue == "repeatable")
    }

    @Test func challengeTypeIsCaseIterable() {
        #expect(Challenge.ChallengeType.allCases.count == 2)
    }
}
