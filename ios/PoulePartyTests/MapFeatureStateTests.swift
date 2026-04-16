//
//  MapFeatureStateTests.swift
//  PoulePartyTests
//
//  Verifies the protocol conformance of ChickenMapFeature.State and
//  HunterMapFeature.State to MapFeatureState. The shared map composables
//  rely on every property listed in the protocol existing on both states.
//

import CoreLocation
import FirebaseCore
import FirebaseFirestore
import Testing
@testable import PouleParty

@MainActor
struct MapFeatureStateTests {

    // MARK: - Conformance smoke tests

    @Test func chickenStateConformsToMapFeatureState() {
        let state = ChickenMapFeature.State(game: .mock)
        let surface: any MapFeatureState = state
        #expect(surface.game.id == state.game.id)
        #expect(surface.radius == state.radius)
        #expect(surface.availablePowerUps.isEmpty)
    }

    @Test func hunterStateConformsToMapFeatureState() {
        let state = HunterMapFeature.State(game: .mock)
        let surface: any MapFeatureState = state
        #expect(surface.game.id == state.game.id)
        #expect(surface.radius == state.radius)
        #expect(surface.availablePowerUps.isEmpty)
    }

    // MARK: - Computed: hasGameStarted differs by role

    @Test func chickenHasGameStartedUsesGameStartDate() {
        var game = Game.mock
        game.timing.start = .init(date: .now.addingTimeInterval(-60))
        let state = ChickenMapFeature.State(game: game)
        let surface: any MapFeatureState = state
        // nowDate is initialized to .now so start is in the past → started
        #expect(surface.hasGameStarted)
    }

    @Test func hunterHasGameStartedUsesHunterStartDate() {
        var game = Game.mock
        game.timing.start = .init(date: .now.addingTimeInterval(-3600))
        // headStartMinutes pushes hunterStartDate forward
        game.timing.headStartMinutes = 90 // 90 min — hunter start is in the future
        let state = HunterMapFeature.State(game: game)
        let surface: any MapFeatureState = state
        #expect(!surface.hasGameStarted)
    }

    // MARK: - Power-ups passthrough on chicken state

    @Test func chickenPowerUpPassthroughsReadFromChildState() {
        var state = ChickenMapFeature.State(game: .mock)
        let powerUp = PowerUp(
            id: "test",
            type: .invisibility,
            location: .init(latitude: 50.0, longitude: 4.0),
            spawnedAt: .init(date: .now)
        )
        state.powerUps.collected = [powerUp]
        state.powerUps.available = [powerUp]
        state.powerUps.showInventory = true
        state.powerUps.notification = "Hello"
        state.powerUps.lastActivatedType = .invisibility

        let surface: any MapFeatureState = state
        #expect(surface.collectedPowerUps.count == 1)
        #expect(surface.availablePowerUps.count == 1)
        #expect(surface.showPowerUpInventory)
        #expect(surface.powerUpNotification == "Hello")
        #expect(surface.lastActivatedPowerUpType == .invisibility)
    }

    // MARK: - Edge cases

    @Test func chickenHasNotStartedWhenStartDateInFuture() {
        var game = Game.mock
        game.timing.start = .init(date: .now.addingTimeInterval(3600))
        let state = ChickenMapFeature.State(game: game)
        let surface: any MapFeatureState = state
        #expect(!surface.hasGameStarted)
    }

    @Test func hunterHasGameStartedRequiresHeadStartElapsed() {
        var game = Game.mock
        game.timing.start = .init(date: .now.addingTimeInterval(-3600))
        game.timing.headStartMinutes = 0
        let state = HunterMapFeature.State(game: game)
        let surface: any MapFeatureState = state
        #expect(surface.hasGameStarted)
    }

    @Test func defaultStateHasEmptyPowerUpCollections() {
        let state = ChickenMapFeature.State(game: .mock)
        let surface: any MapFeatureState = state
        #expect(surface.availablePowerUps.isEmpty)
        #expect(surface.collectedPowerUps.isEmpty)
        #expect(surface.powerUpNotification == nil)
        #expect(surface.lastActivatedPowerUpType == nil)
        #expect(!surface.showPowerUpInventory)
    }

    @Test func hunterAndChickenStatesShareTheSameRadiusDefault() {
        let chicken = ChickenMapFeature.State(game: .mock)
        let hunter = HunterMapFeature.State(game: .mock)
        #expect(chicken.radius == hunter.radius)
        #expect(chicken.radius == 1500)
    }

    @Test func mapCircleNilWhenNoZoneInitialized() {
        let state = ChickenMapFeature.State(game: .mock)
        let surface: any MapFeatureState = state
        #expect(surface.mapCircle == nil)
    }

    @Test func showGameInfoDefaultsFalse() {
        let chicken: any MapFeatureState = ChickenMapFeature.State(game: .mock)
        let hunter: any MapFeatureState = HunterMapFeature.State(game: .mock)
        #expect(!chicken.showGameInfo)
        #expect(!hunter.showGameInfo)
    }

    // MARK: - Hunter passthroughs (kept last)

    @Test func hunterPowerUpPassthroughsReadFromChildState() {
        var state = HunterMapFeature.State(game: .mock)
        state.powerUps.notification = "Radar!"
        state.powerUps.lastActivatedType = .radarPing

        let surface: any MapFeatureState = state
        #expect(surface.powerUpNotification == "Radar!")
        #expect(surface.lastActivatedPowerUpType == .radarPing)
    }
}
