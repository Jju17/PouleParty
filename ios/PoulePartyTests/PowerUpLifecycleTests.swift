//
//  PowerUpLifecycleTests.swift
//  PoulePartyTests
//
//  Tests for the complete power-up lifecycle:
//  spawn -> collect -> activate -> expire
//

import CoreLocation
import FirebaseFirestore
import Testing
@testable import PouleParty

struct PowerUpLifecycleTests {

    // MARK: - Spawn Phase

    @Test func generatePowerUpsProducesDeterministicResults() {
        let center = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let batch1 = generatePowerUps(center: center, radius: 1000, count: 5, driftSeed: 42, batchIndex: 0)
        let batch2 = generatePowerUps(center: center, radius: 1000, count: 5, driftSeed: 42, batchIndex: 0)

        #expect(batch1.count == batch2.count)
        for (a, b) in zip(batch1, batch2) {
            #expect(a.id == b.id)
            #expect(a.type == b.type)
            #expect(abs(a.coordinate.latitude - b.coordinate.latitude) < 1e-10)
            #expect(abs(a.coordinate.longitude - b.coordinate.longitude) < 1e-10)
        }
    }

    @Test func generatePowerUpsRespectsCount() {
        let center = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let batch = generatePowerUps(center: center, radius: 1000, count: 3, driftSeed: 99, batchIndex: 0)
        #expect(batch.count == 3)
    }

    @Test func generatePowerUpsWithZeroCountReturnsEmpty() {
        let center = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let batch = generatePowerUps(center: center, radius: 1000, count: 0, driftSeed: 42, batchIndex: 0)
        #expect(batch.isEmpty)
    }

    @Test func generatePowerUpsWithEmptyTypesReturnsEmpty() {
        let center = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let batch = generatePowerUps(center: center, radius: 1000, count: 5, driftSeed: 42, batchIndex: 0, enabledTypes: [])
        #expect(batch.isEmpty)
    }

    @Test func generatePowerUpsPositionsAreWithinZone() {
        let center = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let radius: Double = 1000
        let batch = generatePowerUps(center: center, radius: radius, count: 20, driftSeed: 42, batchIndex: 0)

        let centerLoc = CLLocation(latitude: center.latitude, longitude: center.longitude)
        for powerUp in batch {
            let puLoc = CLLocation(latitude: powerUp.coordinate.latitude, longitude: powerUp.coordinate.longitude)
            let distance = centerLoc.distance(from: puLoc)
            // 0.85 factor means max distance is 85% of radius
            #expect(distance <= radius * 0.85 + 1, "Power-up at distance \(distance) exceeds zone")
        }
    }

    @Test func generatePowerUpsDifferentSeedsProduceDifferentPositions() {
        let center = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let batch1 = generatePowerUps(center: center, radius: 1000, count: 5, driftSeed: 42, batchIndex: 0)
        let batch2 = generatePowerUps(center: center, radius: 1000, count: 5, driftSeed: 99, batchIndex: 0)

        // At least one position should differ
        let anyDifferent = zip(batch1, batch2).contains { a, b in
            abs(a.coordinate.latitude - b.coordinate.latitude) > 1e-10
        }
        #expect(anyDifferent)
    }

    @Test func generatePowerUpsDifferentBatchIndexProducesDifferentResults() {
        let center = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let batch0 = generatePowerUps(center: center, radius: 1000, count: 5, driftSeed: 42, batchIndex: 0)
        let batch1 = generatePowerUps(center: center, radius: 1000, count: 5, driftSeed: 42, batchIndex: 1)

        #expect(batch0[0].id != batch1[0].id)
    }

    @Test func generatePowerUpsHasDeterministicIds() {
        let center = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let batch = generatePowerUps(center: center, radius: 1000, count: 3, driftSeed: 42, batchIndex: 2)

        for powerUp in batch {
            #expect(powerUp.id.hasPrefix("pu-2-"))
        }
    }

    @Test func generatePowerUpsFiltersEnabledTypes() {
        let center = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let onlyHunterTypes = ["zonePreview", "radarPing"]
        let batch = generatePowerUps(center: center, radius: 1000, count: 10, driftSeed: 42, batchIndex: 0, enabledTypes: onlyHunterTypes)

        for powerUp in batch {
            #expect(powerUp.type.isHunterPowerUp, "Expected hunter power-up but got \(powerUp.type)")
        }
    }

    @Test func initialBatchSizeIsCorrect() {
        #expect(AppConstants.powerUpInitialBatchSize == 5)
    }

    @Test func periodicBatchSizeIsCorrect() {
        #expect(AppConstants.powerUpPeriodicBatchSize == 2)
    }

    // MARK: - Collection Phase

    @Test func findNearbyPowerUpsDetectsWithinRadius() {
        let userLoc = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let nearbyPU = PowerUp(
            id: "nearby",
            type: .radarPing,
            location: GeoPoint(latitude: 50.8466, longitude: 4.3528),
            spawnedAt: Timestamp(date: .now)
        )

        let result = findNearbyPowerUps(userLocation: userLoc, availablePowerUps: [nearbyPU])
        #expect(result.count == 1)
    }

