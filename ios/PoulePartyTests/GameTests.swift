//
//  GameTests.swift
//  PoulePartyTests
//

import CoreLocation
import FirebaseFirestore
import Testing
@testable import PouleParty

struct GameTests {

    @Test func gameCodeIsSixCharacters() {
        let game = Game(id: "abcdef-1234-5678")
        #expect(game.gameCode == "ABCDEF")
    }

    @Test func gameCodeIsUppercased() {
        let game = Game(id: "xyz123")
        #expect(game.gameCode == "XYZ123")
    }

    @Test func findLastUpdateReturnsInitialRadiusBeforeStart() {
        let game = Game(
            id: "test",
            startTimestamp: Timestamp(date: .now.addingTimeInterval(600)),
            endTimestamp: Timestamp(date: .now.addingTimeInterval(3600)),
            initialRadius: 1500,
            radiusDeclinePerUpdate: 100
        )
        let (_, radius) = game.findLastUpdate()
        #expect(radius == 1500)
    }

    @Test func findLastUpdateShrinksRadius() {
        let game = Game(
            id: "test",
            radiusIntervalUpdate: 5,
            startTimestamp: Timestamp(date: .now.addingTimeInterval(-600)),
            endTimestamp: Timestamp(date: .now.addingTimeInterval(3600)),
            initialRadius: 1500,
            radiusDeclinePerUpdate: 100
        )
        let (_, radius) = game.findLastUpdate()
        #expect(radius < 1500)
    }

    @Test func startDateAndEndDateConversion() {
        var game = Game(id: "test")
        let date = Date.now.addingTimeInterval(1000)
        game.startDate = date

        #expect(abs(game.startDate.timeIntervalSince(date)) < 1)
    }

    @Test func initialLocationConversion() {
        var game = Game(id: "test")
        let coord = CLLocationCoordinate2D(latitude: 48.8566, longitude: 2.3522)
        game.initialLocation = coord

        #expect(abs(game.initialLocation.latitude - 48.8566) < 0.0001)
        #expect(abs(game.initialLocation.longitude - 2.3522) < 0.0001)
    }

    // MARK: - GameMod tests

    @Test func gameModDefaultIsFollowTheChicken() {
        let game = Game(id: "test")
        #expect(game.gameMod == .followTheChicken)
    }

    @Test func gameModTitlesAreCorrect() {
        #expect(Game.GameMod.followTheChicken.title == "Follow the chicken 🐔")
        #expect(Game.GameMod.stayInTheZone.title == "Stay in the zone 📍")
        #expect(Game.GameMod.mutualTracking.title == "Mutual tracking 👀")
    }

    @Test func gameModAllCasesContainsThreeModes() {
        #expect(Game.GameMod.allCases.count == 3)
    }

    @Test func gameModCodableRoundTrip() throws {
        let encoder = JSONEncoder()
        let decoder = JSONDecoder()

        for mod in Game.GameMod.allCases {
            let data = try encoder.encode(mod)
            let decoded = try decoder.decode(Game.GameMod.self, from: data)
            #expect(decoded == mod)
        }
    }

    // MARK: - Found code tests

    @Test func foundCodeDefaultsToEmpty() {
        let game = Game(id: "test")
        #expect(game.foundCode == "")
    }

    @Test func generateFoundCodeIsFourDigits() {
        let code = Game.generateFoundCode()
        #expect(code.count == 4)
        #expect(Int(code) != nil)
    }

    @Test func generateFoundCodeIsInRange() {
        for _ in 0..<50 {
            let code = Game.generateFoundCode()
            let value = Int(code)!
            #expect(value >= 0 && value <= 9999)
        }
    }

    @Test func generateFoundCodePadsWithZeros() {
        // Run multiple times to increase chance of hitting a low number
        // At minimum, verify format is always 4 chars
        for _ in 0..<100 {
            let code = Game.generateFoundCode()
            #expect(code.count == 4)
        }
    }

    // MARK: - Winners tests

    @Test func winnersDefaultsToEmpty() {
        let game = Game(id: "test")
        #expect(game.winners.isEmpty)
    }

    @Test func winnerCodableRoundTrip() throws {
        let encoder = JSONEncoder()
        let decoder = JSONDecoder()

        let winner = Winner(
            hunterId: "hunter-1",
            hunterName: "Julien",
            timestamp: .now
        )
        let data = try encoder.encode(winner)
        let decoded = try decoder.decode(Winner.self, from: data)

        #expect(decoded.hunterId == winner.hunterId)
        #expect(decoded.hunterName == winner.hunterName)
    }

    @Test func mockGameHasFoundCode() {
        let mock = Game.mock
        #expect(mock.foundCode == "1234")
    }

