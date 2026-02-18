//
//  ChickenMapFeatureTests.swift
//  ChickenRushTests
//

import ComposableArchitecture
import CoreLocation
import FirebaseFirestore
import Testing
@testable import PouleParty

@MainActor
struct ChickenMapFeatureTests {

    @Test func setGameTriggeredCalculatesRadius() async {
        let game = Game.mock
        let store = TestStore(initialState: ChickenMapFeature.State(game: game)) {
            ChickenMapFeature()
        }

        await store.send(.setGameTriggered) {
            let (lastUpdate, lastRadius) = game.findLastUpdate()
            $0.radius = lastRadius
            $0.nextRadiusUpdate = lastUpdate
            $0.mapCircle = MapCircle(
                center: game.initialCoordinates.toCLCoordinates,
                radius: CLLocationDistance(game.initialRadius)
            )
        }
    }

    @Test func beenFoundButtonShowsEndGameCode() async {
        let store = TestStore(initialState: ChickenMapFeature.State(game: .mock)) {
            ChickenMapFeature()
        }

        await store.send(.beenFoundButtonTapped) {
            $0.destination = .endGameCode(EndGameCodeFeature.State())
        }
    }

    @Test func cancelGameButtonShowsAlert() async {
        let store = TestStore(initialState: ChickenMapFeature.State(game: .mock)) {
            ChickenMapFeature()
        }

        await store.send(.cancelGameButtonTapped) {
            $0.destination = .alert(
                AlertState {
                    TextState("Cancel game")
                } actions: {
                    ButtonState(role: .cancel) {
                        TextState("Never mind")
                    }
                    ButtonState(role: .destructive, action: .cancelGame) {
                        TextState("Cancel game")
                    }
                } message: {
                    TextState("Are you sure you want to cancel and finish the game now ?")
                }
            )
        }
    }

    @Test func dismissEndGameCodeClearsDestination() async {
        var state = ChickenMapFeature.State(game: .mock)
        state.destination = .endGameCode(EndGameCodeFeature.State())

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        }

        await store.send(.dismissEndGameCode) {
            $0.destination = nil
        }
    }

    @Test func newLocationFetchedUpdatesMapCircle() async {
        let store = TestStore(initialState: ChickenMapFeature.State(game: .mock)) {
            ChickenMapFeature()
        }

        let location = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        await store.send(.newLocationFetched(location)) {
            $0.mapCircle = MapCircle(
                center: location,
                radius: CLLocationDistance($0.radius)
            )
        }
    }
}
