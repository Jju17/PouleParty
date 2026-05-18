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
        // CRIT-2 (audit 2026-05-17): foundCode check is now server-side
        // via `submitFoundCode`. The integration mock returns invalidCode
        // for "0000" and success for "4321".
        var game = Game.mock
        game.startDate = .now.addingTimeInterval(-600)
        game.endDate = .now.addingTimeInterval(3000)
        // game.foundCode no longer used client-side — CF reads from
        // /private/security. Kept here for legacy game fixtures.

        var state = HunterMapFeature.State(game: game)
        state.hunterId = "test-hunter-uid"
        state.hunterName = "TestHunter"

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        } withDependencies: {
            $0.apiClient.submitFoundCode = { _, code, _ in
                if code == "4321" { return }
                throw SubmitFoundCodeError.invalidCode
            }
        }
        store.exhaustivity = .off

        // 1. Hunter taps FOUND
        await store.send(.view(.foundButtonTapped))

        // 2. Hunter enters wrong code first
        await store.send(.binding(.set(\.enteredCode, "0000")))
        await store.send(.view(.submitCodeButtonTapped))
        await store.receive(\.internal.wrongCodeRejected)
        #expect(store.state.wrongCodeAttempts == 1)
        #expect(store.state.destination != nil)

        // 3. Dismiss wrong code alert
        await store.send(.destination(.presented(.alert(.wrongCode))))

        // 4. Try again with correct code
        await store.send(.view(.foundButtonTapped))
        await store.send(.binding(.set(\.enteredCode, "4321")))
        await store.send(.view(.submitCodeButtonTapped))
        await store.receive(\.internal.winnerRegistered)
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

        await store.send(.internal(.gameConfigUpdated( endedGame))) {
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
        game.zone.shrinkIntervalMinutes = 0
        game.startDate = .now.addingTimeInterval(-600)

        let (_, radius) = game.findLastUpdate()
        #expect(radius == Int(game.zone.radius))
    }

    @Test func negativeRadiusIntervalDoesNotCrash() {
        var game = Game.mock
        game.zone.shrinkIntervalMinutes = -5
        game.startDate = .now.addingTimeInterval(-600)

        let (_, radius) = game.findLastUpdate()
        #expect(radius == Int(game.zone.radius))
    }
}
