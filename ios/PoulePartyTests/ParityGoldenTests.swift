//
//  ParityGoldenTests.swift
//  PoulePartyTests
//
//  Locks cross-platform determinism for the math that iOS, Android, and the
//  Cloud Functions all run. The golden values are authored once in the TS
//  reference (`functions/test/parity.test.ts`) and copied verbatim here +
//  into the Android sibling (`ParityGoldenTest.kt`). If any platform ever
//  drifts, these tests flag it immediately instead of hunters seeing
//  different decoys / drifts / spawn positions in the same game.
//

import CoreLocation
import Testing
@testable import PouleParty

struct ParityGoldenTests {

    // Geo math uses double precision, exact equality can fail on harmless
    // last-bit divergences between platforms. 1e-9 ° ≈ 0.11 mm, well below
    // anything visible on the map.
    static let tolerance: Double = 1e-9

    // ─── interpolateZoneCenter ───────────────────────────────

    @Test func interpolateHalfProgress() {
        let out = interpolateZoneCenter(
            initialCenter: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            finalCenter: CLLocationCoordinate2D(latitude: 50.87, longitude: 4.37),
            initialRadius: 1500,
            currentRadius: 750
        )
        #expect(abs(out.latitude - 50.86) < Self.tolerance)
        #expect(abs(out.longitude - 4.36) < Self.tolerance)
    }

    @Test func interpolateMissingFinalReturnsInitial() {
        let initial = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let out = interpolateZoneCenter(
            initialCenter: initial,
            finalCenter: nil,
            initialRadius: 1500,
            currentRadius: 750
        )
        #expect(out.latitude == initial.latitude)
        #expect(out.longitude == initial.longitude)
    }

    @Test func interpolateZeroProgressReturnsFinal() {
        let out = interpolateZoneCenter(
            initialCenter: CLLocationCoordinate2D(latitude: 48.8, longitude: 2.3),
            finalCenter: CLLocationCoordinate2D(latitude: 48.9, longitude: 2.4),
            initialRadius: 2000,
            currentRadius: 0
        )
        #expect(abs(out.latitude - 48.9) < Self.tolerance)
        #expect(abs(out.longitude - 2.4) < Self.tolerance)
    }

    @Test func interpolateFullRadiusReturnsInitial() {
        let initial = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let out = interpolateZoneCenter(
            initialCenter: initial,
            finalCenter: CLLocationCoordinate2D(latitude: 50.9, longitude: 4.4),
            initialRadius: 1500,
            currentRadius: 1500
        )
        #expect(out.latitude == initial.latitude)
        #expect(out.longitude == initial.longitude)
    }

    // ─── deterministicDriftCenter ────────────────────────────

    @Test func driftSeed12345_1500to1400() {
        let out = deterministicDriftCenter(
            basePoint: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            oldRadius: 1500,
            newRadius: 1400,
            driftSeed: 12345
        )
        #expect(abs(out.latitude - 50.849704286836015) < Self.tolerance)
        #expect(abs(out.longitude - 4.349626765727747) < Self.tolerance)
    }

    @Test func driftSeed42_2000to1800() {
        let out = deterministicDriftCenter(
            basePoint: CLLocationCoordinate2D(latitude: 48.8, longitude: 2.3),
            oldRadius: 2000,
            newRadius: 1800,
            driftSeed: 42
        )
        #expect(abs(out.latitude - 48.800889182782186) < Self.tolerance)
        #expect(abs(out.longitude - 2.3001280811249516) < Self.tolerance)
    }

    @Test func driftNewRadiusZeroReturnsBase() {
        let base = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let out = deterministicDriftCenter(
            basePoint: base,
            oldRadius: 500,
            newRadius: 0,
            driftSeed: 7
        )
        #expect(out.latitude == base.latitude)
        #expect(out.longitude == base.longitude)
    }

    @Test func driftSameRadiusReturnsBase() {
        let base = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let out = deterministicDriftCenter(
            basePoint: base,
            oldRadius: 1500,
            newRadius: 1500,
            driftSeed: 12345
        )
        #expect(out.latitude == base.latitude)
        #expect(out.longitude == base.longitude)
    }

    // ─── generatePowerUps ────────────────────────────────────