    // MARK: - Normal mode calculation tests

    // MARK: - Normal mode calculation: basic

    @Test func calculateNormalModeSettingsAlwaysReturnsFixedInterval() {
        let durations: [Double] = [5, 30, 60, 90, 120, 150, 180]
        for duration in durations {
            let (interval, _) = calculateNormalModeSettings(initialRadius: 1500, gameDurationMinutes: duration)
            #expect(interval == normalModeFixedInterval, "interval should be \(normalModeFixedInterval) for duration \(duration)")
        }
    }

    @Test func calculateNormalModeSettingsFor2Hours() {
        let (interval, decline) = calculateNormalModeSettings(initialRadius: 1500, gameDurationMinutes: 120)
        #expect(interval == 5)
        let expectedDecline = (1500.0 - 100.0) / 24.0
        #expect(abs(decline - expectedDecline) < 0.01)
    }

    @Test func calculateNormalModeSettingsFor1Hour() {
        let (interval, decline) = calculateNormalModeSettings(initialRadius: 1500, gameDurationMinutes: 60)
        #expect(interval == 5)
        let expectedDecline = (1500.0 - 100.0) / 12.0
        #expect(abs(decline - expectedDecline) < 0.01)
    }

    // MARK: - Normal mode: all picker durations reach 100m

    @Test func allPickerDurationsReach100m() {
        let durations: [Double] = [60, 90, 120, 150, 180]
        for duration in durations {
            let radius = 1500.0
            let (interval, decline) = calculateNormalModeSettings(initialRadius: radius, gameDurationMinutes: duration)
            let numberOfShrinks = duration / interval
            let finalRadius = radius - (decline * numberOfShrinks)
            #expect(abs(finalRadius - normalModeMinimumRadius) < 0.01, "duration \(duration) should reach 100m, got \(finalRadius)")
        }
    }

    // MARK: - Normal mode: all radius slider values reach 100m

    @Test func allRadiusValuesReach100m() {
        // Slider: 500 to 2000 step 100
        let radii = stride(from: 500.0, through: 2000.0, by: 100.0)
        for radius in radii {
            let (interval, decline) = calculateNormalModeSettings(initialRadius: radius, gameDurationMinutes: 120)
            let numberOfShrinks = 120.0 / interval
            let finalRadius = radius - (decline * numberOfShrinks)
            #expect(abs(finalRadius - normalModeMinimumRadius) < 0.01, "radius \(radius) should reach 100m, got \(finalRadius)")
        }
    }

    // MARK: - Normal mode: all combinations of radius x duration

    @Test func allRadiusDurationCombinationsReach100m() {
        let durations: [Double] = [60, 90, 120, 150, 180]
        let radii = stride(from: 500.0, through: 2000.0, by: 100.0)
        for radius in radii {
            for duration in durations {
                let (interval, decline) = calculateNormalModeSettings(initialRadius: radius, gameDurationMinutes: duration)
                let numberOfShrinks = duration / interval
                let finalRadius = radius - (decline * numberOfShrinks)
                #expect(abs(finalRadius - normalModeMinimumRadius) < 0.01, "radius \(radius), duration \(duration) should reach 100m, got \(finalRadius)")
            }
        }
    }

    // MARK: - Normal mode: decline never produces negative final radius

    @Test func declineNeverProducesNegativeFinalRadius() {
        let durations: [Double] = [5, 30, 60, 90, 120, 150, 180]
        let radii = stride(from: 100.0, through: 2000.0, by: 100.0)
        for radius in radii {
            for duration in durations {
                let (interval, decline) = calculateNormalModeSettings(initialRadius: radius, gameDurationMinutes: duration)
                let numberOfShrinks = duration / interval
                let finalRadius = radius - (decline * numberOfShrinks)
                #expect(finalRadius >= 0, "radius \(radius), duration \(duration) produced negative final radius \(finalRadius)")
            }
        }
    }

    // MARK: - Edge cases: zero and negative duration

    @Test func zeroDurationReturnsZeroDecline() {
        let (interval, decline) = calculateNormalModeSettings(initialRadius: 1500, gameDurationMinutes: 0)
        #expect(interval == normalModeFixedInterval)
        #expect(decline == 0)
    }

    @Test func negativeDurationReturnsZeroDecline() {
        let (interval, decline) = calculateNormalModeSettings(initialRadius: 1500, gameDurationMinutes: -10)
        #expect(interval == normalModeFixedInterval)
        #expect(decline == 0)
    }

    // MARK: - Edge cases: minimal duration (single shrink)

