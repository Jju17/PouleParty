//
//  HunterMapFeatureTests.swift
//  PoulePartyTests
//

import ComposableArchitecture
import CoreLocation
import FirebaseFirestore
import Testing
@testable import PouleParty

/// A mock game that has already started (start dates in the past).
private var startedGameMock: Game {
    var game = Game.mock
    game.startDate = .now.addingTimeInterval(-600)   // started 10 min ago
    game.endDate = .now.addingTimeInterval(3000)      // ends in 50 min
    return game
}

@MainActor
struct HunterMapFeatureTests {

    @Test func newLocationFetchedUpdatesMapCircle() async {
        let store = TestStore(initialState: HunterMapFeature.State(game: .mock)) {
            HunterMapFeature()
        }

        let location = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        await store.send(.internal(.newLocationFetched(location))) {
            $0.mapCircle = CircleOverlay(
                center: location,
                radius: CLLocationDistance($0.radius)
            )
        }
    }

    @Test func gameConfigUpdatedUpdatesState() async {
        let store = TestStore(initialState: HunterMapFeature.State(game: .mock)) {
            HunterMapFeature()
        }

        let newGame = Game(
            id: "updated",
            timing: .init(
                start: Timestamp(date: .now.addingTimeInterval(300)),
                end: Timestamp(date: .now.addingTimeInterval(3900))
            ),
            zone: .init(
                radius: 2000,
                shrinkIntervalMinutes: 10,
                shrinkMetersPerUpdate: 200
            )
        )

        await store.send(.internal(.gameConfigUpdated( newGame))) {
            let (lastUpdate, lastRadius) = newGame.findLastUpdate()
            $0.game = newGame
            $0.radius = lastRadius
            $0.nextRadiusUpdate = lastUpdate
            $0.mapCircle = CircleOverlay(
                center: newGame.zone.center.toCLCoordinates,
                radius: CLLocationDistance(newGame.zone.radius)
            )
            $0.previousWinnersCount = newGame.winners.count
            $0.lastLiveActivityState = $0.liveActivityState
        }
    }

    @Test func timerTickedDoesNotShrinkBelowZero() async {
        var game = startedGameMock
        game.zone.shrinkMetersPerUpdate = 100
        var state = HunterMapFeature.State(game: game)
        state.radius = 50
        state.nextRadiusUpdate = .now.addingTimeInterval(-1)

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        }
        store.exhaustivity = .off

        await store.send(.internal(.timerTicked)) {
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
        var game = startedGameMock
        game.gameMode = .stayInTheZone
        var state = HunterMapFeature.State(game: game)
        state.radius = 500
        state.nextRadiusUpdate = .now.addingTimeInterval(-1)

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        }
        store.exhaustivity = .off

