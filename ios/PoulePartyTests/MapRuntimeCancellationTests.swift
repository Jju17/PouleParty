import ComposableArchitecture
import CoreLocation
import FirebaseCore
import Foundation
import Testing
@testable import PouleParty

/// Once a game is over nothing keeps running behind the end banner:
/// `finish()` fails if a timer, writer or listener is still alive.
@MainActor
struct MapRuntimeCancellationTests {
    private func runningGame() -> Game {
        var game = Game.mock
        game.status = .inProgress
        game.timing.start = .init(date: .now.addingTimeInterval(-600))
        game.timing.end = .init(date: .now.addingTimeInterval(3600))
        return game
    }

    @Test func gameMasterStopsEverythingWhenTheGameEnds() async {
        let clock = TestClock()
        let (configs, configSink) = AsyncStream<Game?>.makeStream()
        let game = runningGame()
        let store = TestStore(initialState: GameMasterMapFeature.State(game: game)) {
            GameMasterMapFeature()
        } withDependencies: {
            $0.continuousClock = clock
            $0.apiClient.gameConfigStream = { _ in configs }
        }
        store.exhaustivity = .off

        await store.send(.view(.onTask))
        var done = game
        done.status = .done
        configSink.yield(done)
        await store.receive(\.internal.gameUpdated)
        await clock.advance(by: .seconds(10))
        await store.finish()
    }

    @Test func hunterStopsEverythingWhenTheGameEnds() async {
        let clock = TestClock()
        let (configs, configSink) = AsyncStream<Game?>.makeStream()
        let game = runningGame()
        let store = TestStore(initialState: HunterMapFeature.State(game: game)) {
            HunterMapFeature()
        } withDependencies: {
            $0.continuousClock = clock
            $0.apiClient.gameConfigStream = { _ in configs }
        }
        store.exhaustivity = .off

        await store.send(.view(.onTask))
        var done = game
        done.status = .done
        configSink.yield(done)
        await store.receive(\.internal.gameConfigUpdated)
        #expect(store.state.isGameOver)
        await clock.advance(by: .seconds(10))
        await store.finish()
    }

    @Test func chickenStopsEverythingWhenTheGameEnds() async {
        let clock = TestClock()
        let (configs, configSink) = AsyncStream<Game?>.makeStream()
        let game = runningGame()
        let store = TestStore(initialState: ChickenMapFeature.State(game: game)) {
            ChickenMapFeature()
        } withDependencies: {
            $0.continuousClock = clock
            $0.apiClient.gameConfigStream = { _ in configs }
        }
        store.exhaustivity = .off

        await store.send(.view(.onTask))
        var done = game
        done.status = .done
        configSink.yield(done)
        await store.receive(\.internal.gameUpdated)
        #expect(store.state.isGameOver)
        await clock.advance(by: .seconds(40))
        await store.finish()
    }

    @Test func chickenWritesItsPositionOncePerWindow() async {
        let clock = TestClock()
        let writes = LockIsolated(0)
        let store = TestStore(initialState: ChickenMapFeature.State(game: runningGame())) {
            ChickenMapFeature()
        } withDependencies: {
            $0.continuousClock = clock
            $0.locationClient.lastLocation = { CLLocationCoordinate2D(latitude: 50.85, longitude: 4.35) }
            $0.apiClient.setChickenLocation = { _, _, _ in writes.withValue { $0 += 1 } }
        }
        store.exhaustivity = .off

        await store.send(.view(.onTask))
        await clock.advance(by: .seconds(AppConstants.locationThrottleSeconds * 3))
        #expect((3...4).contains(writes.value))
        await store.skipInFlightEffects()
    }
}
