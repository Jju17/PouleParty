//
//  GameTimerLogicTests.swift
//  PoulePartyTests
//

import CoreLocation
import FirebaseFirestore
import Testing
@testable import PouleParty

struct GameTimerLogicTests {

    // MARK: - evaluateCountdown

    @Test func countdownNoChangeWhenFarFromTarget() {
        let phase = CountdownPhase(
            targetDate: Date.now.addingTimeInterval(60),
            completionText: "GO!",
            showNumericCountdown: true,
            isEnabled: true
        )
        let result = evaluateCountdown(
            phases: [phase],
            currentCountdownNumber: nil,
            currentCountdownText: nil
        )
        #expect(result == .noChange)
    }

    @Test func countdownShowsNumberWhenWithinThreshold() {
        let phase = CountdownPhase(
            targetDate: Date.now.addingTimeInterval(2.5),
            completionText: "GO!",
            showNumericCountdown: true,
            isEnabled: true
        )
        let result = evaluateCountdown(
            phases: [phase],
            currentCountdownNumber: nil,
            currentCountdownText: nil
        )
        #expect(result == .updateNumber(3))
    }

    @Test func countdownNoChangeWhenNumberAlreadyShown() {
        let phase = CountdownPhase(
            targetDate: Date.now.addingTimeInterval(2.5),
            completionText: "GO!",
            showNumericCountdown: true,
            isEnabled: true
        )
        let result = evaluateCountdown(
            phases: [phase],
            currentCountdownNumber: 3,
            currentCountdownText: nil
        )
        #expect(result == .noChange)
    }

    @Test func countdownShowsTextOnCompletion() {
        let phase = CountdownPhase(
            targetDate: Date.now.addingTimeInterval(-0.5),
            completionText: "RUN!",
            showNumericCountdown: true,
            isEnabled: true
        )
        let result = evaluateCountdown(
            phases: [phase],
            currentCountdownNumber: nil,
            currentCountdownText: nil
        )
        #expect(result == .showText("RUN!"))
    }

    @Test func countdownSkipsDisabledPhases() {
        let disabledPhase = CountdownPhase(
            targetDate: Date.now.addingTimeInterval(2),
            completionText: "SKIP",
            showNumericCountdown: true,
            isEnabled: false
        )
        let enabledPhase = CountdownPhase(
            targetDate: Date.now.addingTimeInterval(-0.5),
            completionText: "GO!",
            showNumericCountdown: true,
            isEnabled: true
        )
        let result = evaluateCountdown(
            phases: [disabledPhase, enabledPhase],
            currentCountdownNumber: nil,
            currentCountdownText: nil
        )
        #expect(result == .showText("GO!"))
    }

    @Test func countdownNoChangeAfterAllPhasesComplete() {
        let phase = CountdownPhase(
            targetDate: Date.now.addingTimeInterval(-5),
            completionText: "GO!",
            showNumericCountdown: true,
            isEnabled: true
        )
        let result = evaluateCountdown(
            phases: [phase],
            currentCountdownNumber: nil,
            currentCountdownText: nil
        )
        #expect(result == .noChange)
    }

    @Test func countdownNoChangeWhenTextAlreadyShowing() {
        let phase = CountdownPhase(
            targetDate: Date.now.addingTimeInterval(-0.5),
            completionText: "RUN!",
            showNumericCountdown: true,
            isEnabled: true
        )
        let result = evaluateCountdown(
            phases: [phase],
            currentCountdownNumber: nil,
            currentCountdownText: "RUN!"
        )
        #expect(result == .noChange)
    }

    // MARK: - checkGameOverByTime

    @Test func gameOverByTimeWhenPastEnd() {
        #expect(checkGameOverByTime(endDate: Date.now.addingTimeInterval(-1)) == true)
    }

    @Test func gameNotOverWhenBeforeEnd() {
        #expect(checkGameOverByTime(endDate: Date.now.addingTimeInterval(60)) == false)
    }

    // MARK: - processRadiusUpdate