    @Test func generatePowerUpsBatch1Count3Seed12345() {
        let out = generatePowerUps(
            center: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            radius: 1500,
            count: 3,
            driftSeed: 12345,
            batchIndex: 1,
            enabledTypes: ["invisibility", "zoneFreeze", "radarPing"]
        )
        #expect(out.count == 3)

        #expect(out[0].id == "pu-1-0-371690")
        #expect(out[0].type == .radarPing)
        #expect(abs(out[0].coordinate.latitude - 50.85167604533923) < Self.tolerance)
        #expect(abs(out[0].coordinate.longitude - 4.36041475997135) < Self.tolerance)

        #expect(out[1].id == "pu-1-1-371817")
        #expect(out[1].type == .invisibility)
        #expect(abs(out[1].coordinate.latitude - 50.84442779979479) < Self.tolerance)
        #expect(abs(out[1].coordinate.longitude - 4.356573765300285) < Self.tolerance)

        #expect(out[2].id == "pu-1-2-371944")
        #expect(out[2].type == .zoneFreeze)
        #expect(abs(out[2].coordinate.latitude - 50.84439414095354) < Self.tolerance)
        #expect(abs(out[2].coordinate.longitude - 4.344395523809516) < Self.tolerance)
    }

    // ─── seededRandom ────────────────────────────────────────

    @Test func seededRandomGolden() {
        // Kotlin/TS/Swift must all emit these for `splitmix64`-style PRNG.
        #expect(abs(seededRandom(seed: 0, index: 0) - 0.0) < Self.tolerance)
        #expect(abs(seededRandom(seed: 1, index: 0) - 0.3381666012719898) < Self.tolerance)
        #expect(abs(seededRandom(seed: 12345, index: 0) - 0.9508810691208036) < Self.tolerance)
        #expect(abs(seededRandom(seed: 12345, index: 1) - 0.13307966866142731) < Self.tolerance)
        #expect(abs(seededRandom(seed: 42, index: 7) - 0.21840519371218445) < Self.tolerance)
        #expect(abs(seededRandom(seed: -1, index: 0) - 0.7063039534139497) < Self.tolerance)
    }

    // ─── generatePowerUps, ordering & edges ─────────────────

    @Test func generatePowerUpsPreservesEnabledTypesOrder() {
        // Same inputs as the main generatePowerUps test, but enabledTypes is
        // reversed → types at each index must also permute. If iOS ever
        // drifts back to allCases-filter order, this fails.
        let out = generatePowerUps(
            center: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            radius: 1500,
            count: 3,
            driftSeed: 12345,
            batchIndex: 1,
            enabledTypes: ["radarPing", "zoneFreeze", "invisibility"]
        )
        #expect(out.map(\.type) == [.invisibility, .radarPing, .zoneFreeze])
    }

    @Test func generatePowerUpsSingleTypeFillsEverySlot() {
        let out = generatePowerUps(
            center: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            radius: 1500,
            count: 3,
            driftSeed: 12345,
            batchIndex: 1,
            enabledTypes: ["invisibility"]
        )
        #expect(out.map(\.type) == [.invisibility, .invisibility, .invisibility])
    }

    @Test func generatePowerUpsZeroCountReturnsEmpty() {
        let out = generatePowerUps(
            center: CLLocationCoordinate2D(latitude: 0, longitude: 0),
            radius: 1500,
            count: 0,
            driftSeed: 12345,
            batchIndex: 1,
            enabledTypes: ["invisibility"]
        )
        #expect(out.isEmpty)
    }

    @Test func generatePowerUpsEmptyTypesReturnsEmpty() {
        let out = generatePowerUps(
            center: CLLocationCoordinate2D(latitude: 0, longitude: 0),
            radius: 1500,
            count: 5,
            driftSeed: 12345,
            batchIndex: 1,
            enabledTypes: []
        )
        #expect(out.isEmpty)
    }

    @Test func generatePowerUpsNegativeSeedBatch0() {
        let out = generatePowerUps(
            center: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            radius: 1500,
            count: 2,
            driftSeed: -1,
            batchIndex: 0,
            enabledTypes: ["invisibility", "radarPing"]
        )
        #expect(out.count == 2)
        #expect(out[0].id == "pu-0-0-31")
        #expect(out[0].type == .radarPing)
        #expect(abs(out[0].coordinate.latitude - 50.85543657219541) < Self.tolerance)
        #expect(abs(out[0].coordinate.longitude - 4.3525392519978965) < Self.tolerance)
        #expect(out[1].id == "pu-0-1-96")
        #expect(out[1].type == .invisibility)
        #expect(abs(out[1].coordinate.latitude - 50.85637613935454) < Self.tolerance)
        #expect(abs(out[1].coordinate.longitude - 4.362005903305925) < Self.tolerance)
    }

    // ─── interpolate edges ───────────────────────────────────

