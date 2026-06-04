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
        // PP-zone-stored: gameInitialized seeds the circle from the zone
        // center + radius and fires a one-shot schedule fetch. The default
        // testValue `fetchZoneSchedule` returns [], so `.scheduleLoaded([])`
        // arrives but produces no net state change (empty circles fall back
        // to the same radius / center).
        let game = Game.mock
        let store = TestStore(initialState: ChickenMapFeature.State(game: game)) {
            ChickenMapFeature()
        }

        await store.send(.view(.gameInitialized)) {
            $0.radius = Int(game.zone.radius)
            $0.nextRadiusUpdate = game.hunterStartDate
            $0.mapCircle = CircleOverlay(
                center: game.zone.center.toCLCoordinates,
                radius: CLLocationDistance(game.zone.radius)
            )
            $0.lastLiveActivityState = $0.liveActivityState
        }
        await store.receive(\.internal.scheduleLoaded)
    }

    @Test func beenFoundButtonShowsEndGameCode() async {
        // CRIT-2 (audit 2026-05-17): destination uses
        // `state.chickenFoundCode` (populated by getFoundCode CF on map
        // load) instead of `state.game.foundCode`. Seed it directly so
        // the assertion stays deterministic.
        var initial = ChickenMapFeature.State(game: .mock)
        initial.chickenFoundCode = "1234"
        let store = TestStore(initialState: initial) {
            ChickenMapFeature()
        }

        await store.send(.view(.beenFoundButtonTapped)) {
            $0.destination = .endGameCode("1234")
        }
    }

    @Test func cancelGameButtonShowsAlert() async {
        let store = TestStore(initialState: ChickenMapFeature.State(game: .mock)) {
            ChickenMapFeature()
        }

        await store.send(.view(.cancelGameButtonTapped)) {
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

        await store.send(.view(.endGameCodeDismissed)) {
            $0.destination = nil
        }
    }

    @Test func newLocationFetchedUpdatesMapCircle() async {
        let store = TestStore(initialState: ChickenMapFeature.State(game: .mock)) {
            ChickenMapFeature()
        }

        let location = CLLocationCoordinate2D(latitude: 50.0, longitude: 4.0)
        await store.send(.internal(.newLocationFetched(location))) {
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

        await store.send(.internal(.hunterLocationsUpdated(hunters))) {
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

        await store.send(.internal(.hunterLocationsUpdated([]))) {
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
            Winner(hunterId: "h1", hunterName: "Julien", timestamp: Timestamp(date: .now))
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
        let winner = Winner(hunterId: "h1", hunterName: "Julien", timestamp: Timestamp(date: .now))
        var game = Game.mock
        game.winners = [winner]

        var state = ChickenMapFeature.State(game: game)
        state.previousWinnersCount = 1

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        }

        // Same game, same number of winners → no notification, but lastLiveActivityState may update
        await store.send(.internal(.gameUpdated(game))) {
            $0.lastLiveActivityState = $0.liveActivityState
        }
    }

    @Test func winnerNotificationDismissedClearsNotification() async {
        var state = ChickenMapFeature.State(game: .mock)
        state.winnerNotification = "Test notification"

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        }

        await store.send(.internal(.winnerNotificationDismissed)) {
            $0.winnerNotification = nil
        }
    }

    // MARK: - stayInTheZone: location does not move circle

    @Test func newLocationFetchedDoesNotMoveCircleInStayInTheZone() async {
        var game = Game.mock
        game.gameMode = .stayInTheZone
        var state = ChickenMapFeature.State(game: game)
        let initialCenter = CLLocationCoordinate2D(latitude: 50.8466, longitude: 4.3528)
        state.mapCircle = CircleOverlay(center: initialCenter, radius: 1500)

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        }

        let newLocation = CLLocationCoordinate2D(latitude: 51.0, longitude: 5.0)
        await store.send(.internal(.newLocationFetched(newLocation))) {
            $0.userLocation = newLocation
            // mapCircle should NOT change in stayInTheZone
        }
    }

    // MARK: - Game info

    @Test func infoButtonTappedShowsGameInfo() async {
        let store = TestStore(initialState: ChickenMapFeature.State(game: .mock)) {
            ChickenMapFeature()
        }

        await store.send(.view(.infoButtonTapped)) {
            $0.showGameInfo = true
        }
    }

    @Test func gameInfoDismissedHidesGameInfo() async {
        var state = ChickenMapFeature.State(game: .mock)
        state.showGameInfo = true

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        }

        await store.send(.view(.gameInfoDismissed)) {
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

        await store.send(.internal(.countdownDismissed)) {
            $0.countdownNumber = nil
            $0.countdownText = nil
        }
    }

    // MARK: - Timer + stayInTheZone

    @Test func timerTickedUpdatesCircleInStayInTheZone() async {
        // PP-zone-stored: the timer resolves the active circle from the
        // stored schedule (no on-device recompute). In stayInTheZone the
        // center comes from the stored circle. With the head start in the
        // past and a 1-min shrink interval, every shrink has elapsed so the
        // active index clamps to the final stored circle.
        var game = Game.mock
        game.gameMode = .stayInTheZone
        game.startDate = .now.addingTimeInterval(-600)   // started 10 min ago
        game.endDate = .now.addingTimeInterval(3000)      // ends in 50 min
        game.timing.headStartMinutes = 0
        game.zone.shrinkIntervalMinutes = 1.0
        var state = ChickenMapFeature.State(game: game)
        state.radius = 1500
        state.nextRadiusUpdate = .now.addingTimeInterval(-1)
        let finalCircle = ZoneCircle(order: 1, radiusMeters: 400, lat: 50.8500, lng: 4.3450)
        state.circles = [
            ZoneCircle(order: 0, radiusMeters: 1500, lat: 50.8466, lng: 4.3528),
            finalCircle,
        ]

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        }
        store.exhaustivity = .off

        await store.send(.internal(.timerTicked)) {
            $0.radius = 400
            $0.mapCircle = CircleOverlay(
                center: finalCircle.center,
                radius: CLLocationDistance(400)
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

        await store.send(.internal(.timerTicked)) {
            $0.isGameOver = true
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
            $0.isCancelling = true
        }
        await store.receive(\.delegate.returnedToMenu)
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
            Winner(hunterId: "h1", hunterName: "Julien", timestamp: Timestamp(date: .now))
        ]

        await store.send(.internal(.gameUpdated(updatedGame))) {
            $0.game = updatedGame
            $0.lastLiveActivityState = $0.liveActivityState
            $0.winnerNotification = "Julien found the chicken! 🐔"
            $0.previousWinnersCount = 1
        }

        await clock.advance(by: .seconds(AppConstants.winnerNotificationSeconds))
        await store.receive(\.internal.winnerNotificationDismissed) {
            $0.winnerNotification = nil
        }
    }

    // MARK: - PP-19 end-game stays on map
    //
    // The map must stay mounted at gameOver — `isGameOver` flips to
    // true, no auto-transition to Victory is dispatched. The chicken's
    // GPS effect cancels (no more `setChickenLocation` writes). Mirrors
    // `ChickenMapViewModelBehaviorTest` on Android.

    /// Scenario 1: timeout — `nowDate >= endDate` flips `isGameOver`
    /// to true and the map stays mounted (no `.delegate.allHuntersFound`
    /// auto-fire, no transition to Victory). The GPS effect is killed
    /// synchronously via `locationClient.stopTracking()`.
    @Test func pp19_timeoutFlipsIsGameOverWithoutTransition() async {
        var game = Game.mock
        game.startDate = .now.addingTimeInterval(-3600)
        game.endDate = .now.addingTimeInterval(-1)
        var state = ChickenMapFeature.State(game: game)
        state.radius = 500

        let stopCalls = LockIsolated(0)
        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        } withDependencies: {
            $0.locationClient.stopTracking = {
                stopCalls.withValue { $0 += 1 }
            }
            $0.apiClient.updateGameStatus = { _, _ in }
        }
        store.exhaustivity = .off

        await store.send(.internal(.timerTicked)) {
            $0.isGameOver = true
        }
        #expect(stopCalls.value == 1, "locationClient.stopTracking must be called once GPS gating fires")
        // The map stays mounted: no `.delegate.allHuntersFound` is sent
        // automatically. The chicken sees the gameOver alert and only
        // emits the delegate when the user taps OK.
    }

    /// Scenario 2 (PP-zone-stored): the zone no longer "collapses" to 0 /
    /// ends the game. The stored schedule stops at the 50m final circle and
    /// stays there; the game ends only by time / all-found / cancel. So even
    /// with every shrink elapsed, `isGameOver` stays false (endDate is in the
    /// future) and the radius settles on the last stored circle.
    @Test func pp_zoneStored_zoneShrinksToFinalCircleWithoutGameOver() async {
        var game = Game.mock
        game.gameMode = .followTheChicken
        game.startDate = .now.addingTimeInterval(-3600)
        game.endDate = .now.addingTimeInterval(3600)
        game.timing.headStartMinutes = 0
        game.zone.shrinkIntervalMinutes = 1.0
        var state = ChickenMapFeature.State(game: game)
        state.radius = 100
        state.nextRadiusUpdate = .now.addingTimeInterval(-1)
        state.circles = [
            ZoneCircle(order: 0, radiusMeters: 100, lat: 50.8466, lng: 4.3528),
            ZoneCircle(order: 1, radiusMeters: 50, lat: 50.8466, lng: 4.3528),
        ]

        let stopCalls = LockIsolated(0)
        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        } withDependencies: {
            $0.locationClient.stopTracking = {
                stopCalls.withValue { $0 += 1 }
            }
            $0.apiClient.updateGameStatus = { _, _ in }
        }
        store.exhaustivity = .off

        await store.send(.internal(.timerTicked)) {
            $0.radius = 50
        }
        #expect(store.state.isGameOver == false, "game must NOT end on zone shrink (ends by time now)")
    }

    /// Scenario 3: all hunters found — chicken side. When
    /// `winners.count >= hunterIds.count`, the chicken flips
    /// `isGameOver` and calls `stopTracking()`; no auto-transition.
    @Test func pp19_allHuntersFoundFlipsIsGameOverWithoutTransition() async {
        var game = Game.mock
        game.hunterIds = ["h1", "h2"]
        var state = ChickenMapFeature.State(game: game)
        state.previousWinnersCount = 0

        var updatedGame = game
        updatedGame.winners = [
            Winner(hunterId: "h1", hunterName: "Alice", timestamp: Timestamp(date: .now)),
            Winner(hunterId: "h2", hunterName: "Bob", timestamp: Timestamp(date: .now))
        ]

        let stopCalls = LockIsolated(0)
        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        } withDependencies: {
            $0.continuousClock = ImmediateClock()
            $0.locationClient.stopTracking = {
                stopCalls.withValue { $0 += 1 }
            }
            $0.apiClient.updateGameStatus = { _, _ in }
            $0.liveActivityClient.end = { _ in }
        }
        store.exhaustivity = .off

        await store.send(.internal(.gameUpdated(updatedGame))) {
            $0.isGameOver = true
        }
        #expect(stopCalls.value == 1, "Chicken must call stopTracking once all hunters found")
    }

    /// Scenario 5 (chicken): once `isGameOver` is set via the gameOver
    /// alert, sending `.cancelGameButtonTapped` is a no-op (guarded by
    /// `guard !state.isGameOver`). Used to prove that the post-gameOver
    /// surface treats the chicken as terminal.
    @Test func pp19_cancelGameButtonNoOpAfterGameOver() async {
        var state = ChickenMapFeature.State(game: .mock)
        state.isGameOver = true

        let store = TestStore(initialState: state) {
            ChickenMapFeature()
        }

        // No alert should be presented because the chicken is already
        // in the post-game state.
        await store.send(.view(.cancelGameButtonTapped))
        #expect(store.state.destination == nil)
    }
}