    @Test func radiusUpdateReturnsNilWhenNoNextUpdate() {
        let result = processRadiusUpdate(
            nextRadiusUpdate: nil,
            currentRadius: 1500,
            radiusDeclinePerUpdate: 100,
            radiusIntervalUpdate: 5,
            gameMod: .followTheChicken,
            initialCoordinates: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0),
            currentCircle: nil
        )
        #expect(result == nil)
    }

    @Test func radiusUpdateReturnsNilWhenNotDueYet() {
        let result = processRadiusUpdate(
            nextRadiusUpdate: Date.now.addingTimeInterval(60),
            currentRadius: 1500,
            radiusDeclinePerUpdate: 100,
            radiusIntervalUpdate: 5,
            gameMod: .followTheChicken,
            initialCoordinates: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0),
            currentCircle: nil
        )
        #expect(result == nil)
    }

    @Test func radiusUpdateShrinksWhenDue() {
        let result = processRadiusUpdate(
            nextRadiusUpdate: Date.now.addingTimeInterval(-1),
            currentRadius: 1500,
            radiusDeclinePerUpdate: 100,
            radiusIntervalUpdate: 5,
            gameMod: .followTheChicken,
            initialCoordinates: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0),
            currentCircle: CircleOverlay(
                center: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0),
                radius: 1500
            )
        )
        #expect(result != nil)
        #expect(result!.newRadius == 1400)
        #expect(result!.isGameOver == false)
    }

    @Test func radiusUpdateGameOverWhenRadiusReachesZero() {
        let result = processRadiusUpdate(
            nextRadiusUpdate: Date.now.addingTimeInterval(-1),
            currentRadius: 100,
            radiusDeclinePerUpdate: 100,
            radiusIntervalUpdate: 5,
            gameMod: .followTheChicken,
            initialCoordinates: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0),
            currentCircle: nil
        )
        #expect(result != nil)
        #expect(result!.isGameOver == true)
        #expect(result!.gameOverMessage == "The zone has collapsed!")
    }

    @Test func radiusUpdateUsesInitialCoordsForStayInTheZone() {
        let initialCoords = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let currentCenter = CLLocationCoordinate2D(latitude: 51.0, longitude: 5.0)
        let result = processRadiusUpdate(
            nextRadiusUpdate: Date.now.addingTimeInterval(-1),
            currentRadius: 1500,
            radiusDeclinePerUpdate: 100,
            radiusIntervalUpdate: 5,
            gameMod: .stayInTheZone,
            initialCoordinates: initialCoords,
            currentCircle: CircleOverlay(center: currentCenter, radius: 1500),
            driftSeed: 12345
        )
        #expect(result != nil)
        // With drift, center is close to initialCoords but not exactly equal
        let distance = CLLocation(latitude: result!.newCircle!.center.latitude, longitude: result!.newCircle!.center.longitude)
            .distance(from: CLLocation(latitude: initialCoords.latitude, longitude: initialCoords.longitude))
        // Max drift = min(1400*0.5, 100*0.5) = 50m
        #expect(distance < 60, "Drifted center should be within 60m of base point")
    }

    // MARK: - deterministicDriftCenter

    @Test func driftCenterIsDeterministic() {
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let result1 = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: 12345)
        let result2 = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: 12345)
        #expect(result1.latitude == result2.latitude)
        #expect(result1.longitude == result2.longitude)
    }

    @Test func driftCenterDiffersForDifferentRadius() {
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let result1 = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: 12345)
        let result2 = deterministicDriftCenter(basePoint: base, oldRadius: 1400, newRadius: 1300, driftSeed: 12345)
        let areDifferent = result1.latitude != result2.latitude || result1.longitude != result2.longitude
        #expect(areDifferent, "Different radius values should produce different drift centers")
    }

    @Test func driftCenterDiffersForDifferentSeeds() {
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let result1 = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: 11111)
        let result2 = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: 99999)
        let areDifferent = result1.latitude != result2.latitude || result1.longitude != result2.longitude
        #expect(areDifferent, "Different seeds should produce different drift centers")
    }

    @Test func driftCenterStaysCloseToBase() {
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let result = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: 54321)
        let distance = CLLocation(latitude: result.latitude, longitude: result.longitude)
            .distance(from: CLLocation(latitude: base.latitude, longitude: base.longitude))
        // safeDrift = min(1400*0.5, 100*0.5) = 50m
        #expect(distance <= 50, "Drifted center must be within safeDrift of base")
    }

    @Test func driftCenterReturnsBaseWhenNoRoom() {
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        // oldRadius == newRadius → no room to drift
        let result = deterministicDriftCenter(basePoint: base, oldRadius: 1000, newRadius: 1000, driftSeed: 12345)
        #expect(result.latitude == base.latitude)
        #expect(result.longitude == base.longitude)
    }

    // MARK: - detectNewWinners

    @Test func detectNewWinnersReturnsNilWhenNoNewWinners() {
        let winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: Timestamp(date: .now))
        ]
        let result = detectNewWinners(winners: winners, previousCount: 1)
        #expect(result == nil)
    }

    @Test func detectNewWinnersReturnsNotification() {
        let winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: Timestamp(date: .now)),
            Winner(hunterId: "h2", hunterName: "Bob", timestamp: Timestamp(date: .now))
        ]
        let result = detectNewWinners(winners: winners, previousCount: 1)
        #expect(result == "Bob found the chicken! 🐔")
    }

    @Test func detectNewWinnersFiltersOwnHunter() {
        let winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: Timestamp(date: .now)),
            Winner(hunterId: "me", hunterName: "Me", timestamp: Timestamp(date: .now))
        ]
        let result = detectNewWinners(winners: winners, previousCount: 1, ownHunterId: "me")
        #expect(result == nil)
    }

    @Test func detectNewWinnersWithEmptyList() {
        let result = detectNewWinners(winners: [], previousCount: 0)
        #expect(result == nil)
    }

    @Test func detectNewWinnersFromZero() {
        let winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: Timestamp(date: .now))
        ]
        let result = detectNewWinners(winners: winners, previousCount: 0)
        #expect(result == "Alice found the chicken! 🐔")
    }

    // MARK: - Zone freeze

    @Test func processRadiusUpdateReturnsNilWhenZoneFrozen() {
        let result = processRadiusUpdate(
            nextRadiusUpdate: Date.now.addingTimeInterval(-1),
            currentRadius: 1500,
            radiusDeclinePerUpdate: 100,
            radiusIntervalUpdate: 5,
            gameMod: .stayInTheZone,
            initialCoordinates: CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528),
            currentCircle: nil,
            isZoneFrozen: true
        )
        #expect(result == nil)
    }

    @Test func processRadiusUpdateProceedsWhenNotFrozen() {
        let result = processRadiusUpdate(
            nextRadiusUpdate: Date.now.addingTimeInterval(-1),
            currentRadius: 1500,
            radiusDeclinePerUpdate: 100,
            radiusIntervalUpdate: 5,
            gameMod: .stayInTheZone,
            initialCoordinates: CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528),
            currentCircle: nil,
            isZoneFrozen: false
        )
        #expect(result != nil)
        #expect(result!.newRadius == 1400)
    }

    // MARK: - shouldCheckZone

    @Test func shouldCheckZoneChickenStayInTheZone() {
        #expect(shouldCheckZone(role: .chicken, gameMod: .stayInTheZone) == true)
    }

    @Test func shouldCheckZoneHunterStayInTheZone() {
        #expect(shouldCheckZone(role: .hunter, gameMod: .stayInTheZone) == true)
    }

    @Test func shouldCheckZoneChickenFollowTheChicken() {
        #expect(shouldCheckZone(role: .chicken, gameMod: .followTheChicken) == false)
    }

    @Test func shouldCheckZoneHunterFollowTheChicken() {
        #expect(shouldCheckZone(role: .hunter, gameMod: .followTheChicken) == true)
    }

    // MARK: - checkZoneStatus

    @Test func checkZoneStatusInsideZone() {
        // Brussels center, ~100m away, 1500m radius → inside
        let center = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let nearby = CLLocationCoordinate2D(latitude: 50.8470, longitude: 4.3530)
        let result = checkZoneStatus(userLocation: nearby, zoneCenter: center, zoneRadius: 1500)
        #expect(result.isOutsideZone == false)
    }

    @Test func checkZoneStatusOutsideZone() {
        // Brussels center vs Namur (~60km away), 1500m radius → outside
        let center = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let farAway = CLLocationCoordinate2D(latitude: 50.4674, longitude: 4.8720)
        let result = checkZoneStatus(userLocation: farAway, zoneCenter: center, zoneRadius: 1500)
        #expect(result.isOutsideZone == true)
    }

    @Test func checkZoneStatusAtCenter() {
        let center = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let result = checkZoneStatus(userLocation: center, zoneCenter: center, zoneRadius: 1500)
        #expect(result.isOutsideZone == false)
        #expect(result.distanceToCenter < 1)
    }

    // MARK: - seededRandom (splitmix64 cross-platform parity)

    @Test func seededRandomIsDeterministic() {
        let a = seededRandom(seed: 42, index: 0)
        let b = seededRandom(seed: 42, index: 0)
        #expect(a == b)
    }

    @Test func seededRandomDiffersForDifferentIndex() {
        let a = seededRandom(seed: 42, index: 0)
        let b = seededRandom(seed: 42, index: 1)
        #expect(a != b)
    }

    @Test func seededRandomDiffersForDifferentSeed() {
        let a = seededRandom(seed: 100, index: 0)
        let b = seededRandom(seed: 200, index: 0)
        #expect(a != b)
    }

    @Test func seededRandomReturnsValueInZeroOneRange() {
        for seed in [0, 1, 42, 999_999, -1, Int.max, Int.min] {
            for idx in [0, 1, 5, 100] {
                let val = seededRandom(seed: seed, index: idx)
                #expect(val >= 0.0, "seed=\(seed) index=\(idx) produced \(val)")
                #expect(val < 1.0, "seed=\(seed) index=\(idx) produced \(val)")
            }
        }
    }

    /// Hard-coded expected values verified against Android output.
    /// If these break, cross-platform parity is lost — fix both platforms.
    @Test func seededRandomCrossPlatformParity() {
        #expect(abs(seededRandom(seed: 42, index: 0) - 0.6537157389870546) < 1e-15)
        #expect(abs(seededRandom(seed: 42, index: 1) - 0.7415648787718234) < 1e-15)
        #expect(abs(seededRandom(seed: 12345, index: 0) - 0.9508810691208036) < 1e-15)
        #expect(seededRandom(seed: 0, index: 0) == 0.0)
        #expect(abs(seededRandom(seed: 999_999, index: 5) - 0.6193696364984258) < 1e-15)
    }

    // MARK: - deterministicDriftCenter with large seeds

    @Test func driftCenterHandlesLargeSeed() {
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        // Large seed that could overflow Int32 — tests Int64 wrapping multiplication
        let result = deterministicDriftCenter(
            basePoint: base,
            oldRadius: 1500,
            newRadius: 1400,
            driftSeed: 500_000_000
        )
        let distance = CLLocation(latitude: result.latitude, longitude: result.longitude)
            .distance(from: CLLocation(latitude: base.latitude, longitude: base.longitude))
        #expect(distance <= 50, "Large seed must still produce valid drift within bounds")
    }

    @Test func driftCenterHandlesNegativeSeed() {
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let result = deterministicDriftCenter(
            basePoint: base,
            oldRadius: 1500,
            newRadius: 1400,
            driftSeed: -42
        )
        let distance = CLLocation(latitude: result.latitude, longitude: result.longitude)
            .distance(from: CLLocation(latitude: base.latitude, longitude: base.longitude))
        #expect(distance <= 50, "Negative seed must still produce valid drift")
    }

    // MARK: - evaluateCountdown with explicit now

    @Test func countdownWithExplicitNow() {
        let target = Date(timeIntervalSince1970: 1000)
        let now = Date(timeIntervalSince1970: 997.5) // 2.5s before target
        let phase = CountdownPhase(
            targetDate: target,
            completionText: "GO!",
            showNumericCountdown: true,
            isEnabled: true
        )
        let result = evaluateCountdown(
            phases: [phase],
            now: now,
            currentCountdownNumber: nil,
            currentCountdownText: nil
        )
        #expect(result == .updateNumber(3))
    }

    @Test func countdownWithExplicitNowShowsText() {
        let target = Date(timeIntervalSince1970: 1000)
        let now = Date(timeIntervalSince1970: 1000.5) // 0.5s after target
        let phase = CountdownPhase(
            targetDate: target,
            completionText: "RUN!",
            showNumericCountdown: true,
            isEnabled: true
        )
        let result = evaluateCountdown(
            phases: [phase],
            now: now,
            currentCountdownNumber: nil,
            currentCountdownText: nil
        )
        #expect(result == .showText("RUN!"))
    }

    // MARK: - interpolateZoneCenter edge cases

    @Test func interpolateCenterReturnsInitialWhenNoFinal() {
        let initial = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let result = interpolateZoneCenter(
            initialCenter: initial,
            finalCenter: nil,
            initialRadius: 1500,
            currentRadius: 750
        )
        #expect(result.latitude == initial.latitude)
        #expect(result.longitude == initial.longitude)
    }

    @Test func interpolateCenterReturnsFinalWhenRadiusZero() {
        let initial = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let final_ = CLLocationCoordinate2D(latitude: 51.0, longitude: 5.0)
        let result = interpolateZoneCenter(
            initialCenter: initial,
            finalCenter: final_,
            initialRadius: 1500,
            currentRadius: 0
        )
        #expect(abs(result.latitude - final_.latitude) < 0.0001)
        #expect(abs(result.longitude - final_.longitude) < 0.0001)
    }

    @Test func interpolateCenterMidpoint() {
        let initial = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let final_ = CLLocationCoordinate2D(latitude: 52.0, longitude: 6.0)
        let result = interpolateZoneCenter(
            initialCenter: initial,
            finalCenter: final_,
            initialRadius: 1000,
            currentRadius: 500 // 50% shrunk
        )
        #expect(abs(result.latitude - 51.0) < 0.0001)
        #expect(abs(result.longitude - 5.0) < 0.0001)
    }

    @Test func interpolateCenterClampsNegativeRadius() {
        let initial = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let final_ = CLLocationCoordinate2D(latitude: 52.0, longitude: 6.0)
        let result = interpolateZoneCenter(
            initialCenter: initial,
            finalCenter: final_,
            initialRadius: 1000,
            currentRadius: -100 // past zero → clamped to progress=1
        )
        #expect(abs(result.latitude - final_.latitude) < 0.0001)
        #expect(abs(result.longitude - final_.longitude) < 0.0001)
    }

    @Test func interpolateCenterReturnsInitialWhenZeroInitialRadius() {
        // initialRadius=0 → division by zero guard → returns initial
        let initial = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let final_ = CLLocationCoordinate2D(latitude: 52.0, longitude: 6.0)
        let result = interpolateZoneCenter(
            initialCenter: initial, finalCenter: final_, initialRadius: 0, currentRadius: 0
        )
        #expect(result.latitude == initial.latitude)
        #expect(result.longitude == initial.longitude)
    }

    @Test func interpolateCenterReturnsInitialWhenCurrentExceedsInitial() {
        // currentRadius > initialRadius → progress negative → clamped to 0
        let initial = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let final_ = CLLocationCoordinate2D(latitude: 52.0, longitude: 6.0)
        let result = interpolateZoneCenter(
            initialCenter: initial, finalCenter: final_, initialRadius: 1000, currentRadius: 1500
        )
        #expect(abs(result.latitude - initial.latitude) < 0.0001)
        #expect(abs(result.longitude - initial.longitude) < 0.0001)
    }

    @Test func interpolateCenterSameInitialAndFinal() {
        let point = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let result = interpolateZoneCenter(
            initialCenter: point, finalCenter: point, initialRadius: 1000, currentRadius: 500
        )
        #expect(abs(result.latitude - point.latitude) < 0.0001)
        #expect(abs(result.longitude - point.longitude) < 0.0001)
    }

    // MARK: - deterministicDriftCenter exhaustive edge cases

    @Test func driftCenterNewRadiusZero() {
        // newRadius=0 → maxFromBase=0 → safeDrift=0 → returns base
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let result = deterministicDriftCenter(basePoint: base, oldRadius: 100, newRadius: 0, driftSeed: 42)
        #expect(result.latitude == base.latitude)
        #expect(result.longitude == base.longitude)
    }

    @Test func driftCenterNewRadiusLargerThanOld() {
        // newRadius > oldRadius → maxFromPrev = max(0, negative) = 0 → safeDrift=0 → base
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let result = deterministicDriftCenter(basePoint: base, oldRadius: 100, newRadius: 200, driftSeed: 42)
        #expect(result.latitude == base.latitude)
        #expect(result.longitude == base.longitude)
    }

    @Test func driftCenterMaxIntSeed() {
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let result = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: Int.max)
        let distance = CLLocation(latitude: result.latitude, longitude: result.longitude)
            .distance(from: CLLocation(latitude: base.latitude, longitude: base.longitude))
        #expect(distance <= 50, "Int.max seed must produce valid drift")
    }

    @Test func driftCenterMinIntSeed() {
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let result = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: Int.min)
        let distance = CLLocation(latitude: result.latitude, longitude: result.longitude)
            .distance(from: CLLocation(latitude: base.latitude, longitude: base.longitude))
        #expect(distance <= 50, "Int.min seed must produce valid drift")
    }

    @Test func driftCenterVerySmallDifference() {
        // oldRadius=1001, newRadius=1000 → maxFromPrev=0.5, maxFromBase=500 → safeDrift=0.5
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let result = deterministicDriftCenter(basePoint: base, oldRadius: 1001, newRadius: 1000, driftSeed: 42)
        let distance = CLLocation(latitude: result.latitude, longitude: result.longitude)
            .distance(from: CLLocation(latitude: base.latitude, longitude: base.longitude))
        #expect(distance <= 0.5, "Tiny drift for tiny radius difference")
    }

    @Test func driftCenterNearPole() {
        // Near north pole: metersPerDegreeLng → very small
        let base = CLLocationCoordinate2D(latitude: 89.0, longitude: 4.0)
        let result = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: 42)
        // Should not crash, result should be finite
        #expect(result.latitude.isFinite)
        #expect(result.longitude.isFinite)
    }

    @Test func driftCenterNearDateLine() {
        let base = CLLocationCoordinate2D(latitude: 50.0, longitude: 179.9)
        let result = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: 42)
        #expect(result.latitude.isFinite)
        #expect(result.longitude.isFinite)
    }

    // MARK: - seededRandom extreme edge cases

    @Test func seededRandomNegativeIndex() {
        let val = seededRandom(seed: 42, index: -1)
        #expect(val >= 0.0 && val < 1.0)
    }

    @Test func seededRandomMaxIndex() {
        let val = seededRandom(seed: 42, index: Int.max)
        #expect(val >= 0.0 && val < 1.0)
    }

    // MARK: - processRadiusUpdate edge cases

    @Test func radiusUpdateDeclineZeroStillAdvancesTime() {
        let before = Date.now.addingTimeInterval(-1)
        let result = processRadiusUpdate(
            nextRadiusUpdate: before,
            currentRadius: 1500,
            radiusDeclinePerUpdate: 0,
            radiusIntervalUpdate: 5,
            gameMod: .followTheChicken,
            initialCoordinates: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0),
            currentCircle: CircleOverlay(center: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0), radius: 1500)
        )
        #expect(result != nil)
        #expect(result!.newRadius == 1500, "Zero decline means radius stays the same")
        #expect(result!.isGameOver == false)
    }

    @Test func radiusUpdateFractionalDeclineTruncates() {
        // 0.9 → Int(0.9) = 0 → radius stays the same
        let result = processRadiusUpdate(
            nextRadiusUpdate: Date.now.addingTimeInterval(-1),
            currentRadius: 1500,
            radiusDeclinePerUpdate: 0.9,
            radiusIntervalUpdate: 5,
            gameMod: .followTheChicken,
            initialCoordinates: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0),
            currentCircle: CircleOverlay(center: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0), radius: 1500)
        )
        #expect(result != nil)
        #expect(result!.newRadius == 1500, "0.9 truncates to 0, radius unchanged")
    }

    @Test func radiusUpdateDeclineLargerThanCurrentTriggersGameOver() {
        let result = processRadiusUpdate(
            nextRadiusUpdate: Date.now.addingTimeInterval(-1),
            currentRadius: 50,
            radiusDeclinePerUpdate: 100,
            radiusIntervalUpdate: 5,
            gameMod: .followTheChicken,
            initialCoordinates: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0),
            currentCircle: nil
        )
        #expect(result != nil)
        #expect(result!.isGameOver == true)
        #expect(result!.newRadius == 50, "Game over preserves current radius")
    }

    @Test func radiusUpdateFollowTheChickenNoCircleReturnsNilCircle() {
        let result = processRadiusUpdate(
            nextRadiusUpdate: Date.now.addingTimeInterval(-1),
            currentRadius: 1500,
            radiusDeclinePerUpdate: 100,
            radiusIntervalUpdate: 5,
            gameMod: .followTheChicken,
            initialCoordinates: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0),
            currentCircle: nil // no circle yet (chicken hasn't shared location)
        )
        #expect(result != nil)
        #expect(result!.newCircle == nil, "No circle when no current circle in followTheChicken")
    }

    // MARK: - evaluateCountdown edge cases

    @Test func countdownEmptyPhasesReturnsNoChange() {
        let result = evaluateCountdown(
            phases: [],
            now: .now,
            currentCountdownNumber: nil,
            currentCountdownText: nil
        )
        #expect(result == .noChange)
    }

    @Test func countdownExactlyAtThresholdBoundary() {
        let target = Date(timeIntervalSince1970: 1000)
        let now = Date(timeIntervalSince1970: 997.0) // exactly 3.0s before
        let phase = CountdownPhase(
            targetDate: target, completionText: "GO!",
            showNumericCountdown: true, isEnabled: true
        )
        let result = evaluateCountdown(phases: [phase], now: now,
            currentCountdownNumber: nil, currentCountdownText: nil)
        #expect(result == .updateNumber(3))
    }

    @Test func countdownExactlyOnTarget() {
        let target = Date(timeIntervalSince1970: 1000)
        let now = target // exactly at target → timeToTarget=0 → showText
        let phase = CountdownPhase(
            targetDate: target, completionText: "GO!",
            showNumericCountdown: true, isEnabled: true
        )
        let result = evaluateCountdown(phases: [phase], now: now,
            currentCountdownNumber: nil, currentCountdownText: nil)
        #expect(result == .showText("GO!"))
    }

    @Test func countdownNonNumericPhaseSkipsNumber() {
        let target = Date(timeIntervalSince1970: 1000)
        let now = Date(timeIntervalSince1970: 998) // 2s before
        let phase = CountdownPhase(
            targetDate: target, completionText: "GO!",
            showNumericCountdown: false, // non-numeric
            isEnabled: true
        )
        let result = evaluateCountdown(phases: [phase], now: now,
            currentCountdownNumber: nil, currentCountdownText: nil)
        // Should not show number, and not yet past target → noChange
        #expect(result == .noChange)
    }

    // MARK: - Profanity filter edge cases

    @Test func profanityFilterEmptyString() {
        #expect(ProfanityFilter.containsProfanity("") == false)
    }

    @Test func profanityFilterSingleBlockedWord() {
        #expect(ProfanityFilter.containsProfanity("fuck") == true)
    }

    @Test func profanityFilterMultipleBlockedWords() {
        #expect(ProfanityFilter.containsProfanity("fuck shit merde") == true)
    }

    @Test func profanityFilterNumbersOnly() {
        #expect(ProfanityFilter.containsProfanity("123456") == false)
    }

    @Test func profanityFilterEmojis() {
        #expect(ProfanityFilter.containsProfanity("🐔🎉") == false)
    }

    // MARK: - Enum edge cases

    @Test func gameStatusDecodesEmptyStringToDefault() throws {
        let decoded = try JSONDecoder().decode(Game.GameStatus.self, from: #""""#.data(using: .utf8)!)
        #expect(decoded == .waiting)
    }

    @Test func gameModeDecodesCaseSensitive() throws {
        // "FollowTheChicken" (capital F) should not match → falls back to default
        let decoded = try JSONDecoder().decode(Game.GameMode.self, from: #""FollowTheChicken""#.data(using: .utf8)!)
        #expect(decoded == .followTheChicken)
    }

    // MARK: - generatePowerUps edge cases

    @Test func generatePowerUpsCountZero() {
        let result = generatePowerUps(
            center: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0),
            radius: 1500, count: 0, driftSeed: 42, batchIndex: 0
        )
        #expect(result.isEmpty)
    }

    @Test func generatePowerUpsEmptyEnabledTypes() {
        let result = generatePowerUps(
            center: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0),
            radius: 1500, count: 5, driftSeed: 42, batchIndex: 0,
            enabledTypes: []
        )
        #expect(result.isEmpty)
    }

    @Test func generatePowerUpsIsDeterministic() {
        let center = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let a = generatePowerUps(center: center, radius: 1500, count: 3, driftSeed: 42, batchIndex: 0)
        let b = generatePowerUps(center: center, radius: 1500, count: 3, driftSeed: 42, batchIndex: 0)
        #expect(a.count == b.count)
        for i in 0..<a.count {
            #expect(a[i].id == b[i].id)
            #expect(a[i].location.latitude == b[i].location.latitude)
            #expect(a[i].location.longitude == b[i].location.longitude)
        }
    }

    @Test func generatePowerUpsAllWithinZone() {
        let center = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let radius = 1500.0
        let powerUps = generatePowerUps(center: center, radius: radius, count: 20, driftSeed: 12345, batchIndex: 0)
        let centerLoc = CLLocation(latitude: center.latitude, longitude: center.longitude)
        for pu in powerUps {
            let puLoc = CLLocation(latitude: pu.location.latitude, longitude: pu.location.longitude)
            let distance = centerLoc.distance(from: puLoc)
            // 0.85 factor means max distance = 1500 * 0.85 = 1275m
            #expect(distance <= radius * 0.85 + 1, "Power-up at \(distance)m exceeds zone")
        }
    }

    @Test func generatePowerUpsDifferentSeedsProduceDifferentPositions() {
        let center = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let a = generatePowerUps(center: center, radius: 1500, count: 1, driftSeed: 111, batchIndex: 0)
        let b = generatePowerUps(center: center, radius: 1500, count: 1, driftSeed: 222, batchIndex: 0)
        let different = a[0].location.latitude != b[0].location.latitude ||
                        a[0].location.longitude != b[0].location.longitude
        #expect(different, "Different seeds should produce different positions")
    }

    @Test func generatePowerUpsRadiusZeroSpawnsAtCenter() {
        let center = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let powerUps = generatePowerUps(center: center, radius: 0, count: 3, driftSeed: 42, batchIndex: 0)
        for pu in powerUps {
            #expect(abs(pu.location.latitude - center.latitude) < 0.0001)
            #expect(abs(pu.location.longitude - center.longitude) < 0.0001)
        }
    }

    // MARK: - Cross-platform parity: deterministicDriftCenter

    /// Hardcoded values verified against Android. If these break, parity is lost.
    @Test func driftCenterCrossPlatformParity() {
        let base = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let tests: [(Double, Double, Int, Double, Double)] = [
            (1500, 1400, 42,     50.846975022854906, 4.352811404529953),
            (1500, 1400, 12345,  50.846304286836016, 4.35242679292988),
            (1500, 1400, 999999, 50.846981166449616, 4.353113589623775),
            (1000,  900, 42,     50.84699857089946,  4.352990826261173),
            (2000, 1800, 54321,  50.84676052483228,  4.352177942909372),
            ( 500,  400, 1,      50.846909360931114, 4.352834175494442),
        ]
        for (oldR, newR, seed, expectedLat, expectedLng) in tests {
            let r = deterministicDriftCenter(basePoint: base, oldRadius: oldR, newRadius: newR, driftSeed: seed)
            #expect(abs(r.latitude - expectedLat) < 1e-12, "drift(\(oldR),\(newR),\(seed)) lat mismatch")
            #expect(abs(r.longitude - expectedLng) < 1e-12, "drift(\(oldR),\(newR),\(seed)) lng mismatch")
        }
    }

    // MARK: - Cross-platform parity: interpolateZoneCenter

    @Test func interpolateCenterCrossPlatformParity() {
        let initial = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        let final_ = CLLocationCoordinate2D(latitude: 51.0, longitude: 5.0)
        let tests: [(Double, Double, Double, Double)] = [
            (1500, 1500, 50.0,                4.0),
            (1500,  750, 50.5,                4.5),
            (1500,    0, 51.0,                5.0),
            (1500, 1000, 50.333333333333336,  4.333333333333333),
            (1500,  500, 50.666666666666664,  4.666666666666667),
            (1500,  100, 50.93333333333333,   4.933333333333334),
        ]
        for (iR, cR, expectedLat, expectedLng) in tests {
            let r = interpolateZoneCenter(initialCenter: initial, finalCenter: final_, initialRadius: iR, currentRadius: cR)
            #expect(abs(r.latitude - expectedLat) < 1e-12, "interp(\(iR),\(cR)) lat mismatch")
            #expect(abs(r.longitude - expectedLng) < 1e-12, "interp(\(iR),\(cR)) lng mismatch")
        }
    }

    // MARK: - Cross-platform parity: calculateNormalModeSettings

    @Test func normalModeSettingsCrossPlatformParity() {
        let tests: [(Double, Double, Double)] = [
            (1500,  60, 116.66666666666667),
            (1500,  90,  77.77777777777777),
            (1500, 120,  58.333333333333336),
            (1500, 150,  46.666666666666664),
            (1500, 180,  38.888888888888886),
            ( 500,  90,  22.22222222222222),
            (3000, 120, 120.83333333333333),
            ( 100,  60,   0.0),
            (  101, 90,   0.05555555555555555),
            (50000,180,1386.111111111111),
        ]
        for (radius, duration, expectedDecline) in tests {
            let (interval, decline) = calculateNormalModeSettings(initialRadius: radius, gameDurationMinutes: duration)
            #expect(interval == 5.0, "Interval must always be 5.0")
            #expect(abs(decline - expectedDecline) < 1e-10, "normalMode(\(radius),\(duration)) decline mismatch: \(decline) vs \(expectedDecline)")
        }
    }

    // MARK: - Cross-platform parity: generatePowerUps

    @Test func generatePowerUpsCrossPlatformParity() {
        let center = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        let powerUps = generatePowerUps(center: center, radius: 1500, count: 5, driftSeed: 42, batchIndex: 0)

        let expected: [(String, Double, Double)] = [
            ("pu-0-0-1302", 50.85190592354773, 4.347959996611229),
            ("pu-0-1-1429", 50.85139253538571, 4.358528048360003),
            ("pu-0-2-1556", 50.84476488107779, 4.362537022917026),
            ("pu-0-3-1683", 50.84061364403051, 4.353763055127964),
            ("pu-0-4-1810", 50.84376473675584, 4.344824231076483),
        ]

        #expect(powerUps.count == 5)
        for (i, (expectedId, expectedLat, expectedLng)) in expected.enumerated() {
            #expect(powerUps[i].id == expectedId, "PU[\(i)] id mismatch")
            #expect(abs(powerUps[i].location.latitude - expectedLat) < 1e-10, "PU[\(i)] lat mismatch")
            #expect(abs(powerUps[i].location.longitude - expectedLng) < 1e-10, "PU[\(i)] lng mismatch")
        }
    }
}