        let newRadius = 500 - Int(game.zone.shrinkMetersPerUpdate)
        let expectedCenter = deterministicDriftCenter(
            basePoint: game.zone.center.toCLCoordinates,
            oldRadius: 500,
            newRadius: Double(newRadius),
            driftSeed: game.zone.driftSeed
        )
        await store.send(.internal(.timerTicked)) {
            $0.radius = newRadius
            $0.mapCircle = CircleOverlay(
                center: expectedCenter,
                radius: CLLocationDistance(newRadius)
            )
        }
    }

    @Test func timerTickedDoesNotUpdateCircleInFollowTheChicken() async {
        var game = startedGameMock
        game.gameMode = .followTheChicken
        var state = HunterMapFeature.State(game: game)
        state.radius = 500
        state.nextRadiusUpdate = .now.addingTimeInterval(-1)

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        }
        store.exhaustivity = .off

        await store.send(.internal(.timerTicked)) {
            $0.radius = 500 - Int(game.zone.shrinkMetersPerUpdate)
        }
    }

    // MARK: - hunterId

    @Test func hunterIdDefaultsToEmptyBeforeAuth() {
        let state = HunterMapFeature.State(game: .mock)
        #expect(state.hunterId.isEmpty)
    }

    // MARK: - Found code

    @Test func foundButtonTappedShowsCodeEntry() async {
        let store = TestStore(initialState: HunterMapFeature.State(game: .mock)) {
            HunterMapFeature()
        }

        await store.send(.view(.foundButtonTapped)) {
            $0.isEnteringFoundCode = true
        }
    }

    @Test func submitCodeButtonTappedWithCorrectCodeAddsWinner() async {
        var state = HunterMapFeature.State(game: .mock)
        state.enteredCode = "1234" // matches mock.foundCode

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        } withDependencies: {
            $0.apiClient.addWinner = { _, _ in }
        }

        await store.send(.view(.submitCodeButtonTapped)) {
            $0.enteredCode = ""
            $0.isEnteringFoundCode = false
        }
        await store.receive(\.internal.winnerRegistered)
    }

    @Test func submitCodeButtonTappedWithWrongCodeShowsAlert() async {
        var state = HunterMapFeature.State(game: .mock)
        state.enteredCode = "9999" // wrong code

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        }

        await store.send(.view(.submitCodeButtonTapped)) {
            $0.enteredCode = ""
            $0.isEnteringFoundCode = false
            $0.wrongCodeAttempts = 1
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
            Winner(hunterId: "other-hunter", hunterName: "Julien", timestamp: Timestamp(date: .now))
        ]

        let previousCount = state.previousWinnersCount
        #expect(previousCount == -1)

        // Simulate first config update setting the baseline
        state.previousWinnersCount = game.winners.count // 0

        guard let latest = updatedGame.winners.last else { return }
        #expect(latest.hunterId != state.hunterId)

        state.winnerNotification = "\(latest.hunterName) found the chicken! 🐔"
        state.previousWinnersCount = updatedGame.winners.count

        #expect(state.winnerNotification == "Julien found the chicken! 🐔")
        #expect(state.previousWinnersCount == 1)
    }

    @Test func gameConfigUpdatedOwnWinDoesNotShowNotification() async {
        var game = Game.mock
        game.hunterIds = ["my-hunter-id", "other-hunter"]
        var state = HunterMapFeature.State(game: game)
        state.hunterId = "my-hunter-id"
        state.previousWinnersCount = 0

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        } withDependencies: {
            $0.locationClient.stopTracking = { }
            $0.liveActivityClient.end = { _ in }
        }
        store.exhaustivity = .off

        var updatedGame = game
        updatedGame.winners = [
            Winner(hunterId: "my-hunter-id", hunterName: "Me", timestamp: Timestamp(date: .now))
        ]

        await store.send(.internal(.gameConfigUpdated(updatedGame)))

        // The key assertion: own win should NOT trigger a notification
        #expect(store.state.winnerNotification == nil)
    }

    @Test func hunterNameDefaultsToHunter() {
        let state = HunterMapFeature.State(game: .mock)
        #expect(state.hunterName == "Hunter")
    }

    // MARK: - setGameTriggered with status == .done

    @Test func gameConfigUpdatedWithDoneStatusShowsGameOver() async {
        var game = Game.mock
        game.status = .done

        let store = TestStore(initialState: HunterMapFeature.State(game: .mock)) {
            HunterMapFeature()
        } withDependencies: {
            $0.locationClient.stopTracking = { }
        }

        await store.send(.internal(.gameConfigUpdated( game))) {
            $0.game = game
            $0.destination = .alert(
                AlertState {
                    TextState("Game Over")
                } actions: {
                    ButtonState(action: .gameOver) {
                        TextState("OK")
                    }
                } message: {
                    TextState("The game has ended!")
                }
            )
        }
    }

    // MARK: - Cancel / leave game

    @Test func cancelGameButtonShowsQuitAlert() async {
        let store = TestStore(initialState: HunterMapFeature.State(game: .mock)) {
            HunterMapFeature()
        }

        await store.send(.view(.cancelGameButtonTapped)) {
            $0.destination = .alert(
                AlertState {
                    TextState("Quit game")
                } actions: {
                    ButtonState(role: .cancel) {
                        TextState("Never mind")
                    }
                    ButtonState(action: .leaveGame) {
                        TextState("Quit")
                    }
                } message: {
                    TextState("Are you sure you want to quit the game?")
                }
            )
        }
    }

    @Test func leaveGameAlertSendsGoToMenu() async {
        var state = HunterMapFeature.State(game: .mock)
        state.destination = .alert(
            AlertState {
                TextState("Quit game")
            } actions: {
                ButtonState(role: .cancel) {
                    TextState("Never mind")
                }
                ButtonState(action: .leaveGame) {
                    TextState("Quit")
                }
            } message: {
                TextState("Are you sure you want to quit the game?")
            }
        )

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        } withDependencies: {
            $0.locationClient.stopTracking = { }
        }

        await store.send(.destination(.presented(.alert(.leaveGame)))) {
            $0.destination = nil
        }
        await store.receive(\.delegate.returnedToMenu)
    }

    @Test func gameOverAlertSendsGoToMenu() async {
        var state = HunterMapFeature.State(game: .mock)
        state.destination = .alert(
            AlertState {
                TextState("Game Over")
            } actions: {
                ButtonState(action: .gameOver) {
                    TextState("OK")
                }
            } message: {
                TextState("The game has ended!")
            }
        )

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        }

        await store.send(.destination(.presented(.alert(.gameOver)))) {
            $0.destination = nil
        }
        await store.receive(\.delegate.allHuntersFound)
    }

    // MARK: - Game info

    @Test func infoButtonTappedShowsGameInfo() async {
        let store = TestStore(initialState: HunterMapFeature.State(game: .mock)) {
            HunterMapFeature()
        }

        await store.send(.view(.infoButtonTapped)) {
            $0.showGameInfo = true
        }
    }

    @Test func gameInfoDismissedHidesGameInfo() async {
        var state = HunterMapFeature.State(game: .mock)
        state.showGameInfo = true

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        }

        await store.send(.view(.gameInfoDismissed)) {
            $0.showGameInfo = false
        }
    }

    // MARK: - Dismiss countdown

    @Test func countdownDismissedClearsState() async {
        var state = HunterMapFeature.State(game: .mock)
        state.countdownNumber = 3
        state.countdownText = "GO!"

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        }

        await store.send(.internal(.countdownDismissed)) {
            $0.countdownNumber = nil
            $0.countdownText = nil
        }
    }

    // MARK: - Dismiss winner notification

    @Test func winnerNotificationDismissedClearsNotification() async {
        var state = HunterMapFeature.State(game: .mock)
        state.winnerNotification = "Test notification"

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        }

        await store.send(.internal(.winnerNotificationDismissed)) {
            $0.winnerNotification = nil
        }
    }

    // MARK: - User location

    @Test func userLocationUpdatedSetsLocation() async {
        let store = TestStore(initialState: HunterMapFeature.State(game: .mock)) {
            HunterMapFeature()
        }

        let location = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        await store.send(.internal(.userLocationUpdated(location))) {
            $0.userLocation = location
        }
    }

    // MARK: - Submit found code cooldown

    @Test func submitCodeButtonTappedOnCooldownDoesNothing() async {
        var state = HunterMapFeature.State(game: .mock)
        state.enteredCode = "9999"
        state.codeCooldownUntil = .now.addingTimeInterval(60)

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        }

        await store.send(.view(.submitCodeButtonTapped))
    }

    @Test func submitCodeButtonTappedTriggersCooldownAfterMaxAttempts() async {
        var state = HunterMapFeature.State(game: .mock)
        state.enteredCode = "9999"
        state.wrongCodeAttempts = AppConstants.codeMaxWrongAttempts - 1

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        }
        store.exhaustivity = .off

        await store.send(.view(.submitCodeButtonTapped)) {
            $0.enteredCode = ""
            $0.isEnteringFoundCode = false
            $0.wrongCodeAttempts = 0
        }
        // codeCooldownUntil is set to .now + codeCooldownSeconds (time-dependent)
        #expect(store.state.codeCooldownUntil != nil)
    }

    // MARK: - Timer game over by time

    @Test func timerTickedGameOverByTime() async {
        var game = startedGameMock
        game.endDate = .now.addingTimeInterval(-1)
        var state = HunterMapFeature.State(game: game)
        state.radius = 500

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        } withDependencies: {
            $0.locationClient.stopTracking = { }
        }
        store.exhaustivity = .off

        await store.send(.internal(.timerTicked)) {
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

    // MARK: - Winner auto-dismiss effect

    @Test func gameConfigUpdatedWithNewWinnerSchedulesAutoDismiss() async {
        let clock = TestClock()
        var game = Game.mock
        game.hunterIds = ["my-hunter-id", "other-hunter", "third-hunter"]
        var state = HunterMapFeature.State(game: game)
        state.hunterId = "my-hunter-id"
        state.previousWinnersCount = 0

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        } withDependencies: {
            $0.continuousClock = clock
        }

        var updatedGame = game
        updatedGame.winners = [
            Winner(hunterId: "other-hunter", hunterName: "Alice", timestamp: Timestamp(date: .now))
        ]

        await store.send(.internal(.gameConfigUpdated( updatedGame))) {
            let (lastUpdate, lastRadius) = updatedGame.findLastUpdate()
            $0.game = updatedGame
            $0.radius = lastRadius
            $0.nextRadiusUpdate = lastUpdate
            // followTheChicken mode: mapCircle stays nil when no chicken location received yet
            $0.lastLiveActivityState = $0.liveActivityState
            $0.winnerNotification = "Alice found the chicken! 🐔"
            $0.previousWinnersCount = 1
        }

        await clock.advance(by: .seconds(AppConstants.winnerNotificationSeconds))
        await store.receive(\.internal.winnerNotificationDismissed) {
            $0.winnerNotification = nil
        }
    }
}
