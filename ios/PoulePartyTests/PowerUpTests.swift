//
//  PowerUpTests.swift
//  PoulePartyTests
//

import CoreLocation
import Testing
import FirebaseFirestore
@testable import PouleParty

struct PowerUpTests {

    @Test func hunterPowerUpsAreCorrectlyClassified() {
        #expect(PowerUp.PowerUpType.zonePreview.isHunterPowerUp == true)
        #expect(PowerUp.PowerUpType.radarPing.isHunterPowerUp == true)
        #expect(PowerUp.PowerUpType.invisibility.isHunterPowerUp == false)
        #expect(PowerUp.PowerUpType.zoneFreeze.isHunterPowerUp == false)
    }

    @Test func durationValuesAreCorrect() {
        #expect(PowerUp.PowerUpType.zonePreview.durationSeconds == nil)
        #expect(PowerUp.PowerUpType.radarPing.durationSeconds == 30)
        #expect(PowerUp.PowerUpType.invisibility.durationSeconds == 30)
        #expect(PowerUp.PowerUpType.zoneFreeze.durationSeconds == 120)
    }

    @Test func isCollectedReturnsTrueWhenSet() {
        var powerUp = PowerUp.mock
        powerUp.collectedBy = "user123"
        #expect(powerUp.isCollected == true)
    }

    @Test func isCollectedReturnsFalseWhenNil() {
        let powerUp = PowerUp.mock
        #expect(powerUp.isCollected == false)
    }

    @Test func isActivatedReturnsTrueWhenSet() {
        var powerUp = PowerUp.mock
        powerUp.activatedAt = Timestamp(date: .now)
        #expect(powerUp.isActivated == true)
    }

    @Test func isActivatedReturnsFalseWhenNil() {
        let powerUp = PowerUp.mock
        #expect(powerUp.isActivated == false)
    }

    @Test func displayNamesExist() {
        for type in PowerUp.PowerUpType.allCases {
            #expect(!type.displayName.isEmpty)
            #expect(!type.iconName.isEmpty)
        }
    }

    @Test func mockIsValid() {
        let mock = PowerUp.mock
        #expect(!mock.id.isEmpty)
        #expect(mock.type == .radarPing)
        #expect(!mock.isCollected)
        #expect(!mock.isActivated)
    }

    @Test func powerUpTypeDecodesUnknownToZonePreview() throws {
        let decoded = try JSONDecoder().decode(PowerUp.PowerUpType.self, from: #""futureType""#.data(using: .utf8)!)
        #expect(decoded == .zonePreview)
    }

    @Test func powerUpTypeDecodesKnownValues() throws {
        let types: [(String, PowerUp.PowerUpType)] = [
            ("zonePreview", .zonePreview),
            ("radarPing", .radarPing),
            ("invisibility", .invisibility),
            ("zoneFreeze", .zoneFreeze),
            ("decoy", .decoy),
            ("jammer", .jammer),
        ]
        for (raw, expected) in types {
            let decoded = try JSONDecoder().decode(PowerUp.PowerUpType.self, from: "\"\(raw)\"".data(using: .utf8)!)
            #expect(decoded == expected, "Expected \(expected) for raw value \(raw)")
        }
    }

    @Test func coordinateIsCorrect() {
        let powerUp = PowerUp(
            id: "test",
            type: .invisibility,
            location: GeoPoint(latitude: 50.8466, longitude: 4.3528),
            spawnedAt: Timestamp(date: .now)
        )
        #expect(abs(powerUp.coordinate.latitude - 50.8466) < 0.0001)
        #expect(abs(powerUp.coordinate.longitude - 4.3528) < 0.0001)
    }

    // MARK: - Firestore decoding (regression for v1.6.2 id injection)

    /// Server-written docs after the 1.6.2 Cloud Function include an explicit `id`
    /// field. Decode must succeed naturally.
    @Test func decodesDocWithExplicitIdField() throws {
        let data: [String: Any] = [
            "id": "pu-0-0-1529788",
            "type": "radarPing",
            "location": GeoPoint(latitude: 50.8466, longitude: 4.3528),
            "spawnedAt": Timestamp(date: Date(timeIntervalSince1970: 1_800_000_000)),
        ]
        let powerUp = try Firestore.Decoder().decode(PowerUp.self, from: data)
        #expect(powerUp.id == "pu-0-0-1529788")
        #expect(powerUp.type == .radarPing)
    }

    /// Legacy / pre-1.6.2 docs don't have an `id` field — the ApiClient
    /// injects `doc.documentID` before decoding. This test simulates that path.
    @Test func decodesDocWithoutIdAfterInjection() throws {
        var data: [String: Any] = [
            "type": "zoneFreeze",
            "location": GeoPoint(latitude: 50.8466, longitude: 4.3528),
            "spawnedAt": Timestamp(date: Date(timeIntervalSince1970: 1_800_000_000)),
        ]
        // Simulate ApiClient injecting the document ID before decode.
        data["id"] = "legacy-doc-id"
        let powerUp = try Firestore.Decoder().decode(PowerUp.self, from: data)
        #expect(powerUp.id == "legacy-doc-id")
        #expect(powerUp.type == .zoneFreeze)
    }

    /// A doc missing the `id` field AND no injection must fail loudly.
    /// Protects against silent loss of the id injection in ApiClient.
    @Test func decodeFailsWithoutIdWhenNotInjected() {
        let data: [String: Any] = [
            "type": "radarPing",
            "location": GeoPoint(latitude: 50.8466, longitude: 4.3528),
            "spawnedAt": Timestamp(date: Date(timeIntervalSince1970: 1_800_000_000)),
        ]
        #expect(throws: (any Error).self) {
            _ = try Firestore.Decoder().decode(PowerUp.self, from: data)
        }
    }
}
