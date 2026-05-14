//
//  ZoneCalculationTests.swift
//  PoulePartyTests
//
//  PP-64 — strict goldens for the PP-13 / PP-14 zone helpers
//  (`Models/GameSettings.swift`). Mirrors the Kotlin sibling
//  `ZoneCalculationTest.kt` byte-for-byte and the TS reference
//  `functions/test/zoneCalculation.test.ts` formula tests. Any drift
//  between iOS, Android, or the Cloud Function will fail one of these
//  on every platform that's wrong — the cross-platform contract is
//  that `computeZoneRadius` on the same inputs returns the same
//  number to within 1 m (sub-millimetre after the haversine round).
//

import CoreLocation
import Testing
@testable import PouleParty

struct ZoneCalculationTests {

    /// Tolerance for the radius-from-pin-distance goldens. The test
    /// constructs pin offsets via the `1° latitude ≈ 111_111 m`
    /// approximation; CLLocation / Android `Location.distanceBetween`
    /// both use the WGS-84 ellipsoid which deviates by ~0.02 % at this
    /// latitude. At D = 10 km that's still under 20 m — well below the
    /// `D × 1.5` interior margin so the test is in the noise. 25 m
    /// covers all distances up to 10 km.
    static let metresTolerance: Double = 25.0

    // MARK: - computeZoneRadius (stayInTheZone) — golden distances

    @Test func radiusStayInTheZoneAtFiftyMeters() {
        // D = 50 m → max(75, 300, 800) = 800 (floor wins).
        let start = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let finalCenter = CLLocationCoordinate2D(latitude: 50.85 + (50.0 / 111_111.0), longitude: 4.35)
        let radius = computeZoneRadius(
            start: start, finalCenter: finalCenter,
            gameMode: .stayInTheZone, radiusHint: nil
        )
        #expect(abs(radius - zoneMinimumInitialRadiusMeters) < Self.metresTolerance)
    }

    @Test func radiusStayInTheZoneAtFiveHundredMeters() {
        // D = 500 m → max(750, 750, 800) = 800 (floor wins).
        let start = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let finalCenter = CLLocationCoordinate2D(latitude: 50.85 + (500.0 / 111_111.0), longitude: 4.35)
        let radius = computeZoneRadius(
            start: start, finalCenter: finalCenter,
            gameMode: .stayInTheZone, radiusHint: nil
        )
        #expect(abs(radius - 800) < Self.metresTolerance)
    }

    @Test func radiusStayInTheZoneAtOneKilometre() {
        // D = 1000 m → max(1500, 1250, 800) = 1500 (D × 1.5 dominates).
        let start = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let finalCenter = CLLocationCoordinate2D(latitude: 50.85 + (1000.0 / 111_111.0), longitude: 4.35)
        let radius = computeZoneRadius(
            start: start, finalCenter: finalCenter,
            gameMode: .stayInTheZone, radiusHint: nil
        )
        #expect(abs(radius - 1500) < Self.metresTolerance)
    }

    @Test func radiusStayInTheZoneAtTwoKilometres() {
        // D = 2000 m → max(3000, 2250, 800) = 3000.
        let start = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let finalCenter = CLLocationCoordinate2D(latitude: 50.85 + (2000.0 / 111_111.0), longitude: 4.35)
        let radius = computeZoneRadius(
            start: start, finalCenter: finalCenter,
            gameMode: .stayInTheZone, radiusHint: nil
        )
        #expect(abs(radius - 3000) < Self.metresTolerance)
    }

    @Test func radiusStayInTheZoneAtTenKilometres() {
        // D = 10 000 m → max(15 000, 10 250, 800) = 15 000.
        let start = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let finalCenter = CLLocationCoordinate2D(latitude: 50.85 + (10_000.0 / 111_111.0), longitude: 4.35)
        let radius = computeZoneRadius(
            start: start, finalCenter: finalCenter,
            gameMode: .stayInTheZone, radiusHint: nil
        )
        #expect(abs(radius - 15_000) < Self.metresTolerance)
    }

    @Test func radiusStayInTheZoneNilFinalReturnsFloor() {
        let start = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let radius = computeZoneRadius(
            start: start, finalCenter: nil,
            gameMode: .stayInTheZone, radiusHint: nil
        )
        #expect(radius == zoneMinimumInitialRadiusMeters)
    }

    @Test func radiusStayInTheZoneInteriorMarginInvariant() {
        // PP-69 / PP-13 contract: for every D ≤ 10 km, the interior
        // margin (initialRadius − D) is ≥ 200 m so the zone never
        // collapses early. Sweep at 100 m steps to lock it in.
        let start = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        for d in stride(from: 100.0, through: 10_000.0, by: 100.0) {
            let finalCenter = CLLocationCoordinate2D(
                latitude: 50.85 + (d / 111_111.0),
                longitude: 4.35
            )
            let radius = computeZoneRadius(
                start: start, finalCenter: finalCenter,
                gameMode: .stayInTheZone, radiusHint: nil
            )
            let margin = radius - d
            #expect(margin >= zoneInteriorMarginMeters - 1, "D=\(d) margin=\(margin)")
        }
    }

    // MARK: - computeZoneRadius (followTheChicken)

    @Test func radiusFollowTheChickenSmall() {
        let r = computeZoneRadius(
            start: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            finalCenter: nil,
            gameMode: .followTheChicken,
            radiusHint: 500
        )
        #expect(r == 500)
    }

    @Test func radiusFollowTheChickenMedium() {
        let r = computeZoneRadius(
            start: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            finalCenter: nil,
            gameMode: .followTheChicken,
            radiusHint: 1000
        )
        #expect(r == 1000)
    }

