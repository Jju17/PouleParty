//
//  HunterMapFeatureTests.swift
//  ChickenRushTests
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
            $0.mapCircle = MapCircle(
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
            $0.mapCircle = MapCircle(
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
}