    @Test func interpolateClampsWhenCurrentRadiusExceedsInitial() {
        // currentRadius > initialRadius → progress is negative → clamped to
        // 0 → returns initialCenter. Happens briefly when a client sees a
        // stale local radius while the game config just arrived.
        let initial = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let out = interpolateZoneCenter(
            initialCenter: initial,
            finalCenter: CLLocationCoordinate2D(latitude: 50.87, longitude: 4.37),
            initialRadius: 1500,
            currentRadius: 2000
        )
        #expect(out.latitude == initial.latitude)
        #expect(out.longitude == initial.longitude)
    }

    @Test func interpolateInitialRadiusZeroIsGuard() {
        let initial = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let out = interpolateZoneCenter(
            initialCenter: initial,
            finalCenter: CLLocationCoordinate2D(latitude: 50.9, longitude: 4.4),
            initialRadius: 0,
            currentRadius: 0
        )
        #expect(out.latitude == initial.latitude)
        #expect(out.longitude == initial.longitude)
    }

    // ─── drift edges ─────────────────────────────────────────

    @Test func driftBigShrink() {
        let out = deterministicDriftCenter(
            basePoint: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            oldRadius: 2000,
            newRadius: 500,
            driftSeed: 777
        )
        #expect(abs(out.latitude - 50.849217523608516) < Self.tolerance)
        #expect(abs(out.longitude - 4.34728423145545) < Self.tolerance)
    }

    @Test func driftNegativeSeed() {
        let out = deterministicDriftCenter(
            basePoint: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            oldRadius: 1500,
            newRadius: 1400,
            driftSeed: -99
        )
        #expect(abs(out.latitude - 50.85023537723916) < Self.tolerance)
        #expect(abs(out.longitude - 4.350282673772987) < Self.tolerance)
    }

    // ─── finalCenter invariant (see functions/test/parity.test.ts) ─

    /// Flat-earth distance in meters between two coordinates. Matches the
    /// same conversion `deterministicDriftCenter` uses internally, so the
    /// invariant check is self-consistent.
    private func distMeters(
        _ a: CLLocationCoordinate2D,
        _ b: CLLocationCoordinate2D
    ) -> Double {
        let metersPerDegreeLat = 111_320.0
        let metersPerDegreeLng = 111_320.0 * cos(a.latitude * .pi / 180.0)
        let dLatM = (b.latitude - a.latitude) * metersPerDegreeLat
        let dLngM = (b.longitude - a.longitude) * metersPerDegreeLng
        return (dLatM * dLatM + dLngM * dLngM).squareRoot()
    }

    @Test func driftFinalCenterConstrainsDriftNearEdge() {
        let final = CLLocationCoordinate2D(latitude: 50.86, longitude: 4.36)
        let out = deterministicDriftCenter(
            basePoint: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            oldRadius: 2000,
            newRadius: 1400,
            driftSeed: 12345,
            finalCenter: final
        )
        #expect(abs(out.latitude - 50.84951207475732) < Self.tolerance)
        #expect(abs(out.longitude - 4.349384165316106) < Self.tolerance)
        #expect(distMeters(out, final) <= 1400)
    }

    @Test func driftMissingFinalLeavesExistingBehaviorUntouched() {
        // Explicitly passing `finalCenter: nil` must produce the exact same
        // output as omitting it — both must match the legacy golden.
        let explicitNil = deterministicDriftCenter(
            basePoint: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            oldRadius: 1500,
            newRadius: 1400,
            driftSeed: 12345,
            finalCenter: nil
        )
        #expect(abs(explicitNil.latitude - 50.849704286836015) < Self.tolerance)
        #expect(abs(explicitNil.longitude - 4.349626765727747) < Self.tolerance)
    }

    @Test func driftFinalCenterOutsideNewCircleReturnsBase() {
        // finalCenter 150 m north of base, newRadius 100 → maxFromFinal
        // clamps to 0, drift returns basePoint verbatim.
        let base = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let farFinal = CLLocationCoordinate2D(
            latitude: 50.85 + 150.0 / 111_320.0,
            longitude: 4.35
        )
        let out = deterministicDriftCenter(
            basePoint: base,
            oldRadius: 200,
            newRadius: 100,
            driftSeed: 12345,
            finalCenter: farFinal
        )
        #expect(out.latitude == base.latitude)
        #expect(out.longitude == base.longitude)
    }

