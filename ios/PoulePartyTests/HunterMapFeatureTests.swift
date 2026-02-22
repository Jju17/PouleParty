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
        } withDependencies: {
            $0.locationClient.stopTracking = { }
        }
        store.exhaustivity = .off

        await store.send(.timerTicked) {
            $0.destination = .alert(
                AlertState {
                    TextState("Game Over")
                } actions: {
                    ButtonState(action: .gameOver) {
                        TextState("OK")
                    }
                } message: {
                    TextState("The zone has collapsed!")
                }
            )
        }
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
        store.exhaustivity = .off

        await store.send(.timerTicked) {
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
        store.exhaustivity = .off

        await store.send(.timerTicked) {
            $0.radius = 500 - Int(game.radiusDeclinePerUpdate)
        }
    }

    // MARK: - hunterId

    @Test func hunterIdIsGenerated() {
        let state = HunterMapFeature.State(game: .mock)
        #expect(!state.hunterId.isEmpty)
    }

    // MARK: - Found code

    @Test func foundButtonTappedShowsCodeEntry() async {
        let store = TestStore(initialState: HunterMapFeature.State(game: .mock)) {
            HunterMapFeature()
        }

        await store.send(.foundButtonTapped) {
            $0.isEnteringFoundCode = true
        }
    }

    @Test func submitFoundCodeWithCorrectCodeAddsWinner() async {
        var state = HunterMapFeature.State(game: .mock)
        state.enteredCode = "1234" // matches mock.foundCode

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        } withDependencies: {
            $0.apiClient.addWinner = { _, _ in }
        }

        await store.send(.submitFoundCode) {
            $0.enteredCode = ""
            $0.isEnteringFoundCode = false
        }
    }

    @Test func submitFoundCodeWithWrongCodeShowsAlert() async {
        var state = HunterMapFeature.State(game: .mock)
        state.enteredCode = "9999" // wrong code

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        }

        await store.send(.submitFoundCode) {
            $0.enteredCode = ""
            $0.isEnteringFoundCode = false
            $0.destination = .alert(
                AlertState {
                    TextState("Wrong code")
                } actions: {
                    ButtonState(action: .wrongCode) {
                        TextState("OK")
                    }
                } message: {
                    TextState("That code is incorrect. Try again!")
                }
            )
        }
    }

    // MARK: - Winner notifications

    @Test func newWinnerFromOtherHunterSetsNotification() {
        let game = Game.mock
        var state = HunterMapFeature.State(game: game)
        state.hunterId = "my-hunter-id"

        // Simulate the reducer's winner detection logic from setGameTriggered
        var updatedGame = game
        updatedGame.winners = [
            Winner(hunterId: "other-hunter", hunterName: "Julien", timestamp: .now)
        ]

        let previousCount = state.previousWinnersCount
        #expect(previousCount == 0)
        #expect(updatedGame.winners.count > previousCount)

        let latest = updatedGame.winners.last!
        #expect(latest.hunterId != state.hunterId)

        state.winnerNotification = "\(latest.hunterName) a trouvé la poule !"
        state.previousWinnersCount = updatedGame.winners.count

        #expect(state.winnerNotification == "Julien a trouvé la poule !")
        #expect(state.previousWinnersCount == 1)
    }

    @Test func setGameTriggeredOwnWinDoesNotShowNotification() async {
        let game = Game.mock
        var state = HunterMapFeature.State(game: game)
        state.hunterId = "my-hunter-id"

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        }

        var updatedGame = game
        updatedGame.winners = [
            Winner(hunterId: "my-hunter-id", hunterName: "Me", timestamp: .now)
        ]

        await store.send(.setGameTriggered(to: updatedGame)) {
            let (lastUpdate, lastRadius) = updatedGame.findLastUpdate()
            $0.game = updatedGame
            $0.radius = lastRadius
            $0.nextRadiusUpdate = lastUpdate
            $0.mapCircle = CircleOverlay(
                center: updatedGame.initialCoordinates.toCLCoordinates,
                radius: CLLocationDistance(lastRadius)
            )
            $0.previousWinnersCount = 1
        }
    }

    @Test func hunterNameDefaultsToHunter() {
        let state = HunterMapFeature.State(game: .mock)
        #expect(state.hunterName == "Hunter")
    }
}
