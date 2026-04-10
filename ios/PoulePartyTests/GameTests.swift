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
            timing: .init(
                start: Timestamp(date: .now.addingTimeInterval(600)),
                end: Timestamp(date: .now.addingTimeInterval(3600))
            ),
            zone: .init(
                radius: 1500,
                shrinkMetersPerUpdate: 100
            )
        )
        let (_, radius) = game.findLastUpdate()
        #expect(radius == 1500)
    }

    @Test func findLastUpdateShrinksRadius() {
        let game = Game(
            id: "test",
            timing: .init(
                start: Timestamp(date: .now.addingTimeInterval(-600)),
                end: Timestamp(date: .now.addingTimeInterval(3600))
            ),
            zone: .init(
                radius: 1500,
                shrinkIntervalMinutes: 5,
                shrinkMetersPerUpdate: 100
            )
        )
        let (_, radius) = game.findLastUpdate()
        #expect(radius < 1500)
    }

    @Test func startDateAndEndDateConversion() {
        var game = Game(id: "test")
        // Use a date with 0 seconds so the setter's second-stripping doesn't affect it
        var components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: .now.addingTimeInterval(1000))
        components.second = 0
        let date = Calendar.current.date(from: components) ?? .now
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

    // MARK: - GameMode tests

    @Test func gameModeDefaultIsStayInTheZone() {
        let game = Game(id: "test")
        #expect(game.gameMode == .stayInTheZone)
    }

    @Test func gameModeTitlesAreCorrect() {
        #expect(Game.GameMode.followTheChicken.title == "Follow the chicken 🐔")
        #expect(Game.GameMode.stayInTheZone.title == "Stay in the zone 📍")
    }

    @Test func gameModeAllCasesContainsTwoModes() {
        #expect(Game.GameMode.allCases.count == 2)
    }

    @Test func gameModeCodableRoundTrip() throws {
        let encoder = JSONEncoder()
        let decoder = JSONDecoder()

        for mode in Game.GameMode.allCases {
            let data = try encoder.encode(mode)
            let decoded = try decoder.decode(Game.GameMode.self, from: data)
            #expect(decoded == mode)
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
            #expect(interval == AppConstants.normalModeFixedInterval, "interval should be \(AppConstants.normalModeFixedInterval) for duration \(duration)")
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
            #expect(abs(finalRadius - AppConstants.normalModeMinimumRadius) < 0.01, "duration \(duration) should reach 100m, got \(finalRadius)")
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
            #expect(abs(finalRadius - AppConstants.normalModeMinimumRadius) < 0.01, "radius \(radius) should reach 100m, got \(finalRadius)")
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
                #expect(abs(finalRadius - AppConstants.normalModeMinimumRadius) < 0.01, "radius \(radius), duration \(duration) should reach 100m, got \(finalRadius)")
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
        #expect(interval == AppConstants.normalModeFixedInterval)
        #expect(decline == 0)
    }

    @Test func negativeDurationReturnsZeroDecline() {
        let (interval, decline) = calculateNormalModeSettings(initialRadius: 1500, gameDurationMinutes: -10)
        #expect(interval == AppConstants.normalModeFixedInterval)
        #expect(decline == 0)
    }

    // MARK: - Edge cases: minimal duration (single shrink)

    @Test func fiveMinuteDurationProducesSingleShrink() {
        let (interval, decline) = calculateNormalModeSettings(initialRadius: 1500, gameDurationMinutes: 5)
        #expect(interval == 5)
        // 5/5 = 1 shrink, decline = 1500 - 100 = 1400 in one step
        let finalRadius = 1500.0 - decline
        #expect(abs(finalRadius - AppConstants.normalModeMinimumRadius) < 0.01)
    }

    // MARK: - Edge cases: radius at or below minimum

    @Test func radiusExactlyAtMinimumGivesZeroDecline() {
        let (_, decline) = calculateNormalModeSettings(initialRadius: AppConstants.normalModeMinimumRadius, gameDurationMinutes: 120)
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
        #expect(abs(finalRadius - AppConstants.normalModeMinimumRadius) < 0.01)
    }

    // MARK: - Edge cases: large values

    @Test func veryLargeRadius() {
        let radius = 10000.0
        let (interval, decline) = calculateNormalModeSettings(initialRadius: radius, gameDurationMinutes: 120)
        let numberOfShrinks = 120.0 / interval
        let finalRadius = radius - (decline * numberOfShrinks)
        #expect(abs(finalRadius - AppConstants.normalModeMinimumRadius) < 0.01)
        #expect(decline > 0)
    }

    @Test func veryLongDurationProducesSmallDecline() {
        let (interval, decline) = calculateNormalModeSettings(initialRadius: 1500, gameDurationMinutes: 600)
        let numberOfShrinks = 600.0 / interval
        // 600/5 = 120 shrinks, (1500-100)/120 = 11.67
        #expect(decline < 12)
        #expect(decline > 11)
        let finalRadius = 1500.0 - (decline * numberOfShrinks)
        #expect(abs(finalRadius - AppConstants.normalModeMinimumRadius) < 0.01)
    }

    // MARK: - Integration: findLastUpdate with normal mode settings

    @Test func findLastUpdateWithNormalModeSettingsShrinksCorrectly() {
        let radius = 1500.0
        let duration = 120.0
        let (interval, decline) = calculateNormalModeSettings(initialRadius: radius, gameDurationMinutes: duration)

        // Game started 30.5 minutes ago (extra buffer to avoid boundary issues)
        let game = Game(
            id: "test",
            timing: .init(
                start: Timestamp(date: .now.addingTimeInterval(-1830)),
                end: Timestamp(date: .now.addingTimeInterval(TimeInterval((duration - 30) * 60)))
            ),
            zone: .init(
                radius: radius,
                shrinkIntervalMinutes: interval,
                shrinkMetersPerUpdate: decline
            )
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
            timing: .init(
                start: Timestamp(date: .now.addingTimeInterval(-7200)),
                end: Timestamp(date: .now.addingTimeInterval(-3600))
            ),
            zone: .init(
                radius: radius,
                shrinkIntervalMinutes: interval,
                shrinkMetersPerUpdate: decline
            )
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

    // MARK: - Pricing Model

    @Test func defaultGameHasFreePricingModel() {
        let game = Game(id: "test")
        #expect(game.pricing.model == .free)
    }

    @Test func isPaidIsFalseForFreeGames() {
        var game = Game(id: "test")
        game.pricing.model = .free
        #expect(game.isPaid == false)
    }

    @Test func isPaidIsTrueForFlatGames() {
        var game = Game(id: "test")
        game.pricing.model = .flat
        #expect(game.isPaid == true)
    }

    @Test func isPaidIsTrueForDepositGames() {
        var game = Game(id: "test")
        game.pricing.model = .deposit
        #expect(game.isPaid == true)
    }

    @Test func pricingFieldsHaveCorrectDefaults() {
        let game = Game(id: "test")
        #expect(game.pricing.pricePerPlayer == 0)
        #expect(game.pricing.deposit == 0)
        #expect(game.pricing.commission == 15.0)
    }

    @Test func flatGameStoresPricePerPlayer() {
        var game = Game(id: "test")
        game.pricing.model = .flat
        game.pricing.pricePerPlayer = 300
        game.maxPlayers = 15
        #expect(game.pricing.pricePerPlayer == 300)
        #expect(game.maxPlayers == 15)
    }

    @Test func depositGameStoresDepositAndPrice() {
        var game = Game(id: "test")
        game.pricing.model = .deposit
        game.pricing.deposit = 1000
        game.pricing.pricePerPlayer = 500
        #expect(game.pricing.deposit == 1000)
        #expect(game.pricing.pricePerPlayer == 500)
    }

    @Test func pricingModelRawValues() {
        #expect(Game.PricingModel.free.rawValue == "free")
        #expect(Game.PricingModel.flat.rawValue == "flat")
        #expect(Game.PricingModel.deposit.rawValue == "deposit")
    }

    // MARK: - Enum safe decoding (unknown values → safe defaults)

    @Test func gameStatusDecodesUnknownToWaiting() throws {
        let json = #"{"_0": "unknownStatus"}"#
        let data = json.data(using: .utf8)!
        // Decode via JSONDecoder to simulate unknown rawValue
        let decoded = try JSONDecoder().decode(Game.GameStatus.self, from: #""unknownStatus""#.data(using: .utf8)!)
        #expect(decoded == .waiting)
    }

    @Test func gameStatusDecodesKnownValues() throws {
        let waiting = try JSONDecoder().decode(Game.GameStatus.self, from: #""waiting""#.data(using: .utf8)!)
        let inProgress = try JSONDecoder().decode(Game.GameStatus.self, from: #""inProgress""#.data(using: .utf8)!)
        let done = try JSONDecoder().decode(Game.GameStatus.self, from: #""done""#.data(using: .utf8)!)
        #expect(waiting == .waiting)
        #expect(inProgress == .inProgress)
        #expect(done == .done)
    }

    @Test func gameModeDecodesUnknownToFollowTheChicken() throws {
        let decoded = try JSONDecoder().decode(Game.GameMode.self, from: #""futureMode""#.data(using: .utf8)!)
        #expect(decoded == .followTheChicken)
    }

    @Test func gameModeDecodesKnownValues() throws {
        let ftc = try JSONDecoder().decode(Game.GameMode.self, from: #""followTheChicken""#.data(using: .utf8)!)
        let siz = try JSONDecoder().decode(Game.GameMode.self, from: #""stayInTheZone""#.data(using: .utf8)!)
        #expect(ftc == .followTheChicken)
        #expect(siz == .stayInTheZone)
    }

    @Test func pricingModelDecodesUnknownToFree() throws {
        let decoded = try JSONDecoder().decode(Game.PricingModel.self, from: #""subscription""#.data(using: .utf8)!)
        #expect(decoded == .free)
    }

    // MARK: - Profanity filter

    @Test func profanityFilterDetectsBlockedWords() {
        #expect(ProfanityFilter.containsProfanity("hello fuck you") == true)
        #expect(ProfanityFilter.containsProfanity("merde") == true)
        #expect(ProfanityFilter.containsProfanity("connard") == true)
    }

    @Test func profanityFilterAllowsCleanText() {
        #expect(ProfanityFilter.containsProfanity("hello world") == false)
        #expect(ProfanityFilter.containsProfanity("PouleParty") == false)
        #expect(ProfanityFilter.containsProfanity("Julien") == false)
    }

    @Test func profanityFilterDetectsLeetspeak() {
        #expect(ProfanityFilter.containsProfanity("fvck") == false) // 'v' not in substitutions
        #expect(ProfanityFilter.containsProfanity("sh1t") == true)  // 1→i
        #expect(ProfanityFilter.containsProfanity("@$$hole") == true) // @→a, $→s
        #expect(ProfanityFilter.containsProfanity("m3rde") == true) // 3→e
    }

    @Test func profanityFilterHandlesDiacritics() {
        #expect(ProfanityFilter.containsProfanity("enculé") == true)
        #expect(ProfanityFilter.containsProfanity("pédé") == true)
        #expect(ProfanityFilter.containsProfanity("bâtard") == true)
    }

    @Test func profanityFilterIsCaseInsensitive() {
        #expect(ProfanityFilter.containsProfanity("FUCK") == true)
        #expect(ProfanityFilter.containsProfanity("Merde") == true)
        #expect(ProfanityFilter.containsProfanity("CONNARD") == true)
    }

    // MARK: - Found code generation

    @Test func foundCodeIsAlwaysFourDigits() {
        for _ in 0..<100 {
            let code = Game.generateFoundCode()
            #expect(code.count == 4, "Found code must be 4 digits: \(code)")
            #expect(Int(code) != nil, "Found code must be numeric: \(code)")
        }
    }

    @Test func foundCodeLeadingZerosPreserved() {
        // Generate many codes, check that format is always 4 digits (0000-9999)
        var seenLeadingZero = false
        for _ in 0..<1000 {
            let code = Game.generateFoundCode()
            if code.hasPrefix("0") { seenLeadingZero = true }
            #expect(code.count == 4)
        }
        // With 1000 attempts, probability of never seeing a leading zero is ~(0.9)^1000 ≈ 0
        #expect(seenLeadingZero, "Should see at least one code with leading zero in 1000 attempts")
    }

    // MARK: - Game code derivation

    @Test func gameCodeFromShortId() {
        let game = Game(id: "abc")
        #expect(game.gameCode == "ABC")
    }

    @Test func gameCodeFromEmptyId() {
        let game = Game(id: "")
        #expect(game.gameCode == "")
    }

    @Test func gameCodeFromExactlySixChars() {
        let game = Game(id: "abcdef")
        #expect(game.gameCode == "ABCDEF")
    }

    // MARK: - Game defaults

    @Test func defaultTimingHeadStartIsZero() {
        let timing = Game.Timing()
        #expect(timing.headStartMinutes == 0)
    }

    @Test func defaultZoneValues() {
        let zone = Game.Zone()
        #expect(zone.radius == 1500)
        #expect(zone.shrinkIntervalMinutes == 5)
        #expect(zone.shrinkMetersPerUpdate == 100)
        #expect(zone.driftSeed == 0)
        #expect(zone.finalCenter == nil)
    }

    @Test func defaultGameModeIsStayInTheZone() {
        let game = Game(id: "test")
        #expect(game.gameMode == .stayInTheZone)
    }

    @Test func defaultStatusIsWaiting() {
        let game = Game(id: "test")
        #expect(game.status == .waiting)
    }

    @Test func defaultPricingIsFree() {
        let game = Game(id: "test")
        #expect(game.pricing.model == .free)
        #expect(game.isPaid == false)
    }

    @Test func defaultRegistrationNotRequired() {
        let game = Game(id: "test")
        #expect(game.registration.required == false)
    }

    @Test func defaultPowerUpsDisabled() {
        let game = Game(id: "test")
        #expect(game.powerUps.enabled == false)
    }

    @Test func defaultMaxPlayers() {
        let game = Game(id: "test")
        #expect(game.maxPlayers == 10)
    }

    // MARK: - hunterStartDate calculation

    @Test func hunterStartDateEqualsStartDateWhenNoHeadStart() {
        var game = Game(id: "test")
        game.timing.headStartMinutes = 0
        #expect(game.hunterStartDate == game.startDate)
    }

    @Test func hunterStartDateAddsHeadStart() {
        var game = Game(id: "test")
        game.timing.headStartMinutes = 5
        let expected = game.startDate.addingTimeInterval(5 * 60)
        #expect(abs(game.hunterStartDate.timeIntervalSince(expected)) < 0.001)
    }

    @Test func hunterStartDateWithMaxHeadStart() {
        var game = Game(id: "test")
        game.timing.headStartMinutes = 15
        let expected = game.startDate.addingTimeInterval(15 * 60)
        #expect(abs(game.hunterStartDate.timeIntervalSince(expected)) < 0.001)
    }

    // MARK: - Registration deadline

    @Test func registrationDeadlineNilWhenNoMinutes() {
        var game = Game(id: "test")
        game.registration.closesMinutesBefore = nil
        #expect(game.registrationDeadline == nil)
    }

    @Test func registrationDeadlineCorrect() {
        var game = Game(id: "test")
        game.registration.closesMinutesBefore = 15
        let deadline = game.registrationDeadline!
        let expected = game.startDate.addingTimeInterval(-15 * 60)
        #expect(abs(deadline.timeIntervalSince(expected)) < 0.001)
    }

    @Test func isRegistrationClosedFalseWhenNotRequired() {
        var game = Game(id: "test")
        game.registration.required = false
        #expect(game.isRegistrationClosed == false)
    }

    @Test func isRegistrationClosedFalseWhenNoDeadline() {
        var game = Game(id: "test")
        game.registration.required = true
        game.registration.closesMinutesBefore = nil
        #expect(game.isRegistrationClosed == false)
    }

    // MARK: - isPaid

    @Test func isPaidTrueForFlat() {
        var game = Game(id: "test")
        game.pricing.model = .flat
        #expect(game.isPaid == true)
    }

    @Test func isPaidTrueForDeposit() {
        var game = Game(id: "test")
        game.pricing.model = .deposit
        #expect(game.isPaid == true)
    }

    // MARK: - findLastUpdate edge cases

    @Test func findLastUpdateWithZeroShrinkInterval() {
        let game = Game(
            id: "test",
            timing: .init(
                start: Timestamp(date: .now.addingTimeInterval(-600)),
                end: Timestamp(date: .now.addingTimeInterval(3600))
            ),
            zone: .init(radius: 1500, shrinkIntervalMinutes: 0, shrinkMetersPerUpdate: 100)
        )
        let (_, radius) = game.findLastUpdate()
        #expect(radius == 1500, "Zero interval should skip shrinking entirely")
    }

    @Test func findLastUpdateWithNegativeShrinkInterval() {
        let game = Game(
            id: "test",
            timing: .init(
                start: Timestamp(date: .now.addingTimeInterval(-600)),
                end: Timestamp(date: .now.addingTimeInterval(3600))
            ),
            zone: .init(radius: 1500, shrinkIntervalMinutes: -5, shrinkMetersPerUpdate: 100)
        )
        let (_, radius) = game.findLastUpdate()
        #expect(radius == 1500, "Negative interval should skip shrinking")
    }

    @Test func findLastUpdateNeverGoesNegative() {
        // Game running for a very long time with aggressive shrinking
        let game = Game(
            id: "test",
            timing: .init(
                start: Timestamp(date: .now.addingTimeInterval(-36000)), // 10 hours ago
                end: Timestamp(date: .now.addingTimeInterval(3600))
            ),
            zone: .init(radius: 500, shrinkIntervalMinutes: 1, shrinkMetersPerUpdate: 100)
        )
        let (_, radius) = game.findLastUpdate()
        #expect(radius >= 0, "Radius must never go below 0")
        #expect(radius == 0, "After 500m/1min shrink for 10 hours, radius should be clamped to 0")
    }

    @Test func findLastUpdateWithVeryLargeRadius() {
        let game = Game(
            id: "test",
            timing: .init(
                start: Timestamp(date: .now.addingTimeInterval(-300)), // 5 min ago
                end: Timestamp(date: .now.addingTimeInterval(3600))
            ),
            zone: .init(radius: 50000, shrinkIntervalMinutes: 5, shrinkMetersPerUpdate: 100)
        )
        let (_, radius) = game.findLastUpdate()
        // 5 min elapsed, 1 shrink should have happened
        #expect(radius == 49900)
    }

    @Test func findLastUpdateWithHeadStart() {
        // Game started 10 min ago with 8 min head start
        // Effective elapsed = 10 - 8 = 2 min (less than 5 min interval)
        // So no shrink should have happened yet
        let game = Game(
            id: "test",
            timing: .init(
                start: Timestamp(date: .now.addingTimeInterval(-600)),
                end: Timestamp(date: .now.addingTimeInterval(3600)),
                headStartMinutes: 8
            ),
            zone: .init(radius: 1500, shrinkIntervalMinutes: 5, shrinkMetersPerUpdate: 100)
        )
        let (_, radius) = game.findLastUpdate()
        #expect(radius == 1500, "No shrink should happen during head start period")
    }

    // MARK: - calculateNormalModeSettings edge cases not yet covered

    @Test func normalModeWithHeadStartSubtracted() {
        // 90 min game with 15 min head start → effective = 75 min
        let (interval, decline) = calculateNormalModeSettings(initialRadius: 1500, gameDurationMinutes: 75)
        #expect(interval == 5)
        let expectedShrinks = 75.0 / 5.0 // 15 shrinks
        let expectedDecline = (1500.0 - 100.0) / expectedShrinks
        #expect(abs(decline - expectedDecline) < 0.01)
    }

    @Test func normalModeVeryShortDurationProducesLargeDecline() {
        // 1 min / 5 min interval = 0.2 shrinks (fractional but > 0, passes guard)
        let (interval, decline) = calculateNormalModeSettings(initialRadius: 1500, gameDurationMinutes: 1)
        #expect(interval == 5)
        // decline = (1500-100) / 0.2 = 7000 — very large but valid
        #expect(decline > 0)
    }

    @Test func normalModeVerySmallRadius() {
        let (_, decline) = calculateNormalModeSettings(initialRadius: 101, gameDurationMinutes: 90)
        let expected = (101.0 - 100.0) / (90.0 / 5.0) // 1/18 ≈ 0.0556
        #expect(abs(decline - expected) < 0.01)
    }

    @Test func normalModeAllDurationOptions() {
        let durations: [Double] = [60, 90, 120, 150, 180]
        for duration in durations {
            let (interval, decline) = calculateNormalModeSettings(initialRadius: 1500, gameDurationMinutes: duration)
            #expect(interval == 5, "Interval must always be 5 min for duration \(duration)")
            #expect(decline > 0, "Decline must be positive for duration \(duration)")

            // Verify zone reaches ~100m after all shrinks
            let numberOfShrinks = Int(duration / 5.0)
            let finalRadius = 1500.0 - Double(numberOfShrinks) * decline
            #expect(abs(finalRadius - 100.0) < 0.01, "Zone must reach 100m for duration \(duration), got \(finalRadius)")
        }
    }

    // MARK: - Power-up active effects

    @Test func isChickenInvisibleFalseByDefault() {
        let game = Game(id: "test")
        #expect(game.isChickenInvisible == false)
    }

    @Test func isZoneFrozenFalseByDefault() {
        let game = Game(id: "test")
        #expect(game.isZoneFrozen == false)
    }

    @Test func isChickenInvisibleTrueWhenFuture() {
        var game = Game(id: "test")
        game.powerUps.activeEffects.invisibility = Timestamp(date: .now.addingTimeInterval(30))
        #expect(game.isChickenInvisible == true)
    }

    @Test func isChickenInvisibleFalseWhenExpired() {
        var game = Game(id: "test")
        game.powerUps.activeEffects.invisibility = Timestamp(date: .now.addingTimeInterval(-5))
        #expect(game.isChickenInvisible == false)
    }

    @Test func isZoneFrozenTrueWhenFuture() {
        var game = Game(id: "test")
        game.powerUps.activeEffects.zoneFreeze = Timestamp(date: .now.addingTimeInterval(120))
        #expect(game.isZoneFrozen == true)
    }

    @Test func isDecoyActiveTrueWhenFuture() {
        var game = Game(id: "test")
        game.powerUps.activeEffects.decoy = Timestamp(date: .now.addingTimeInterval(20))
        #expect(game.isDecoyActive == true)
    }

    @Test func isDecoyActiveFalseWhenExpired() {
        var game = Game(id: "test")
        game.powerUps.activeEffects.decoy = Timestamp(date: .now.addingTimeInterval(-5))
        #expect(game.isDecoyActive == false)
    }

    @Test func isJammerActiveTrueWhenFuture() {
        var game = Game(id: "test")
        game.powerUps.activeEffects.jammer = Timestamp(date: .now.addingTimeInterval(30))
        #expect(game.isJammerActive == true)
    }

    @Test func isRadarPingActiveTrueWhenFuture() {
        var game = Game(id: "test")
        game.powerUps.activeEffects.radarPing = Timestamp(date: .now.addingTimeInterval(30))
        #expect(game.isRadarPingActive == true)
    }

    @Test func allEffectsExpiredReturnFalse() {
        var game = Game(id: "test")
        let past = Timestamp(date: .now.addingTimeInterval(-10))
        game.powerUps.activeEffects = .init(
            invisibility: past, zoneFreeze: past, radarPing: past, decoy: past, jammer: past
        )
        #expect(game.isChickenInvisible == false)
        #expect(game.isZoneFrozen == false)
        #expect(game.isRadarPingActive == false)
        #expect(game.isDecoyActive == false)
        #expect(game.isJammerActive == false)
    }

    // MARK: - startDate seconds stripping

    @Test func startDateStripsSeconds() {
        var game = Game(id: "test")
        // Set a date with non-zero seconds
        let dateWithSeconds = Date(timeIntervalSince1970: 1700000045) // has :45 seconds
        game.startDate = dateWithSeconds
        let seconds = Calendar.current.component(.second, from: game.startDate)
        #expect(seconds == 0, "startDate should have seconds stripped to :00")
    }
}
