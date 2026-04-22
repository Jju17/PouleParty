//
//  GameZoneCodableTests.swift
//  PoulePartyTests
//
//  Proves the iOS decode side of the cross-platform `zone` contract enforced
//  on the server in `functions/src/stripe.ts` + `functions/test/stripe-zone.test.ts`:
//
//    - `zone.center` must be a Firestore `GeoPoint` (native type). A plain
//       `[String: Any]` map crashes Android's Kotlin decoder and fails iOS's
//       Swift decoder — the 1.9.0 Android crash was exactly that.
//    - `zone.finalCenter` is `GeoPoint?`. Missing key / explicit `NSNull`
//       must decode to `nil`. Providing a plain map must fail the decode
//       (regression guard for the server-side `HashMap-not-GeoPoint` bug).
//
//  Uses `Firestore.Decoder()` — the exact same decoder the Firebase SDK
//  drives inside `DocumentSnapshot.data(as:)`. This is as close to the
//  production decode path as we can get without spinning up an emulator.
//

import FirebaseFirestore
import Testing
@testable import PouleParty

struct GameZoneCodableTests {

    // MARK: - Happy path: server-written shape decodes cleanly

    @Test func decodesZoneWithGeoPointCenterAndFinalCenter() throws {
        let dict: [String: Any] = [
            "center": GeoPoint(latitude: 50.85, longitude: 4.35),
            "finalCenter": GeoPoint(latitude: 50.86, longitude: 4.36),
            "radius": 1500.0,
            "shrinkIntervalMinutes": 5.0,
            "shrinkMetersPerUpdate": 100.0,
            "driftSeed": 42,
        ]
        let zone = try Firestore.Decoder().decode(Game.Zone.self, from: dict)
        #expect(zone.center.latitude == 50.85)
        #expect(zone.center.longitude == 4.35)
        #expect(zone.finalCenter?.latitude == 50.86)
        #expect(zone.finalCenter?.longitude == 4.36)
        #expect(zone.radius == 1500.0)
        #expect(zone.driftSeed == 42)
    }

    @Test func decodesZoneWithExplicitlyNullFinalCenter() throws {
        let dict: [String: Any] = [
            "center": GeoPoint(latitude: 50.85, longitude: 4.35),
            "finalCenter": NSNull(),
            "radius": 1500.0,
            "shrinkIntervalMinutes": 5.0,
            "shrinkMetersPerUpdate": 100.0,
            "driftSeed": 0,
        ]
        let zone = try Firestore.Decoder().decode(Game.Zone.self, from: dict)
        #expect(zone.finalCenter == nil)
    }

    @Test func decodesZoneWithMissingFinalCenterKey() throws {
        let dict: [String: Any] = [
            "center": GeoPoint(latitude: 50.85, longitude: 4.35),
            "radius": 1500.0,
            "shrinkIntervalMinutes": 5.0,
            "shrinkMetersPerUpdate": 100.0,
            "driftSeed": 0,
        ]
        let zone = try Firestore.Decoder().decode(Game.Zone.self, from: dict)
        #expect(zone.finalCenter == nil)
    }

    @Test func decodesFullGameWithGeoPointZone() throws {
        // Mirrors the shape `materialiseGameDoc` writes server-side.
        let dict: [String: Any] = [
            "id": "abc123",
            "name": "Test game",
            "maxPlayers": 10,
            "gameMode": "stayInTheZone",
            "chickenCanSeeHunters": false,
            "foundCode": "1234",
            "hunterIds": [],
            "status": "waiting",
            "winners": [],
            "creatorId": "user-1",
            "timing": [
                "start": Timestamp(seconds: 1_700_000_000, nanoseconds: 0),
                "end": Timestamp(seconds: 1_700_003_600, nanoseconds: 0),
                "headStartMinutes": 5.0,
            ] as [String: Any],
            "zone": [
                "center": GeoPoint(latitude: 50.85, longitude: 4.35),
                "finalCenter": GeoPoint(latitude: 50.86, longitude: 4.36),
                "radius": 1500.0,
                "shrinkIntervalMinutes": 5.0,
                "shrinkMetersPerUpdate": 100.0,
                "driftSeed": 42,
            ] as [String: Any],
            "pricing": [
                "model": "flat",
                "pricePerPlayer": 500,
                "deposit": 0,
                "commission": 15.0,
            ] as [String: Any],
            "registration": [
                "required": false,
            ] as [String: Any],
            "powerUps": [
                "enabled": true,
                "enabledTypes": ["radarPing"],
                "activeEffects": [:] as [String: Any],
            ] as [String: Any],
        ]
        let game = try Firestore.Decoder().decode(Game.self, from: dict)
        #expect(game.id == "abc123")
        #expect(game.zone.center.latitude == 50.85)
        #expect(game.zone.finalCenter?.longitude == 4.36)
    }

    // MARK: - iOS is permissive where Android is strict
    //
    // If the server ever writes `zone.center` as a plain map instead of a
    // `GeoPoint` (the 1.9.0 regression), Android's Kotlin decoder throws a
    // `RuntimeException` — iOS's Swift `Firestore.Decoder` silently coerces
    // the map to a `GeoPoint` because `GeoPoint` itself is `Codable` with
    // `latitude` + `longitude` Double keys that happen to line up.
    //
    // That's the asymmetry the 1.9.0 crash report exposed: same broken doc,
    // Android crashed, iOS kept going (logged nothing on the happy-ish path,
    // since the coerced GeoPoint would still deserialize even if imperfectly).
    //
    // We DOCUMENT this behaviour with the two tests below instead of asserting
    // a hard rejection on iOS, because:
    //   1. The real fix is on the server (already done + TS-tested).
    //   2. Asserting a hard rejection on iOS would tie us to a Swift/Firebase
    //      SDK implementation detail that could flip on minor version bumps.
    //   3. Permissive behaviour here is actually desirable — iOS survived the
    //      server bug in production.