    @Test func fiveMinuteDurationProducesSingleShrink() {
        let (interval, decline) = calculateNormalModeSettings(initialRadius: 1500, gameDurationMinutes: 5)
        #expect(interval == 5)
        // 5/5 = 1 shrink, decline = 1500 - 100 = 1400 in one step
        let finalRadius = 1500.0 - decline
        #expect(abs(finalRadius - normalModeMinimumRadius) < 0.01)
    }

    // MARK: - Edge cases: radius at or below minimum

    @Test func radiusExactlyAtMinimumGivesZeroDecline() {
        let (_, decline) = calculateNormalModeSettings(initialRadius: normalModeMinimumRadius, gameDurationMinutes: 120)
        #expect(decline == 0)
    }

    @Test func radiusBelowMinimumGivesZeroDecline() {
        let (_, decline) = calculateNormalModeSettings(initialRadius: 50, gameDurationMinutes: 120)
        #expect(decline == 0)
    }

    @Test func radiusJustAboveMinimum() {
        let (interval, decline) = calculateNormalModeSettings(initialRadius: 101, gameDurationMinutes: 120)
        #expect(interval == 5)
        let numberOfShrinks = 120.0 / interval
        let finalRadius = 101.0 - (decline * numberOfShrinks)
        #expect(abs(finalRadius - normalModeMinimumRadius) < 0.01)
    }

    // MARK: - Edge cases: large values

    @Test func veryLargeRadius() {
        let radius = 10000.0
        let (interval, decline) = calculateNormalModeSettings(initialRadius: radius, gameDurationMinutes: 120)
        let numberOfShrinks = 120.0 / interval
        let finalRadius = radius - (decline * numberOfShrinks)
        #expect(abs(finalRadius - normalModeMinimumRadius) < 0.01)
        #expect(decline > 0)
    }

    @Test func veryLongDurationProducesSmallDecline() {
        let (interval, decline) = calculateNormalModeSettings(initialRadius: 1500, gameDurationMinutes: 600)
        let numberOfShrinks = 600.0 / interval
        // 600/5 = 120 shrinks, (1500-100)/120 = 11.67
        #expect(decline < 12)
        #expect(decline > 11)
        let finalRadius = 1500.0 - (decline * numberOfShrinks)
        #expect(abs(finalRadius - normalModeMinimumRadius) < 0.01)
    }

    // MARK: - Integration: findLastUpdate with normal mode settings

    @Test func findLastUpdateWithNormalModeSettingsShrinksCorrectly() {
        let radius = 1500.0
        let duration = 120.0
        let (interval, decline) = calculateNormalModeSettings(initialRadius: radius, gameDurationMinutes: duration)

        // Game started 30.5 minutes ago (extra buffer to avoid boundary issues)
        let game = Game(
            id: "test",
            radiusIntervalUpdate: interval,
            startTimestamp: Timestamp(date: .now.addingTimeInterval(-1830)),
            endTimestamp: Timestamp(date: .now.addingTimeInterval(TimeInterval((duration - 30) * 60))),
            initialRadius: radius,
            radiusDeclinePerUpdate: decline
        )
        let (_, currentRadius) = game.findLastUpdate()
        // findLastUpdate truncates decline to Int on each step:
        // 6 shrinks * Int(decline) subtracted from Int(initialRadius)
        let expectedRadius = Int(radius) - (Int(decline) * 6)
        #expect(currentRadius == expectedRadius)
    }

    @Test func findLastUpdateWithNormalModeNeverGoesBelowZero() {
        let radius = 500.0
        let duration = 60.0
        let (interval, decline) = calculateNormalModeSettings(initialRadius: radius, gameDurationMinutes: duration)

        // Game started way longer ago than duration — all shrinks have happened
        let game = Game(
            id: "test",
            radiusIntervalUpdate: interval,
            startTimestamp: Timestamp(date: .now.addingTimeInterval(-7200)),
            endTimestamp: Timestamp(date: .now.addingTimeInterval(-3600)),
            initialRadius: radius,
            radiusDeclinePerUpdate: decline
        )
        let (_, currentRadius) = game.findLastUpdate()
        #expect(currentRadius >= 0)
    }

    // MARK: - Decline is always non-negative for any input

    @Test func declineIsNonNegativeForAllInputs() {
        let testRadii: [Double] = [0, 50, 100, 500, 1000, 1500, 2000]
        let testDurations: [Double] = [0, 5, 30, 60, 120, 180, 600]
        for radius in testRadii {
            for duration in testDurations {
                let (_, decline) = calculateNormalModeSettings(initialRadius: radius, gameDurationMinutes: duration)
                #expect(decline >= 0, "decline should be >= 0 for radius \(radius), duration \(duration)")
            }
        }
    }
}
