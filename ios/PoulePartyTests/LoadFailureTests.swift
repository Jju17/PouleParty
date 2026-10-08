import ComposableArchitecture
import Foundation
import Testing
@testable import PouleParty

@MainActor
struct LoadFailureTests {
    @Test func scheduleIsRetriedThenReturned() async {
        let calls = LockIsolated(0)
        let result = await loadZoneSchedule("g", fetch: { _ in
            calls.withValue { $0 += 1 }
            if calls.value < 3 { throw ApiError(code: .network) }
            return [ZoneCircle(order: 0, radiusMeters: 100, lat: 50, lng: 4)]
        }, sleep: { _ in })
        #expect(calls.value == 3)
        #expect((try? result.get())?.count == 1)
    }

    @Test func scheduleGivesUpAfterTheLastAttempt() async {
        let delays = LockIsolated<[Duration]>([])
        let result = await loadZoneSchedule("g", fetch: { _ in throw ApiError(code: .network) }, sleep: { d in delays.withValue { $0.append(d) } })
        #expect(delays.value == [.seconds(1), .seconds(2)])
        guard case let .failure(error) = result else { Issue.record("expected a failure"); return }
        #expect(ApiError(error).code == .network)
    }

    @Test func hunterMapShowsAZoneErrorInsteadOfAnEmptyZone() async {
        let store = TestStore(initialState: HunterMapFeature.State(game: .mock)) {
            HunterMapFeature()
        }
        store.exhaustivity = .off
        await store.send(.internal(.scheduleLoadFailed(ApiErrorCode.network.message))) {
            $0.zoneScheduleError = ApiErrorCode.network.message
        }
        await store.send(.internal(.scheduleLoaded([]))) {
            $0.zoneScheduleError = nil
        }
    }

    @Test func homeReportsAFailedActiveGameLookup() async {
        let clock = TestClock()
        let store = TestStore(initialState: HomeFeature.State()) {
            HomeFeature()
        } withDependencies: {
            $0.continuousClock = clock
            $0.userClient.currentUserId = { "user-1" }
            $0.apiClient.findActiveGame = { _ in throw ApiError(code: .network) }
        }
        store.exhaustivity = .off
        await store.send(.onTask)
        await clock.advance(by: .seconds(2))
        await store.receive(\.activeGameLookupFailed) {
            $0.activeGameLookupError = ApiErrorCode.network.message
        }
    }

    @Test func settingsShowsAnErrorInsteadOfNoGames() async {
        let store = TestStore(initialState: SettingsFeature.State()) {
            SettingsFeature()
        } withDependencies: {
            $0.apiClient.fetchMyGames = { _ in throw ApiError(code: .network) }
        }
        store.exhaustivity = .off
        await store.send(.onAppear)
        await store.receive(\.myGamesFailed) {
            $0.isLoadingGames = false
            $0.myGamesError = ApiErrorCode.network.message
        }
    }

    @Test func victoryShowsAnErrorWhenTeamNamesFail() async {
        let store = TestStore(initialState: VictoryFeature.State(game: .mock, hunterId: "h1", hunterName: "Team")) {
            VictoryFeature()
        } withDependencies: {
            $0.apiClient.fetchAllRegistrations = { _ in throw ApiError(code: .network) }
        }
        store.exhaustivity = .off
        await store.send(.onTask)
        await store.receive(\.registrationsFailed) {
            $0.registrationsError = ApiErrorCode.network.message
        }
        await store.skipInFlightEffects()
    }
}
