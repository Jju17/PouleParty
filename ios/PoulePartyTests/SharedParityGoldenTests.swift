import CoreLocation
import FirebaseFirestore
import Foundation
import Testing
@testable import PouleParty

/// Replays `parity/golden.json`, generated from the server implementation,
/// so a drift on any platform fails a test instead of relying on copied tables.
struct SharedParityGoldenTests {
    private let tolerance = 1e-9

    private struct LatLng: Decodable {
        let latitude: Double
        let longitude: Double
        var coordinate: CLLocationCoordinate2D { .init(latitude: latitude, longitude: longitude) }
    }

    private struct Golden: Decodable {
        struct Distance: Decodable { let from: LatLng; let to: LatLng; let meters: Double }
        struct NormalMode: Decodable {
            let initialRadius: Double; let gameDurationMinutes: Double; let interval: Double; let decline: Double
        }
        struct Interpolate: Decodable {
            let initialCenter: LatLng; let finalCenter: LatLng?; let initialRadius: Double; let currentRadius: Double; let result: LatLng
        }
        struct Drift: Decodable {
            let basePoint: LatLng; let oldRadius: Double; let newRadius: Double; let driftSeed: Int; let finalCenter: LatLng?; let result: LatLng
        }
        struct Spawn: Decodable {
            struct Item: Decodable { let id: String; let type: String; let latitude: Double; let longitude: Double }
            let center: LatLng; let radius: Double; let count: Int; let driftSeed: Int; let batchIndex: Int
            let enabledTypes: [String]; let result: [Item]
        }
        struct Pick: Decodable {
            let startPin: LatLng; let finalCenter: LatLng; let radius: Double; let seed: Int; let result: LatLng
        }
        let distance: [Distance]
        let normalModeSettings: [NormalMode]
        let interpolateZoneCenter: [Interpolate]
        let deterministicDriftCenter: [Drift]
        let generatePowerUps: [Spawn]
        let pickInitialZoneCenter: [Pick]
    }

    private let golden: Golden = {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../../parity/golden.json")
            .standardized
        let data = try! Data(contentsOf: url)
        return try! JSONDecoder().decode(Golden.self, from: data)
    }()

    private func expectClose(_ expected: LatLng, _ actual: CLLocationCoordinate2D) {
        #expect(abs(expected.latitude - actual.latitude) < tolerance)
        #expect(abs(expected.longitude - actual.longitude) < tolerance)
    }

    @Test func distanceMatchesTheServerHaversine() {
        for vector in golden.distance {
            #expect(abs(distanceMeters(vector.from.coordinate, vector.to.coordinate) - vector.meters) < 1e-6)
        }
    }

    @Test func normalModeSettingsMatch() {
        for vector in golden.normalModeSettings {
            let result = calculateNormalModeSettings(initialRadius: vector.initialRadius, gameDurationMinutes: vector.gameDurationMinutes)
            #expect(abs(result.interval - vector.interval) < tolerance)
            #expect(abs(result.decline - vector.decline) < tolerance)
        }
    }

    @Test func interpolatedCentersMatch() {
        for vector in golden.interpolateZoneCenter {
            let actual = interpolateZoneCenter(
                initialCenter: vector.initialCenter.coordinate,
                finalCenter: vector.finalCenter?.coordinate,
                initialRadius: vector.initialRadius,
                currentRadius: vector.currentRadius
            )
            expectClose(vector.result, actual)
        }
    }

    @Test func driftCentersMatch() {
        for vector in golden.deterministicDriftCenter {
            let actual = deterministicDriftCenter(
                basePoint: vector.basePoint.coordinate,
                oldRadius: vector.oldRadius,
                newRadius: vector.newRadius,
                driftSeed: vector.driftSeed,
                finalCenter: vector.finalCenter?.coordinate
            )
            expectClose(vector.result, actual)
        }
    }

    @Test func spawnedPowerUpsMatch() {
        for vector in golden.generatePowerUps {
            let actual = generatePowerUps(
                center: vector.center.coordinate,
                radius: vector.radius,
                count: vector.count,
                driftSeed: vector.driftSeed,
                batchIndex: vector.batchIndex,
                enabledTypes: vector.enabledTypes
            )
            #expect(actual.count == vector.result.count)
            for (expected, powerUp) in zip(vector.result, actual) {
                #expect(powerUp.id == expected.id)
                #expect(powerUp.type.rawValue == expected.type)
                #expect(abs(powerUp.location.latitude - expected.latitude) < tolerance)
                #expect(abs(powerUp.location.longitude - expected.longitude) < tolerance)
            }
        }
    }

    @Test func initialZoneCentersMatch() {
        for vector in golden.pickInitialZoneCenter {
            let actual = pickInitialZoneCenter(
                startPin: vector.startPin.coordinate,
                finalCenter: vector.finalCenter.coordinate,
                radius: vector.radius,
                seed: vector.seed
            )
            expectClose(vector.result, actual)
        }
    }
}
