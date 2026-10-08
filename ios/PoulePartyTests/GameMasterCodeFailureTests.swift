import ComposableArchitecture
import Foundation
import Testing
@testable import PouleParty

@MainActor
struct GameMasterCodeFailureTests {
    private func makeState() -> GameCreationFeature.State {
        let shared = Shared(value: Game.mock)
        var state = GameCreationFeature.State(game: shared, mapConfigState: ChickenMapConfigFeature.State(game: shared))
        state.isGameMasterEnabled = true
        state.gameMasterPassword = "1234"
        return state
    }

    @Test func aFailedRefereeCodeKeepsTheCreatorOnTheWizardUntilRetried() async {
        let attempts = LockIsolated(0)
        let store = TestStore(initialState: makeState()) {
            GameCreationFeature()
        } withDependencies: {
            $0.apiClient.setGameMasterPassword = { _, _ in
                attempts.withValue { $0 += 1 }
                if attempts.value == 1 { throw ApiError(code: .network) }
            }
        }
        store.exhaustivity = .off

        await store.send(.startGameButtonTapped)
        await store.receive(\.gameMasterCodeFailed)
        #expect(store.state.destination != nil)

        await store.send(.destination(.presented(.alert(.retryGameMasterCode))))
        await store.receive(\.gameCreated)
        #expect(attempts.value == 2)
    }

    @Test func theCreatorCanContinueWithoutAReferee() async {
        let store = TestStore(initialState: makeState()) {
            GameCreationFeature()
        } withDependencies: {
            $0.apiClient.setGameMasterPassword = { _, _ in throw ApiError(code: .network) }
        }
        store.exhaustivity = .off

        await store.send(.startGameButtonTapped)
        await store.receive(\.gameMasterCodeFailed)
        await store.send(.destination(.presented(.alert(.continueWithoutGameMaster))))
        await store.receive(\.gameCreated)
    }

    @Test func aFailedGameWriteShowsTheReason() async {
        let store = TestStore(initialState: makeState()) {
            GameCreationFeature()
        } withDependencies: {
            $0.apiClient.setConfig = { _ in throw ApiError(code: .network) }
        }
        store.exhaustivity = .off

        await store.send(.startGameButtonTapped)
        await store.receive(\.configSaveFailed)
        #expect(store.state.destination != nil)
    }
}
