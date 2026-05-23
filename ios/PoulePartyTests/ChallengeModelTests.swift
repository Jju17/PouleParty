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

    @Test func decodesMinimalDocument() throws {
        let challenge = try decode([
            "points": 10,
            "titleByLocale": ["fr": "Boire une bière"],
            "bodyByLocale": ["fr": "Au bar partenaire."],
        ])
        #expect(challenge.points == 10)
        #expect(challenge.type == .oneShot)
        #expect(challenge.location == nil)
        #expect(challenge.proximityRadiusMeters == nil)
        #expect(challenge.partner == nil)
        #expect(challenge.level == 1)
        #expect(challenge.number == 0)
        #expect(challenge.titleByLocale["fr"] == "Boire une bière")
    }

    @Test func decodesEmptyDocument() throws {
        let challenge = try decode([:])
        #expect(challenge.points == 0)
        #expect(challenge.type == .oneShot)
        #expect(challenge.location == nil)
        #expect(challenge.proximityRadiusMeters == nil)
        #expect(challenge.partner == nil)
        #expect(challenge.lastUpdated == nil)
        #expect(challenge.level == 1)
        #expect(challenge.number == 0)
        #expect(challenge.titleByLocale.isEmpty)
        #expect(challenge.bodyByLocale.isEmpty)
    }

    @Test func decodesExplicitLevelAndNumber() throws {
        let challenge = try decode([
            "titleByLocale": ["fr": "Pyramide"],
            "level": 2,
            "number": 7,
        ])
        #expect(challenge.level == 2)
        #expect(challenge.number == 7)
    }

    @Test func decodesNumberZeroAsSentinel() throws {
        let challenge = try decode([
            "titleByLocale": ["fr": "Pending number"],
            "level": 3,
            "number": 0,
        ])
        #expect(challenge.number == 0)
    }

    @Test func decodesOneShotTypeExplicitly() throws {
        let challenge = try decode([
            "titleByLocale": ["fr": "Street stunt"],
            "type": "oneShot",
            "points": 50,
        ])
        #expect(challenge.type == .oneShot)
    }

    @Test func decodesRepeatableType() throws {
        let challenge = try decode([
            "titleByLocale": ["fr": "Bar partner"],
            "type": "repeatable",
            "points": 5,
        ])
        #expect(challenge.type == .repeatable)
    }

    @Test func unknownTypeFallsBackToOneShot() throws {
        let challenge = try decode([
            "titleByLocale": ["fr": "Future type"],
            "type": "dailyOnce",
            "points": 30,
        ])
        #expect(challenge.type == .oneShot)
    }

    @Test func decodesLocationAndProximity() throws {
        let challenge = try decode([
            "titleByLocale": ["fr": "Bar Le Cirio"],
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
        let challenge = try decode([
            "titleByLocale": ["fr": "Anywhere challenge"],
            "type": "oneShot",
            "location": GeoPoint(latitude: 0, longitude: 0),
        ])
        #expect(challenge.proximityRadiusMeters == nil)
    }

    @Test func roundTripPreservesAllFields() throws {
        let original = Challenge(
            firestoreId: "challenge-id",
            points: 150,
            lastUpdated: Timestamp(seconds: 1_700_000_000, nanoseconds: 0),
            type: .repeatable,
            location: GeoPoint(latitude: 50.8467, longitude: 4.3525),
            proximityRadiusMeters: 25,
            partner: "Brussels Tourism",
            level: 2,
            number: 13,
            titleByLocale: ["fr": "Chanter la Brabançonne"],
            bodyByLocale: ["fr": "Au milieu de la Grand-Place."]
        )
        let encoded = try Firestore.Encoder().encode(original)
        let decoded = try Firestore.Decoder().decode(Challenge.self, from: encoded)
        #expect(decoded.points == original.points)
        #expect(decoded.lastUpdated == original.lastUpdated)
        #expect(decoded.type == original.type)
        #expect(decoded.location?.latitude == original.location?.latitude)
        #expect(decoded.location?.longitude == original.location?.longitude)
        #expect(decoded.proximityRadiusMeters == original.proximityRadiusMeters)
        #expect(decoded.partner == original.partner)
        #expect(decoded.level == original.level)
        #expect(decoded.number == original.number)
        #expect(decoded.titleByLocale == original.titleByLocale)
        #expect(decoded.bodyByLocale == original.bodyByLocale)
    }

    @Test func defaultMemberwiseInitMatchesDecodedDefaults() {
        let memberwise = Challenge()
        #expect(memberwise.type == .oneShot)
        #expect(memberwise.location == nil)
        #expect(memberwise.proximityRadiusMeters == nil)
        #expect(memberwise.partner == nil)
        #expect(memberwise.level == 1)
        #expect(memberwise.number == 0)
        #expect(memberwise.titleByLocale.isEmpty)
        #expect(memberwise.bodyByLocale.isEmpty)
    }

    @Test func challengeTypeRawValues() {
        #expect(Challenge.ChallengeType.oneShot.rawValue == "oneShot")
        #expect(Challenge.ChallengeType.repeatable.rawValue == "repeatable")
    }

    @Test func challengeTypeIsCaseIterable() {
        #expect(Challenge.ChallengeType.allCases.count == 2)
    }

    @Test func decodesTitleAndBodyByLocale() throws {
        let challenge = try decode([
            "titleByLocale": [
                "fr": "Chanter la Brabançonne",
                "en": "Sing the anthem",
                "nl": "Zing het volkslied",
            ],
            "bodyByLocale": [
                "fr": "Maintenant",
                "en": "Now",
                "nl": "Nu",
            ],
        ])
        #expect(challenge.titleByLocale["fr"] == "Chanter la Brabançonne")
        #expect(challenge.bodyByLocale["nl"] == "Nu")
    }

    @Test func localizedTitleReturnsRequestedLocaleWhenPresent() {
        let challenge = Challenge(
            titleByLocale: ["fr": "FR", "en": "EN", "nl": "NL"]
        )
        #expect(challenge.localizedTitle("fr") == "FR")
        #expect(challenge.localizedTitle("en") == "EN")
        #expect(challenge.localizedTitle("nl") == "NL")
    }

    @Test func localizedTitleFallsBackToFrWhenLocaleMissing() {
        let challenge = Challenge(titleByLocale: ["fr": "FR"])
        #expect(challenge.localizedTitle("en") == "FR")
        #expect(challenge.localizedTitle("de") == "FR")
    }

    @Test func localizedTitleReturnsEmptyWhenMapIsEmpty() {
        let challenge = Challenge(titleByLocale: [:])
        #expect(challenge.localizedTitle("fr") == "")
        #expect(challenge.localizedTitle("en") == "")
    }

    @Test func localizedTitleTreatsEmptyStringAsMissing() {
        let challenge = Challenge(
            titleByLocale: ["fr": "FR", "en": ""]
        )
        #expect(challenge.localizedTitle("en") == "FR")
    }

    @Test func localizedBodyFollowsSameCascade() {
        let challenge = Challenge(
            bodyByLocale: ["fr": "FR body"]
        )
        #expect(challenge.localizedBody("fr") == "FR body")
        #expect(challenge.localizedBody("en") == "FR body")

        let challenge2 = Challenge(bodyByLocale: [:])
        #expect(challenge2.localizedBody("fr") == "")
    }
}