    @Test func driftInvariantSweepFinalCenterInsideEveryCircle() {
        // Exhaustive property sweep mirroring the TS parity test: 100 seeds
        // × 7 finalCenter distances × 10 shrink steps. If the invariant
        // breaks for any combination, the platform has drifted.
        let initialCenter = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let initialRadius = 1500.0
        let finalDistancesM = [0.0, 100, 400, 700, 1000, 1300, 1499]
        for dM in finalDistancesM {
            let finalCenter = CLLocationCoordinate2D(
                latitude: initialCenter.latitude + dM / 111_320.0,
                longitude: initialCenter.longitude
            )
            for seed in 1...100 {
                var radius = initialRadius
                for _ in 0..<10 {
                    let newRadius = radius - 100
                    if newRadius <= 0 { break }
                    let interpolated = interpolateZoneCenter(
                        initialCenter: initialCenter,
                        finalCenter: finalCenter,
                        initialRadius: initialRadius,
                        currentRadius: newRadius
                    )
                    let drifted = deterministicDriftCenter(
                        basePoint: interpolated,
                        oldRadius: radius,
                        newRadius: newRadius,
                        driftSeed: seed,
                        finalCenter: finalCenter
                    )
                    #expect(distMeters(drifted, finalCenter) <= newRadius)
                    radius = newRadius
                }
            }
        }
    }

    // ─── applyJammerNoise, iOS ↔ Android golden ─────────────

    @Test func jammerGolden_seed12345_now0() {
        let out = applyJammerNoise(
            to: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            driftSeed: 12345,
            now: Date(timeIntervalSince1970: 0)
        )
        #expect(abs(out.latitude - 50.851623171848836) < Self.tolerance)
        #expect(abs(out.longitude - 4.348679086807181) < Self.tolerance)
    }

    @Test func jammerWithinSameSecondIsStable() {
        // Three calls within the same 1 s bucket must produce identical output.
        let coord = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let a = applyJammerNoise(to: coord, driftSeed: 12345, now: Date(timeIntervalSince1970: 0))
        let b = applyJammerNoise(to: coord, driftSeed: 12345, now: Date(timeIntervalSince1970: 0.5))
        let c = applyJammerNoise(to: coord, driftSeed: 12345, now: Date(timeIntervalSince1970: 0.999))
        #expect(a.latitude == b.latitude)
        #expect(a.longitude == b.longitude)
        #expect(a.latitude == c.latitude)
        #expect(a.longitude == c.longitude)
    }

    @Test func jammerCrossesBucketAt1Second() {
        let coord = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let before = applyJammerNoise(to: coord, driftSeed: 12345, now: Date(timeIntervalSince1970: 0.999))
        let after = applyJammerNoise(to: coord, driftSeed: 12345, now: Date(timeIntervalSince1970: 1.0))
        // Next bucket → should emit a different (but still deterministic) offset.
        #expect(before.latitude != after.latitude || before.longitude != after.longitude)
        // Locked value at bucket 1, must match the Android/TS golden.
        #expect(abs(after.latitude - 50.84841369080797) < Self.tolerance)
        #expect(abs(after.longitude - 4.351738880923983) < Self.tolerance)
    }

    @Test func jammerGoldenZeroSeedZeroNow() {
        let out = applyJammerNoise(
            to: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            driftSeed: 0,
            now: Date(timeIntervalSince1970: 0)
        )
        #expect(abs(out.latitude - 50.8482) < Self.tolerance)
        #expect(abs(out.longitude - 4.3513799189095685) < Self.tolerance)
    }

    @Test func jammerGoldenNegativeSeed() {
        let out = applyJammerNoise(
            to: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            driftSeed: -1,
            now: Date(timeIntervalSince1970: 0)
        )
        #expect(abs(out.latitude - 50.850742694232295) < Self.tolerance)
        #expect(abs(out.longitude - 4.351418194513019) < Self.tolerance)
    }

    @Test func jammerGoldenRealtimeBucket() {
        // Realistic Stripe-era timestamp, makes sure the 64-bit XOR doesn't
        // silently drop bits on either platform.
        let out = applyJammerNoise(
            to: CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35),
            driftSeed: 777,
            now: Date(timeIntervalSince1970: 1_700_000_000)
        )
        #expect(abs(out.latitude - 50.85092634988487) < Self.tolerance)
        #expect(abs(out.longitude - 4.3504428176455745) < Self.tolerance)
    }

    @Test func jammerKeepsOutputWithinHalfNoiseBounds() {
        // Exhaustive sweep: 1000 different buckets, all outputs stay within
        // ±halfNoise regardless of seed. Catches the "seededRandom returned
        // something outside [0,1)" class of bugs.
        let coord = CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35)
        let halfNoise = AppConstants.jammerNoiseDegrees / 2.0
        for i in 0..<1000 {
            let out = applyJammerNoise(
                to: coord,
                driftSeed: 12345,
                now: Date(timeIntervalSince1970: TimeInterval(i))
            )
            #expect(abs(out.latitude - coord.latitude) <= halfNoise + 1e-12)
            #expect(abs(out.longitude - coord.longitude) <= halfNoise + 1e-12)
        }
    }
}
