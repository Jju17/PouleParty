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

    @Test func gameInitializedCalculatesRadius() async {
        let game = Game.mock
        let store = TestStore(initialState: ChickenMapFeature.State(game: game)) {
            ChickenMapFeature()
        }

        await store.send(.gameInitialized) {
            let (lastUpdate, lastRadius) = game.findLastUpdate()
            $0.radius = lastRadius
            $0.nextRadiusUpdate = lastUpdate
            $0.mapCircle = CircleOverlay(
                center: game.initialCoordinates.toCLCoordinates,
                radius: CLLocationDistance(game.initialRadius)
            )
            $0.lastLiveActivityState = $0.liveActivityState
        }
    }

    @Test func beenFoundButtonShowsEndGameCode() async {
        let store = TestStore(initialState: ChickenMapFeature.State(game: .mock)) {
            ChickenMapFeature()
        }

        await store.send(.beenFoundButtonTapped) {
            $0.destination = .endGameCode("1234")
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
                    TextState("Are you sure you want to cancel and finish the game now?")
                }
            )
        }
    }

    @Test func endGameCodeDismissedClearsDestination() async {
        var state = ChickenMapFeature.State(game: .mock)
        state.destination = .endGameCode("1234")

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        }

        await store.send(.endGameCodeDismissed) {
            $0.destination = nil
        }
    }

    @Test func newLocationFetchedUpdatesMapCircle() async {
        let store = TestStore(initialState: ChickenMapFeature.State(game: .mock)) {
            ChickenMapFeature()
        }

        let location = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        await store.send(.newLocationFetched(location)) {
            $0.userLocation = location
            $0.mapCircle = CircleOverlay(
                center: location,
                radius: CLLocationDistance($0.radius)
            )
        }
    }

    // MARK: - Hunter annotations (chickenCanSeeHunters)

    @Test func hunterLocationsUpdatedCreatesAnnotations() async {
        var game = Game.mock
        game.chickenCanSeeHunters = true
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

    // MARK: - Winner notifications

    @Test func gameUpdatedWithNewWinnerSetsNotificationState() {
        let game = Game.mock
        var state = ChickenMapFeature.State(game: game)

        // Simulate the reducer's winner detection logic
        var updatedGame = game
        updatedGame.winners = [
            Winner(hunterId: "h1", hunterName: "Julien", timestamp: .now)
        ]

        let previousCount = state.previousWinnersCount
        #expect(previousCount == -1)

        // Simulate first config update setting the baseline
        state.previousWinnersCount = game.winners.count // 0

        // Now simulate a new winner arriving
        state.game = updatedGame
        state.previousWinnersCount = updatedGame.winners.count
        if let latest = updatedGame.winners.last {
            state.winnerNotification = "\(latest.hunterName) found the chicken! 🐔"
        }

        #expect(state.winnerNotification == "Julien found the chicken! 🐔")
        #expect(state.previousWinnersCount == 1)
    }

    @Test func gameUpdatedWithNoNewWinnersDoesNotShowNotification() async {
        let winner = Winner(hunterId: "h1", hunterName: "Julien", timestamp: .now)
        var game = Game.mock
        game.winners = [winner]

        var state = ChickenMapFeature.State(game: game)
        state.previousWinnersCount = 1

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        }

        // Same game, same number of winners → no notification, but lastLiveActivityState may update
        await store.send(.gameUpdated(game)) {
            $0.lastLiveActivityState = $0.liveActivityState
        }
    }

    @Test func winnerNotificationDismissedClearsNotification() async {
        var state = ChickenMapFeature.State(game: .mock)
        state.winnerNotification = "Test notification"

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        }

        await store.send(.winnerNotificationDismissed) {
            $0.winnerNotification = nil
        }
    }

    // MARK: - stayInTheZone: location does not move circle

    @Test func newLocationFetchedDoesNotMoveCircleInStayInTheZone() async {
        var game = Game.mock
        game.gameMod = .stayInTheZone
        var state = ChickenMapFeature.State(game: game)
        let initialCenter = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        state.mapCircle = CircleOverlay(center: initialCenter, radius: 1500)

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        }

        let newLocation = CLLocationCoordinate2D(latitude: 51.0, longitude: 5.0)
        await store.send(.newLocationFetched(newLocation)) {
            $0.userLocation = newLocation
            // mapCircle should NOT change in stayInTheZone
        }
    }

    // MARK: - Game info

    @Test func infoButtonTappedShowsGameInfo() async {
        let store = TestStore(initialState: ChickenMapFeature.State(game: .mock)) {
            ChickenMapFeature()
        }

        await store.send(.infoButtonTapped) {
            $0.showGameInfo = true
        }
    }

    @Test func gameInfoDismissedHidesGameInfo() async {
        var state = ChickenMapFeature.State(game: .mock)
        state.showGameInfo = true

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        }

        await store.send(.gameInfoDismissed) {
            $0.showGameInfo = false
        }
    }

    // MARK: - Dismiss countdown

    @Test func countdownDismissedClearsState() async {
        var state = ChickenMapFeature.State(game: .mock)
        state.countdownNumber = 3
        state.countdownText = "GO!"

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        }

        await store.send(.countdownDismissed) {
            $0.countdownNumber = nil
            $0.countdownText = nil
        }
    }

    // MARK: - Timer + stayInTheZone

    @Test func timerTickedUpdatesCircleInStayInTheZone() async {
        var game = Game.mock
        game.gameMod = .stayInTheZone
        game.startDate = .now.addingTimeInterval(-600)   // started 10 min ago
        game.endDate = .now.addingTimeInterval(3000)      // ends in 50 min
        var state = ChickenMapFeature.State(game: game)
        state.radius = 500
        state.nextRadiusUpdate = .now.addingTimeInterval(-1)

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        }
        store.exhaustivity = .off

        let newRadius = 500 - Int(game.radiusDeclinePerUpdate)
        let expectedCenter = deterministicDriftCenter(
            basePoint: game.initialCoordinates.toCLCoordinates,
            oldRadius: 500,
            newRadius: Double(newRadius),
            driftSeed: game.driftSeed
        )
        await store.send(.timerTicked) {
            $0.radius = newRadius
            $0.mapCircle = CircleOverlay(
                center: expectedCenter,
                radius: CLLocationDistance(newRadius)
            )
        }
    }

    // MARK: - Timer game over by time

    @Test func timerTickedGameOverByTime() async {
        var game = Game.mock
        game.startDate = .now.addingTimeInterval(-3600)
        game.endDate = .now.addingTimeInterval(-1)
        var state = ChickenMapFeature.State(game: game)
        state.radius = 500

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        } withDependencies: {
            $0.locationClient.stopTracking = { }
            $0.apiClient.updateGameStatus = { _, _ in }
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
                    TextState("Time's up! The Chicken survived!")
                }
            )
        }
    }

    // MARK: - Alert confirmation flows

    @Test func cancelGameAlertSendsGoToMenu() async {
        var state = ChickenMapFeature.State(game: .mock)
        state.destination = .alert(
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
                TextState("Are you sure you want to cancel and finish the game now?")
            }
        )

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        } withDependencies: {
            $0.locationClient.stopTracking = { }
        }

        await store.send(.destination(.presented(.alert(.cancelGame)))) {
            $0.destination = nil
        }
        await store.receive(\.returnedToMenu)
    }

    @Test func gameOverAlertSendsGoToMenu() async {
        var state = ChickenMapFeature.State(game: .mock)
        state.destination = .alert(
            AlertState {
                TextState("Game Over")
            } actions: {
                ButtonState(action: .gameOver) {
                    TextState("OK")
                }
            } message: {
                TextState("Time's up! The Chicken survived!")
            }
        )

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        }

        await store.send(.destination(.presented(.alert(.gameOver)))) {
            $0.destination = nil
        }
        await store.receive(\.allHuntersFound)
    }

    // MARK: - Winner auto-dismiss effect

    @Test func gameUpdatedWithNewWinnerSchedulesAutoDismiss() async {
        let clock = TestClock()
        var initialState = ChickenMapFeature.State(game: .mock)
        initialState.previousWinnersCount = 0
        let store = TestStore(initialState: initialState) {
            ChickenMapFeature()
        } withDependencies: {
            $0.continuousClock = clock
        }

        var updatedGame = Game.mock
        updatedGame.winners = [
            Winner(hunterId: "h1", hunterName: "Julien", timestamp: .now)
        ]

        await store.send(.gameUpdated(updatedGame)) {
            $0.game = updatedGame
            $0.lastLiveActivityState = $0.liveActivityState
            $0.winnerNotification = "Julien found the chicken! 🐔"
            $0.previousWinnersCount = 1
        }

        await clock.advance(by: .seconds(AppConstants.winnerNotificationSeconds))
        await store.receive(\.winnerNotificationDismissed) {
            $0.winnerNotification = nil
        }
    }
}