    @Test func findNearbyPowerUpsIgnoresDistantPowerUps() {
        let userLoc = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let farPU = PowerUp(
            id: "far",
            type: .radarPing,
            location: GeoPoint(latitude: 50.85, longitude: 4.36), // ~500m away
            spawnedAt: Timestamp(date: .now)
        )

        let result = findNearbyPowerUps(userLocation: userLoc, availablePowerUps: [farPU])
        #expect(result.isEmpty)
    }

    @Test func findNearbyPowerUpsReturnsEmptyWithNilLocation() {
        let powerUps = [PowerUp.mock]
        let result = findNearbyPowerUps(userLocation: nil, availablePowerUps: powerUps)
        #expect(result.isEmpty)
    }

    @Test func findNearbyPowerUpsRespectsCustomRadius() {
        let userLoc = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        // ~100m away
        let mediumPU = PowerUp(
            id: "medium",
            type: .radarPing,
            location: GeoPoint(latitude: 50.8475, longitude: 4.3528),
            spawnedAt: Timestamp(date: .now)
        )

        let resultSmall = findNearbyPowerUps(userLocation: userLoc, availablePowerUps: [mediumPU], collectionRadius: 30)
        let resultLarge = findNearbyPowerUps(userLocation: userLoc, availablePowerUps: [mediumPU], collectionRadius: 200)
        #expect(resultSmall.isEmpty)
        #expect(resultLarge.count == 1)
    }

    @Test func collectionRadiusIs30Meters() {
        #expect(AppConstants.powerUpCollectionRadiusMeters == 30)
    }

    // MARK: - Activation & Expiration Detection

    @Test func detectActivatedPowerUpDetectsNewInvisibility() {
        var oldGame = Game.mock
        var newGame = Game.mock
        let future = Date.now.addingTimeInterval(30)
        newGame.powerUps.activeEffects.invisibility = Timestamp(date: future)

        let result = detectActivatedPowerUp(oldGame: oldGame, newGame: newGame)
        #expect(result != nil)
        #expect(result?.type == .invisibility)
    }

    @Test func detectActivatedPowerUpDetectsNewZoneFreeze() {
        var newGame = Game.mock
        let future = Date.now.addingTimeInterval(120)
        newGame.powerUps.activeEffects.zoneFreeze = Timestamp(date: future)

        let result = detectActivatedPowerUp(oldGame: Game.mock, newGame: newGame)
        #expect(result != nil)
        #expect(result?.type == .zoneFreeze)
    }

    @Test func detectActivatedPowerUpDetectsNewRadarPing() {
        var newGame = Game.mock
        let future = Date.now.addingTimeInterval(30)
        newGame.powerUps.activeEffects.radarPing = Timestamp(date: future)

        let result = detectActivatedPowerUp(oldGame: Game.mock, newGame: newGame)
        #expect(result != nil)
        #expect(result?.type == .radarPing)
    }

    @Test func detectActivatedPowerUpDetectsNewDecoy() {
        var newGame = Game.mock
        let future = Date.now.addingTimeInterval(20)
        newGame.powerUps.activeEffects.decoy = Timestamp(date: future)

        let result = detectActivatedPowerUp(oldGame: Game.mock, newGame: newGame)
        #expect(result != nil)
        #expect(result?.type == .decoy)
    }

    @Test func detectActivatedPowerUpDetectsNewJammer() {
        var newGame = Game.mock
        let future = Date.now.addingTimeInterval(30)
        newGame.powerUps.activeEffects.jammer = Timestamp(date: future)

        let result = detectActivatedPowerUp(oldGame: Game.mock, newGame: newGame)
        #expect(result != nil)
        #expect(result?.type == .jammer)
    }

    @Test func detectActivatedPowerUpReturnsNilWhenNoChange() {
        let result = detectActivatedPowerUp(oldGame: Game.mock, newGame: Game.mock)
        #expect(result == nil)
    }

    @Test func detectActivatedPowerUpIgnoresExpiredTimestamp() {
        var newGame = Game.mock
        let past = Date.now.addingTimeInterval(-10)
        newGame.powerUps.activeEffects.invisibility = Timestamp(date: past)

        let result = detectActivatedPowerUp(oldGame: Game.mock, newGame: newGame)
        #expect(result == nil)
    }

    @Test func detectActivatedPowerUpIgnoresSameTimestamp() {
        var oldGame = Game.mock
        var newGame = Game.mock
        let future = Date.now.addingTimeInterval(30)
        oldGame.powerUps.activeEffects.invisibility = Timestamp(date: future)
        newGame.powerUps.activeEffects.invisibility = Timestamp(date: future)

        let result = detectActivatedPowerUp(oldGame: oldGame, newGame: newGame)
        #expect(result == nil)
    }

    // MARK: - Active Effect Properties

