//
//  MapOverlaysTests.swift
//  PoulePartyTests
//
//  Tests for the pure helpers in MapOverlays.swift, currently:
//   - powerUpPulseAlpha(at:periodSeconds:minAlpha:maxAlpha:)
//

import Foundation
import Testing
@testable import PouleParty

struct MapOverlaysTests {

    // MARK: - powerUpPulseAlpha

    @Test func pulseAlphaAtTimeZeroReturnsMidpoint() {
        // sin(0) = 0 → (0+1)/2 = 0.5 → min + 0.5*(max-min) = midpoint
        let alpha = powerUpPulseAlpha(at: 0, periodSeconds: 2.0, minAlpha: 0.08, maxAlpha: 0.18)
        #expect(abs(alpha - 0.13) < 0.001)
    }

    @Test func pulseAlphaAtQuarterPeriodReturnsMax() {
        // sin(π/2) = 1 → (1+1)/2 = 1 → maxAlpha
        let alpha = powerUpPulseAlpha(at: 0.5, periodSeconds: 2.0, minAlpha: 0.08, maxAlpha: 0.18)
        #expect(abs(alpha - 0.18) < 0.001)
    }

    @Test func pulseAlphaAtHalfPeriodReturnsMidpoint() {
        // sin(π) = 0 → midpoint
        let alpha = powerUpPulseAlpha(at: 1.0, periodSeconds: 2.0, minAlpha: 0.08, maxAlpha: 0.18)
        #expect(abs(alpha - 0.13) < 0.001)
    }

    @Test func pulseAlphaAtThreeQuarterPeriodReturnsMin() {
        // sin(3π/2) = -1 → (-1+1)/2 = 0 → minAlpha
        let alpha = powerUpPulseAlpha(at: 1.5, periodSeconds: 2.0, minAlpha: 0.08, maxAlpha: 0.18)
        #expect(abs(alpha - 0.08) < 0.001)
    }

    @Test func pulseAlphaWrapsAtPeriodBoundary() {
        let atZero = powerUpPulseAlpha(at: 0, periodSeconds: 2.0)
        let atPeriod = powerUpPulseAlpha(at: 2.0, periodSeconds: 2.0)
        let atTwoPeriods = powerUpPulseAlpha(at: 4.0, periodSeconds: 2.0)
        #expect(abs(atZero - atPeriod) < 0.001)
        #expect(abs(atZero - atTwoPeriods) < 0.001)
    }

    @Test func pulseAlphaStaysWithinBoundsForAnyTime() {
        let minAlpha = 0.05
        let maxAlpha = 0.25
        var t = 0.0
        while t <= 10.0 {
            let alpha = powerUpPulseAlpha(at: t, periodSeconds: 2.0, minAlpha: minAlpha, maxAlpha: maxAlpha)
            #expect(alpha >= minAlpha - 0.001, "alpha \(alpha) below min at t=\(t)")
            #expect(alpha <= maxAlpha + 0.001, "alpha \(alpha) above max at t=\(t)")
            t += 0.073
        }
    }

    @Test func pulseAlphaRespectsCustomPeriod() {
        // period = 4.0 → quarter period = 1.0 → max
        let alpha = powerUpPulseAlpha(at: 1.0, periodSeconds: 4.0, minAlpha: 0.0, maxAlpha: 1.0)
        #expect(abs(alpha - 1.0) < 0.001)
    }

    @Test func pulseAlphaHandlesDegenerateRangeMinEqualsMax() {
        let alpha = powerUpPulseAlpha(at: 0.5, periodSeconds: 2.0, minAlpha: 0.1, maxAlpha: 0.1)
        #expect(abs(alpha - 0.1) < 0.001)
    }

    @Test func pulseAlphaHandlesVeryLargeTimeValues() {
        // 10 years of seconds — should still be within bounds
        let tenYearsSeconds: TimeInterval = 10 * 365 * 24 * 3600
        let alpha = powerUpPulseAlpha(at: tenYearsSeconds, periodSeconds: 2.0, minAlpha: 0.08, maxAlpha: 0.18)
        #expect(alpha >= 0.08 - 0.001)
        #expect(alpha <= 0.18 + 0.001)
    }

    @Test func pulseAlphaIsContinuousAcrossPeriodBoundary() {
        let justBeforePeriod = powerUpPulseAlpha(at: 1.999, periodSeconds: 2.0)
        let justAfterPeriod = powerUpPulseAlpha(at: 2.001, periodSeconds: 2.0)
        // Continuous function — no jump between 1.999s and 2.001s
        #expect(abs(justBeforePeriod - justAfterPeriod) < 0.01)
    }

    @Test func pulseAlphaDefaultBoundsAreSaneForOverlayVisibility() {
        // Default min=0.08, max=0.18 means the disc is always visible
        // but never fully opaque — validates the hardcoded defaults.
        let atMin = powerUpPulseAlpha(at: 1.5, periodSeconds: 2.0)
        let atMax = powerUpPulseAlpha(at: 0.5, periodSeconds: 2.0)
        #expect(atMin >= 0.05, "Min alpha too low — disc would disappear")
        #expect(atMax <= 0.25, "Max alpha too high — disc would obscure map")
    }

    @Test func pulseAlphaNegativeTimeStaysWithinBounds() {
        // truncatingRemainder on negative numbers returns negative, but the
        // function should still return a valid alpha. Verify it doesn't crash
        // or return values outside the sane range.
        let alpha = powerUpPulseAlpha(at: -3.5, periodSeconds: 2.0, minAlpha: 0.08, maxAlpha: 0.18)
        #expect(alpha >= 0.08 - 0.001)
        #expect(alpha <= 0.18 + 0.001)
    }
}
