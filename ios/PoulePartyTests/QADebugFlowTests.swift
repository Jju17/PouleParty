import ComposableArchitecture
import CoreLocation
import FirebaseFirestore
import Testing
@testable import PouleParty

/// Integration coverage for the QA debug feature: the on-map debug panel
/// actions (chicken + GameMaster) route through `apiClient.debugAdvanceGame`
/// with the right action, and creating a debug game compresses the timing so
/// every phase is reachable in minutes.
@MainActor
struct QADebugFlowTests {

    // MARK: - Debug panel actions (chicken)

    @Test func chickenDebugEndNowCallsCallable() async {
        let captured = LockIsolated<String?>(nil)
        let store = TestStore(initialState: ChickenMapFeature.State(game: .mock)) {
            ChickenMapFeature()
        } withDependencies: {
            $0.apiClient.debugAdvanceGame = { _, action in captured.withValue { $0 = action } }
        }
        store.exhaustivity = .off

        await store.send(.view(.debugEndNowTapped))
        await store.finish()

        #expect(captured.value == "endNow")
    }

    @Test func chickenDebugSpawnCallsCallable() async {
        let captured = LockIsolated<String?>(nil)
        let store = TestStore(initialState: ChickenMapFeature.State(game: .mock)) {
            ChickenMapFeature()
        } withDependencies: {
            $0.apiClient.debugAdvanceGame = { _, action in captured.withValue { $0 = action } }
        }
        store.exhaustivity = .off

        await store.send(.view(.debugSpawnPowerUpsTapped))
        await store.finish()

        #expect(captured.value == "spawnPowerUp")
    }

    // MARK: - Debug panel actions (GameMaster)

    @Test func gameMasterDebugEndNowCallsCallable() async {
        let captured = LockIsolated<String?>(nil)
        let store = TestStore(initialState: GameMasterMapFeature.State(game: .mock)) {
            GameMasterMapFeature()
        } withDependencies: {
            $0.apiClient.debugAdvanceGame = { _, action in captured.withValue { $0 = action } }
        }
        store.exhaustivity = .off

        await store.send(.view(.debugEndNowTapped))
        await store.finish()

        #expect(captured.value == "endNow")
    }

    @Test func gameMasterDebugSpawnCallsCallable() async {
        let captured = LockIsolated<String?>(nil)
        let store = TestStore(initialState: GameMasterMapFeature.State(game: .mock)) {
            GameMasterMapFeature()
        } withDependencies: {
            $0.apiClient.debugAdvanceGame = { _, action in captured.withValue { $0 = action } }
        }
        store.exhaustivity = .off

        await store.send(.view(.debugSpawnPowerUpsTapped))
        await store.finish()

        #expect(captured.value == "spawnPowerUp")
    }

    // MARK: - Debug game creation compresses the timing

    @Test func debugGameCreationCompressesTiming() async {
        let captured = LockIsolated<Game?>(nil)
        var game = Game.mock
        game.isDebugGame = true
        let shared = Shared(value: game)
        let state = GameCreationFeature.State(
            game: shared,
            mapConfigState: ChickenMapConfigFeature.State(game: shared),
            isDebugGame: true
        )
        let store = TestStore(initialState: state) {
            GameCreationFeature()
        } withDependencies: {
            $0.apiClient.setConfig = { g in captured.withValue { $0 = g } }
            $0.apiClient.setGameMasterPassword = { _, _ in }
        }
        store.exhaustivity = .off

        await store.send(.startGameButtonTapped)
        await store.finish()

        let result = captured.value
        #expect(result?.isDebugGame == true)
        #expect(result?.manualStartEnabled == true)
        #expect(result?.timing.headStartMinutes == 0)
        #expect(result?.zone.shrinkIntervalMinutes == 1)
        if let result {
            // ~5-minute compressed duration. The `startDate` setter snaps to
            // the minute, so the exact span drifts up to a minute — assert the
            // band rather than an exact 300 s.
            let duration = result.endDate.timeIntervalSince(result.startDate)
            #expect(duration > 240 && duration < 360)
        }
    }

    // MARK: - A non-debug game keeps the standard timing

    @Test func standardGameCreationKeepsStandardTiming() async {
        let captured = LockIsolated<Game?>(nil)
        let game = Game.mock
        let shared = Shared(value: game)
        let state = GameCreationFeature.State(
            game: shared,
            mapConfigState: ChickenMapConfigFeature.State(game: shared),
            isDebugGame: false
        )
        let store = TestStore(initialState: state) {
            GameCreationFeature()
        } withDependencies: {
            $0.apiClient.setConfig = { g in captured.withValue { $0 = g } }
            $0.apiClient.setGameMasterPassword = { _, _ in }
        }
        store.exhaustivity = .off

        await store.send(.startGameButtonTapped)
        await store.finish()

        #expect(captured.value?.isDebugGame == false)
    }

    // MARK: - Edge case: callable failure is swallowed (no crash, no state change)

    @Test func chickenDebugActionSwallowsCallableError() async {
        struct Boom: Error {}
        let store = TestStore(initialState: ChickenMapFeature.State(game: .mock)) {
            ChickenMapFeature()
        } withDependencies: {
            $0.apiClient.debugAdvanceGame = { _, _ in throw Boom() }
        }
        store.exhaustivity = .off

        await store.send(.view(.debugEndNowTapped))
        await store.finish()

        #expect(store.state.isGameOver == false)
    }

    @Test func gameMasterDebugActionSwallowsCallableError() async {
        struct Boom: Error {}
        let store = TestStore(initialState: GameMasterMapFeature.State(game: .mock)) {
            GameMasterMapFeature()
        } withDependencies: {
            $0.apiClient.debugAdvanceGame = { _, _ in throw Boom() }
        }
        store.exhaustivity = .off

        await store.send(.view(.debugSpawnPowerUpsTapped))
        await store.finish()
    }
}
