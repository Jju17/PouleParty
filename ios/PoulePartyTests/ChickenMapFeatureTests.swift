//
//  ChickenMapFeatureTests.swift
//  PoulePartyTests
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
            $0.mapCircle = CircleOverlay(
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
            $0.mapCircle = CircleOverlay(
                center: location,
                radius: CLLocationDistance($0.radius)
            )
        }
    }

    // MARK: - Hunter annotations (mutualTracking)

    @Test func hunterLocationsUpdatedCreatesAnnotations() async {
        var game = Game.mock
        game.gameMod = .mutualTracking
        let store = TestStore(initialState: ChickenMapFeature.State(game: game)) {
            ChickenMapFeature()
        }

        let hunters = [
            HunterLocation(
                hunterId: "hunter-b",
                location: GeoPoint(latitude: 50.0, longitude: 4.0),
                timestamp: Timestamp(date: .now)
            ),
            HunterLocation(
                hunterId: "hunter-a",
                location: GeoPoint(latitude: 51.0, longitude: 5.0),
                timestamp: Timestamp(date: .now)
            ),
        ]

        await store.send(.hunterLocationsUpdated(hunters)) {
            $0.hunterAnnotations = [
                HunterAnnotation(
                    id: "hunter-a",
                    coordinate: CLLocationCoordinate2D(latitude: 51.0, longitude: 5.0),
                    displayName: "Hunter 1"
                ),
                HunterAnnotation(
                    id: "hunter-b",
                    coordinate: CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0),
                    displayName: "Hunter 2"
                ),
            ]
        }
    }

    @Test func hunterLocationsUpdatedWithEmptyArrayClearsAnnotations() async {
        var state = ChickenMapFeature.State(game: .mock)
        state.hunterAnnotations = [
            HunterAnnotation(id: "test", coordinate: CLLocationCoordinate2D(latitude: 0, longitude: 0), displayName: "Hunter 1")
        ]

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        }

        await store.send(.hunterLocationsUpdated([])) {
            $0.hunterAnnotations = []
        }
    }

    // MARK: - Timer + stayInTheZone

    @Test func timerTickedUpdatesCircleInStayInTheZone() async {
        var game = Game.mock
        game.gameMod = .stayInTheZone
        var state = ChickenMapFeature.State(game: game)
        state.radius = 500
        state.nextRadiusUpdate = .now.addingTimeInterval(-1)

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
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
}