    @Test func isChickenInvisibleTrueWhenFutureTimestamp() {
        var game = Game.mock
        game.powerUps.activeEffects.invisibility = Timestamp(date: Date.now.addingTimeInterval(30))
        #expect(game.isChickenInvisible == true)
    }

    @Test func isChickenInvisibleFalseWhenPastTimestamp() {
        var game = Game.mock
        game.powerUps.activeEffects.invisibility = Timestamp(date: Date.now.addingTimeInterval(-10))
        #expect(game.isChickenInvisible == false)
    }

    @Test func isChickenInvisibleFalseWhenNil() {
        let game = Game.mock
        #expect(game.isChickenInvisible == false)
    }

    @Test func isZoneFrozenTrueWhenFutureTimestamp() {
        var game = Game.mock
        game.powerUps.activeEffects.zoneFreeze = Timestamp(date: Date.now.addingTimeInterval(120))
        #expect(game.isZoneFrozen == true)
    }

    @Test func isZoneFrozenFalseWhenPastTimestamp() {
        var game = Game.mock
        game.powerUps.activeEffects.zoneFreeze = Timestamp(date: Date.now.addingTimeInterval(-10))
        #expect(game.isZoneFrozen == false)
    }

    @Test func isRadarPingActiveTrueWhenFutureTimestamp() {
        var game = Game.mock
        game.powerUps.activeEffects.radarPing = Timestamp(date: Date.now.addingTimeInterval(30))
        #expect(game.isRadarPingActive == true)
    }

    @Test func isDecoyActiveTrueWhenFutureTimestamp() {
        var game = Game.mock
        game.powerUps.activeEffects.decoy = Timestamp(date: Date.now.addingTimeInterval(20))
        #expect(game.isDecoyActive == true)
    }

    @Test func isJammerActiveTrueWhenFutureTimestamp() {
        var game = Game.mock
        game.powerUps.activeEffects.jammer = Timestamp(date: Date.now.addingTimeInterval(30))
        #expect(game.isJammerActive == true)
    }

    // MARK: - Zone Freeze Effect on Radius

    @Test func zoneFreezeSkipsRadiusShrink() {
        let result = processRadiusUpdate(
            nextRadiusUpdate: Date.now.addingTimeInterval(-1),
            currentRadius: 1000,
            radiusDeclinePerUpdate: 100,
            radiusIntervalUpdate: 5,
            gameMod: .followTheChicken,
            initialCoordinates: CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528),
            currentCircle: CircleOverlay(
                center: CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528),
                radius: 1000
            ),
            isZoneFrozen: true
        )
        #expect(result == nil)
    }

    @Test func zoneFreezeDoesNotPreventUpdateWhenExpired() {
        let result = processRadiusUpdate(
            nextRadiusUpdate: Date.now.addingTimeInterval(-1),
            currentRadius: 1000,
            radiusDeclinePerUpdate: 100,
            radiusIntervalUpdate: 5,
            gameMod: .followTheChicken,
            initialCoordinates: CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528),
            currentCircle: CircleOverlay(
                center: CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528),
                radius: 1000
            ),
            isZoneFrozen: false
        )
        #expect(result != nil)
        #expect(result?.newRadius == 900)
    }

    // MARK: - Jammer Noise

    @Test func applyJammerNoiseChangesCoordinate() {
        let original = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        // Step the clock by 1s between calls so the time-bucketed seed
        // produces a different offset each iteration.
        var anyDifferent = false
        let base = Date(timeIntervalSince1970: 1_700_000_000)
        for i in 0..<10 {
            let jammed = applyJammerNoise(
                to: original,
                driftSeed: 12345,
                now: base.addingTimeInterval(TimeInterval(i))
            )
            if jammed.latitude != original.latitude || jammed.longitude != original.longitude {
                anyDifferent = true
                break
            }
        }
        #expect(anyDifferent)
    }

    @Test func applyJammerNoiseStaysWithinBounds() {
        let original = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let halfNoise = AppConstants.jammerNoiseDegrees / 2.0
        let base = Date(timeIntervalSince1970: 1_700_000_000)
        for i in 0..<100 {
            let jammed = applyJammerNoise(
                to: original,
                driftSeed: 12345,
                now: base.addingTimeInterval(TimeInterval(i))
            )
            let latDiff = abs(jammed.latitude - original.latitude)
            let lonDiff = abs(jammed.longitude - original.longitude)
            #expect(latDiff <= halfNoise + 1e-10)
            #expect(lonDiff <= halfNoise + 1e-10)
        }
    }

    @Test func applyJammerNoiseIsDeterministic() {
        // Same (driftSeed, now) must produce the exact same offset on every call.
        // This is what allows parity tests to pin the output across iOS/Android.
        let original = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let now = Date(timeIntervalSince1970: 1_700_000_000)
        let a = applyJammerNoise(to: original, driftSeed: 777, now: now)
        let b = applyJammerNoise(to: original, driftSeed: 777, now: now)
        #expect(a.latitude == b.latitude)
        #expect(a.longitude == b.longitude)
    }
}
