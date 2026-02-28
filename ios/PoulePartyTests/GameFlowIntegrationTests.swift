//
//  GameFlowIntegrationTests.swift
//  PoulePartyTests
//
//  Integration tests validating complete game lifecycle flows.
//

import ComposableArchitecture
import CoreLocation
import FirebaseFirestore
import Testing
@testable import PouleParty

@MainActor
struct GameFlowIntegrationTests {

    // MARK: - Full hunter found-chicken flow

    @Test func hunterFindsChickenEndToEnd() async {
        // Setup: game already started, hunter has ID
        var game = Game.mock
        game.startDate = .now.addingTimeInterval(-600)
        game.endDate = .now.addingTimeInterval(3000)
        game.foundCode = "4321"

        var state = HunterMapFeature.State(game: game)
        state.hunterId = "test-hunter-uid"
        state.hunterName = "TestHunter"

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        } withDependencies: {
            $0.apiClient.addWinner = { _, _ in }
        }

        // 1. Hunter taps FOUND
        await store.send(.foundButtonTapped) {
            $0.isEnteringFoundCode = true
        }

        // 2. Hunter enters wrong code first
        store.exhaustivity = .off
        await store.send(.binding(.set(\.enteredCode, "0000")))
        store.exhaustivity = .on

        var wrongState = store.state
        wrongState.enteredCode = "0000"

        await store.send(.submitFoundCode) {
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

        // 3. Dismiss wrong code alert
        await store.send(.destination(.presented(.alert(.wrongCode)))) {
            $0.destination = nil
        }

        // 4. Try again with correct code
        await store.send(.foundButtonTapped) {
            $0.isEnteringFoundCode = true
        }

        store.exhaustivity = .off
        await store.send(.binding(.set(\.enteredCode, "4321")))
        store.exhaustivity = .on

        await store.send(.submitFoundCode) {
            $0.enteredCode = ""
            $0.isEnteringFoundCode = false
        }
        await store.receive(\.goToVictory)
    }

    // MARK: - Game ends by time

    @Test func gameEndsWhenTimerExpires() async {
        var game = Game.mock
        game.startDate = .now.addingTimeInterval(-3600)
        game.endDate = .now.addingTimeInterval(-1)

        var state = HunterMapFeature.State(game: game)
        state.hunterId = "test-uid"

        let store = TestStore(initialState: state) {
            HunterMapFeature()
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

    // MARK: - Game cancelled by chicken

    @Test func chickenCancelsGameDetectedByHunter() async {
        var game = Game.mock
        game.startDate = .now.addingTimeInterval(-600)
        game.endDate = .now.addingTimeInterval(3000)

        let state = HunterMapFeature.State(game: game)

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        }

        var endedGame = game
        endedGame.status = .done

        await store.send(.setGameTriggered(to: endedGame)) {
            $0.game = endedGame
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

    // MARK: - Radius interval guard

    @Test func zeroRadiusIntervalDoesNotCrash() {
        var game = Game.mock
        game.radiusIntervalUpdate = 0
        game.startDate = .now.addingTimeInterval(-600)

        let (_, radius) = game.findLastUpdate()
        #expect(radius == Int(game.initialRadius))
    }

    @Test func negativeRadiusIntervalDoesNotCrash() {
        var game = Game.mock
        game.radiusIntervalUpdate = -5
        game.startDate = .now.addingTimeInterval(-600)

        let (_, radius) = game.findLastUpdate()
        #expect(radius == Int(game.initialRadius))
    }
}
