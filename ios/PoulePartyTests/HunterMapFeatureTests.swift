//
//  HunterMapFeatureTests.swift
//  PoulePartyTests
//

import ComposableArchitecture
import CoreLocation
import FirebaseFirestore
import Testing
@testable import PouleParty

@MainActor
struct HunterMapFeatureTests {

    @Test func newLocationFetchedUpdatesMapCircle() async {
        let store = TestStore(initialState: HunterMapFeature.State(game: .mock)) {
            HunterMapFeature()
        }

        let location = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        await store.send(.newLocationFetched(location)) {
            $0.mapCircle = CircleOverlay(
                center: location,
                radius: CLLocationDistance($0.radius)
            )
        }
    }

    @Test func setGameTriggeredUpdatesState() async {
        let store = TestStore(initialState: HunterMapFeature.State(game: .mock)) {
            HunterMapFeature()
        }

        let newGame = Game(
            id: "updated",
            radiusIntervalUpdate: 10,
            startTimestamp: Timestamp(date: .now.addingTimeInterval(300)),
            endTimestamp: Timestamp(date: .now.addingTimeInterval(3900)),
            initialRadius: 2000,
            radiusDeclinePerUpdate: 200
        )

        await store.send(.setGameTriggered(to: newGame)) {
            let (lastUpdate, lastRadius) = newGame.findLastUpdate()
            $0.game = newGame
            $0.radius = lastRadius
            $0.nextRadiusUpdate = lastUpdate
            $0.mapCircle = CircleOverlay(
                center: newGame.initialCoordinates.toCLCoordinates,
                radius: CLLocationDistance(newGame.initialRadius)
            )
        }
    }

    @Test func timerTickedDoesNotShrinkBelowZero() async {
        var state = HunterMapFeature.State(game: .mock)
        state.radius = 50
        state.nextRadiusUpdate = .now.addingTimeInterval(-1)

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        }

        await store.send(.timerTicked)
    }

    // MARK: - stayInTheZone mode

    @Test func timerTickedUpdatesCircleInStayInTheZone() async {
        var game = Game.mock
        game.gameMod = .stayInTheZone
        var state = HunterMapFeature.State(game: game)
        state.radius = 500
        state.nextRadiusUpdate = .now.addingTimeInterval(-1)

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        }

        await store.send(.timerTicked) {
            $0.nextRadiusUpdate?.addTimeInterval(TimeInterval(game.radiusIntervalUpdate * 60))
            $0.radius = 500 - Int(game.radiusDeclinePerUpdate)
            $0.mapCircle = CircleOverlay(
                center: game.initialCoordinates.toCLCoordinates,
                radius: CLLocationDistance(500 - Int(game.radiusDeclinePerUpdate))
            )
        }
    }

    @Test func timerTickedDoesNotUpdateCircleInFollowTheChicken() async {
        var game = Game.mock
        game.gameMod = .followTheChicken
        var state = HunterMapFeature.State(game: game)
        state.radius = 500
        state.nextRadiusUpdate = .now.addingTimeInterval(-1)

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        }

        await store.send(.timerTicked) {
            $0.nextRadiusUpdate?.addTimeInterval(TimeInterval(game.radiusIntervalUpdate * 60))
            $0.radius = 500 - Int(game.radiusDeclinePerUpdate)
        }
    }

    // MARK: - hunterId

    @Test func hunterIdIsGenerated() {
        let state = HunterMapFeature.State(game: .mock)
        #expect(!state.hunterId.isEmpty)
    }
}