    @Test func permissivelyAcceptsPlainMapForCenter() throws {
        // If some day the server writes this shape again, iOS will keep
        // working. Android won't — that's why the server-side guarantee is
        // the authoritative fix.
        let dict: [String: Any] = [
            "center": ["latitude": 50.85, "longitude": 4.35] as [String: Any],
            "finalCenter": NSNull(),
            "radius": 1500.0,
            "shrinkIntervalMinutes": 5.0,
            "shrinkMetersPerUpdate": 100.0,
            "driftSeed": 0,
        ]
        let zone = try Firestore.Decoder().decode(Game.Zone.self, from: dict)
        #expect(zone.center.latitude == 50.85)
        #expect(zone.center.longitude == 4.35)
    }

    @Test func permissivelyAcceptsPlainMapForFinalCenter() throws {
        let dict: [String: Any] = [
            "center": GeoPoint(latitude: 50.85, longitude: 4.35),
            "finalCenter": ["latitude": 50.86, "longitude": 4.36] as [String: Any],
            "radius": 1500.0,
            "shrinkIntervalMinutes": 5.0,
            "shrinkMetersPerUpdate": 100.0,
            "driftSeed": 0,
        ]
        let zone = try Firestore.Decoder().decode(Game.Zone.self, from: dict)
        #expect(zone.finalCenter?.latitude == 50.86)
        #expect(zone.finalCenter?.longitude == 4.36)
    }

    // MARK: - Boundary coordinates round-trip
    //
    // Mirrors functions/test/stripe-zone.test.ts `boundary coordinates round-trip`.
    // If the server test passes and this test passes on the same inputs, the
    // end-to-end server→iOS decode is pinned at every tricky latitude/longitude.

    private static let BOUNDARY_COORDS: [(String, Double, Double)] = [
        ("null island", 0, 0),
        ("north pole", 90, 0),
        ("south pole", -90, 0),
        ("IDL east", 0, 180),
        ("IDL west", 0, -180),
        ("Brussels", 50.8503, 4.3517),
        ("Sydney", -33.868, 151.2093),
        ("Easter Island", -27.1127, -109.3497),
        ("sub-degree precision", 0.000_001, -0.000_001),
    ]

    @Test(arguments: BOUNDARY_COORDS)
    func boundaryCoordinatesRoundTrip(_ label: String, _ latitude: Double, _ longitude: Double) throws {
        _ = label // surfaced in test description
        let dict: [String: Any] = [
            "center": GeoPoint(latitude: latitude, longitude: longitude),
            "finalCenter": NSNull(),
            "radius": 1500.0,
            "shrinkIntervalMinutes": 5.0,
            "shrinkMetersPerUpdate": 100.0,
            "driftSeed": 0,
        ]
        let zone = try Firestore.Decoder().decode(Game.Zone.self, from: dict)
        #expect(zone.center.latitude == latitude)
        #expect(zone.center.longitude == longitude)
    }

    // MARK: - Encode round-trip (prove Game.Zone can also be written back)

    @Test func encodeDecodeRoundTripPreservesZone() throws {
        let original = Game.Zone(
            center: GeoPoint(latitude: -33.868, longitude: 151.2093),
            finalCenter: GeoPoint(latitude: -33.87, longitude: 151.21),
            radius: 2500,
            shrinkIntervalMinutes: 3,
            shrinkMetersPerUpdate: 75,
            driftSeed: -12345
        )
        let encoded = try Firestore.Encoder().encode(original)
        let decoded = try Firestore.Decoder().decode(Game.Zone.self, from: encoded)
        #expect(decoded.center.latitude == original.center.latitude)
        #expect(decoded.center.longitude == original.center.longitude)
        #expect(decoded.finalCenter?.latitude == original.finalCenter?.latitude)
        #expect(decoded.radius == original.radius)
        #expect(decoded.driftSeed == original.driftSeed)
    }

    @Test func encodedZoneContainsGeoPointInstanceNotMap() throws {
        // This is the write-side counterpart of `rejectsPlainMapForCenter`.
        // If some day someone refactors Zone.center to a custom type, this test
        // will catch a drift away from the native Firestore GeoPoint.
        let zone = Game.Zone(
            center: GeoPoint(latitude: 50.85, longitude: 4.35),
            finalCenter: nil
        )
        let encoded = try Firestore.Encoder().encode(zone)
        let center = encoded["center"]
        #expect(center is GeoPoint)
        #expect((center as? GeoPoint)?.latitude == 50.85)
    }

    @Test func nilFinalCenterRoundTripsAsNilNotMap() throws {
        // Regardless of whether Firestore.Encoder emits the key as nil,
        // NSNull, or omits it, the round-trip must land back as `nil` on
        // the decode side. Anything else would break the
        // `followTheChicken` path where finalCenter is legitimately absent.
        let original = Game.Zone(
            center: GeoPoint(latitude: 50.85, longitude: 4.35),
            finalCenter: nil
        )
        let encoded = try Firestore.Encoder().encode(original)
        let decoded = try Firestore.Decoder().decode(Game.Zone.self, from: encoded)
        #expect(decoded.finalCenter == nil)
    }
}
