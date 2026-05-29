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
        #expect(PowerUp.PowerUpType.radarPing.durationSeconds == 3)
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

    // MARK: PP-35 availablePowerUpTypes helper

    /// `followTheChicken` keeps every power-up type. Ordering must match the
    /// enum declaration so the wizard and the Android sibling render the
    /// cards in the same order.
    @Test func availablePowerUpTypesForFollowTheChicken() {
        let expected: [PowerUp.PowerUpType] = [
            .zonePreview,
            .radarPing,
            .invisibility,
            .zoneFreeze,
            .decoy,
            .jammer,
        ]
        #expect(availablePowerUpTypes(for: .followTheChicken) == expected)
    }

    /// `stayInTheZone` strips the positional power-ups (invisibility / decoy
    /// / jammer) because the chicken does not broadcast its position. Order
    /// must mirror the iOS enum declaration AND the Android sibling.
    @Test func availablePowerUpTypesForStayInTheZone() {
        let expected: [PowerUp.PowerUpType] = [
            .zonePreview,
            .radarPing,
            .zoneFreeze,
        ]
        #expect(availablePowerUpTypes(for: .stayInTheZone) == expected)
    }

    /// Defaults shipped to players: every power-up type is enabled. The
    /// server-side `stayInTheZone` filter still strips the position-only
    /// ones at spawn time. Keep this golden in lockstep with the
    /// Android sibling so a one-platform regression never sneaks in.
    @Test func defaultEnabledTypesCoverEveryType() {
        let defaults = Game.GamePowerUps().enabledTypes
        #expect(defaults == PowerUp.PowerUpType.allCases.map(\.rawValue))
    }

    // MARK: PP-37 parity goldens (PP-35 follow-up)

    /// Strict count parity: 6 types in followTheChicken vs 3 in stayInTheZone.
    /// Mirrors the Android `availablePowerUpTypes counts match parity matrix`
    /// test — a silent enum addition that bypassed the positional filter
    /// would fail this on one platform without the other and surface the
    /// divergence loudly.
    @Test func availablePowerUpTypesCountsMatchParityMatrix() {
        #expect(availablePowerUpTypes(for: .followTheChicken).count == 6)
        #expect(availablePowerUpTypes(for: .stayInTheZone).count == 3)
    }

    /// `stayInTheZone` MUST NOT contain any positional power-up — the
    /// chicken does not broadcast its position in that mode so spawning
    /// invisibility / decoy / jammer is wasted. Mirrors the Android
    /// `STAY_IN_THE_ZONE excludes every positional power-up` test.
    @Test func availablePowerUpTypesStayInTheZoneExcludesEveryPositionalType() {
        let stay = availablePowerUpTypes(for: .stayInTheZone)
        #expect(!stay.contains(.invisibility))
        #expect(!stay.contains(.decoy))
        #expect(!stay.contains(.jammer))
    }

    /// `stayInTheZone` MUST contain every non-positional power-up. Mirrors
    /// the Android `STAY_IN_THE_ZONE keeps every non-positional power-up`
    /// test.
    @Test func availablePowerUpTypesStayInTheZoneIncludesEveryNonPositionalType() {
        let stay = availablePowerUpTypes(for: .stayInTheZone)
        #expect(stay.contains(.zonePreview))
        #expect(stay.contains(.radarPing))
        #expect(stay.contains(.zoneFreeze))
    }

    /// `followTheChicken` is a passthrough — every enum case lands in the
    /// returned list. Mirrors the Android
    /// `FOLLOW_THE_CHICKEN is a passthrough of every enum case` test.
    @Test func availablePowerUpTypesFollowTheChickenIsPassthrough() {
        let follow = availablePowerUpTypes(for: .followTheChicken)
        for type in PowerUp.PowerUpType.allCases {
            #expect(follow.contains(type), "Expected \(type) in followTheChicken")
        }
    }

    /// The Firestore wire format for `enabledTypes` is a `[String]` of raw
    /// values. The strings used by iOS, Android and the TS server MUST
    /// match exactly — a typo on one platform silently breaks the
    /// `stayInTheZone` filter on the server. Locks the wire contract.
    @Test func powerUpTypeRawValuesMatchFirestoreWireContract() {
        let expected: [(PowerUp.PowerUpType, String)] = [
            (.zonePreview, "zonePreview"),
            (.radarPing, "radarPing"),
            (.invisibility, "invisibility"),
            (.zoneFreeze, "zoneFreeze"),
            (.decoy, "decoy"),
            (.jammer, "jammer"),
        ]
        for (type, raw) in expected {
            #expect(type.rawValue == raw, "Wire raw value drift on \(type)")
        }
    }

    /// The defaults shipped to players (`zoneFreeze` + `zonePreview`)
    /// must be available in BOTH modes — a player who keeps the defaults
    /// in `stayInTheZone` should still see both power-up types spawn.
    /// Guards against a future filter change that would accidentally
    /// strip a default type.
    @Test func defaultEnabledTypesAreAvailableInBothModes() {
        let follow = availablePowerUpTypes(for: .followTheChicken)
        let stay = availablePowerUpTypes(for: .stayInTheZone)
        #expect(follow.contains(.zoneFreeze))
        #expect(follow.contains(.zonePreview))
        #expect(stay.contains(.zoneFreeze))
        #expect(stay.contains(.zonePreview))
    }
}
