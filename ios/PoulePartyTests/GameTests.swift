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
        #expect(Game.GameMod.stayInTheZone.title == "Stay in tha zone 📍")
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
}