    @Test func radiusFollowTheChickenLarge() {
        let r = computeZoneRadius(
            start: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            finalCenter: nil,
            gameMode: .followTheChicken,
            radiusHint: 2000
        )
        #expect(r == 2000)
    }

    @Test func radiusFollowTheChickenInvalidHintFallsBackToMedium() {
        let r = computeZoneRadius(
            start: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            finalCenter: nil,
            gameMode: .followTheChicken,
            radiusHint: 1337
        )
        #expect(r == 1000)
    }

    @Test func radiusFollowTheChickenNilHintFallsBackToMedium() {
        let r = computeZoneRadius(
            start: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            finalCenter: nil,
            gameMode: .followTheChicken,
            radiusHint: nil
        )
        #expect(r == 1000)
    }

    // MARK: - generateDriftSeed

    @Test func generateDriftSeedNeverReturnsZero() {
        // 0 is treated as "no drift" by the runtime PRNG. The helper
        // re-rolls until it gets a positive value — sampling 1000 times
        // is statistically more than enough.
        for _ in 0..<1000 {
            #expect(generateDriftSeed() > 0)
        }
    }

    // MARK: - interpolateZoneCenter — strict goldens (mirrors TS / Kotlin)

    static let interpolateTolerance: Double = 1e-9

    @Test func interpolateAtZeroProgressReturnsInitial() {
        let initial = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let out = interpolateZoneCenter(
            initialCenter: initial,
            finalCenter: CLLocationCoordinate2D(latitude: 50.86, longitude: 4.36),
            initialRadius: 1500,
            currentRadius: 1500
        )
        #expect(out.latitude == initial.latitude)
        #expect(out.longitude == initial.longitude)
    }

    @Test func interpolateAtFiftyPercentReturnsMidpoint() {
        let out = interpolateZoneCenter(
            initialCenter: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            finalCenter: CLLocationCoordinate2D(latitude: 50.87, longitude: 4.37),
            initialRadius: 1500,
            currentRadius: 750
        )
        #expect(abs(out.latitude - 50.86) < Self.interpolateTolerance)
        #expect(abs(out.longitude - 4.36) < Self.interpolateTolerance)
    }

    @Test func interpolateAtOneHundredPercentReturnsFinal() {
        let out = interpolateZoneCenter(
            initialCenter: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            finalCenter: CLLocationCoordinate2D(latitude: 50.87, longitude: 4.37),
            initialRadius: 1500,
            currentRadius: 0
        )
        #expect(abs(out.latitude - 50.87) < Self.interpolateTolerance)
        #expect(abs(out.longitude - 4.37) < Self.interpolateTolerance)
    }

    // MARK: - deterministicDriftCenter — strict goldens for the same
    // seed → same output contract (char-by-char with Kotlin / TS).

    @Test func driftDeterministicSameInputsSameOutput() {
        let base = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let a = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: 12345)
        let b = deterministicDriftCenter(basePoint: base, oldRadius: 1500, newRadius: 1400, driftSeed: 12345)
        #expect(a.latitude == b.latitude)
        #expect(a.longitude == b.longitude)
    }

    @Test func driftPinnedGoldenSeed12345_1500to1400() {
        let out = deterministicDriftCenter(
            basePoint: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            oldRadius: 1500,
            newRadius: 1400,
            driftSeed: 12345
        )
        // Same constants as `ParityGoldenTests.driftSeed12345_1500to1400`.
        // Kept here so a `ZoneCalculationTests` run on its own still
        // catches a drift regression without depending on the wider
        // parity suite.
        #expect(abs(out.latitude - 50.84923912478917) < Self.interpolateTolerance)
        #expect(abs(out.longitude - 4.349340564597558) < Self.interpolateTolerance)
    }

    // MARK: - pickInitialZoneCenter — same seed yields same center

    @Test func pickInitialZoneCenterIsDeterministicForSameSeed() {
        let start = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let finalCenter = CLLocationCoordinate2D(latitude: 50.86, longitude: 4.36)
        let a = pickInitialZoneCenter(startPin: start, finalCenter: finalCenter, radius: 1668, seed: 42)
        let b = pickInitialZoneCenter(startPin: start, finalCenter: finalCenter, radius: 1668, seed: 42)
        // CLLocation.distance(from:) has sub-nanometre non-determinism
        // on the simulator (likely a cached coordinate transform). The
        // useful contract is "same seed gets functionally the same
        // centre" — bitwise equality is not achievable on iOS because
        // the radius-budget calculation uses CLLocation. Android +
        // Cloud Function use deterministic haversine and DO get
        // bitwise equality (`ZoneCalculationTest`).
        #expect(abs(a.latitude - b.latitude) < 1e-9)
        #expect(abs(a.longitude - b.longitude) < 1e-9)
    }

    @Test func pickInitialZoneCenterRespectsContainmentLens() {
        // For ANY seed in 1...32, the picked center must keep both pins
        // inside the disc of `radius` around it. PP-13 / PP-69 contract:
        // the user-placed pins live inside the disc as markers, not at
        // its center, and never escape it.
        let start = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let finalCenter = CLLocationCoordinate2D(latitude: 50.86, longitude: 4.36)
        let radius: Double = 1668
        for seed in 1...32 {
            let centre = pickInitialZoneCenter(startPin: start, finalCenter: finalCenter, radius: radius, seed: seed)
            let centreLoc = CLLocation(latitude: centre.latitude, longitude: centre.longitude)
            let startLoc = CLLocation(latitude: start.latitude, longitude: start.longitude)
            let finalLoc = CLLocation(latitude: finalCenter.latitude, longitude: finalCenter.longitude)
            #expect(centreLoc.distance(from: startLoc) <= radius + 1, "seed \(seed) start escaped")
            #expect(centreLoc.distance(from: finalLoc) <= radius + 1, "seed \(seed) final escaped")
        }
    }
}
