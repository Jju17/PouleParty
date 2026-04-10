//
//  GameCreationFeatureTests.swift
//  PoulePartyTests
//

import ComposableArchitecture
import CoreLocation
import FirebaseFirestore
import Testing
@testable import PouleParty

@MainActor
struct GameCreationFeatureTests {

    // MARK: - Helpers

    private func makeState(
        pricingModel: Game.PricingModel = .free,
        gameId: String = "test-game-id",
        maxPlayers: Int = 5
    ) -> GameCreationFeature.State {
        var game = Game(id: gameId)
        game.maxPlayers = maxPlayers
        game.pricing.model = pricingModel
        if pricingModel == .deposit {
            game.registration.required = true
            game.registration.closesMinutesBefore = 15
        }
        let shared = Shared(value: game)
        let mapConfig = ChickenMapConfigFeature.State(game: shared)
        return GameCreationFeature.State(game: shared, mapConfigState: mapConfig)
    }

    private func makeStore(
        state: GameCreationFeature.State? = nil
    ) -> TestStore<GameCreationFeature.State, GameCreationFeature.Action> {
        let initial = state ?? makeState()
        return TestStore(initialState: initial) {
            GameCreationFeature()
        } withDependencies: {
            $0.analyticsClient = .testValue
            $0.apiClient.setConfig = { _ in }
        }
    }

    // MARK: - Initial state

    @Test func initialStateHasDefaultValues() {
        let state = makeState()
        #expect(state.currentStepIndex == 0)
        #expect(state.isParticipating == true)
        #expect(state.gameDurationMinutes == 90)
        #expect(state.goingForward == true)
    }

    @Test func depositPricingRequiresRegistrationByDefault() {
        let state = makeState(pricingModel: .deposit)
        #expect(state.game.registration.required == true)
    }

    @Test func freePricingDoesNotRequireRegistrationByDefault() {
        let state = makeState(pricingModel: .free)
        #expect(state.game.registration.required == false)
    }

    // MARK: - Steps flow

    @Test func stepsAreInCorrectOrderWhenParticipating() {
        let state = makeState()
        let steps = state.steps
        #expect(steps[0] == .participation)
        #expect(steps[1] == .gameMode)
        #expect(steps[2] == .zoneSetup)
        #expect(steps[3] == .registration)
        #expect(steps[4] == .startTime)
        #expect(steps[5] == .duration)
        #expect(steps[6] == .headStart)
        #expect(steps[7] == .powerUps)
        #expect(steps[8] == .chickenSeesHunters)
        #expect(steps[9] == .recap)
    }

    @Test func stepsIncludeChickenSelectionWhenNotParticipating() {
        var state = makeState()
        state.isParticipating = false
        let steps = state.steps
        #expect(steps[0] == .participation)
        #expect(steps[1] == .chickenSelection)
        #expect(steps[2] == .gameMode)
        #expect(steps.count == 11)
    }

    @Test func registrationComesBeforeStartTimeInSteps() {
        let state = makeState()
        let regIndex = state.steps.firstIndex(of: .registration)!
        let startIndex = state.steps.firstIndex(of: .startTime)!
        #expect(regIndex < startIndex)
    }

    // MARK: - Navigation

    @Test func nextIncrementsStepIndex() async {
        let store = makeStore()
        await store.send(.nextTapped) {
            $0.currentStepIndex = 1
            $0.goingForward = true
        }
    }

    @Test func nextDoesNotGoPastLastStep() async {
        var state = makeState()
        state.currentStepIndex = state.steps.count - 1
        let store = makeStore(state: state)
        await store.send(.nextTapped)
    }

    @Test func backDecrementsStepIndex() async {
        var state = makeState()
        state.currentStepIndex = 2
        let store = makeStore(state: state)
        await store.send(.backTapped) {
            $0.currentStepIndex = 1
            $0.goingForward = false
        }
    }

    @Test func backDoesNotGoBelowZero() async {
        let store = makeStore()
        await store.send(.backTapped)
    }

    @Test func canGoBackFalseOnFirstStep() {
        let state = makeState()
        #expect(state.canGoBack == false)
    }

    @Test func canGoBackTrueOnSecondStep() {
        var state = makeState()
        state.currentStepIndex = 1
        #expect(state.canGoBack == true)
    }

    // MARK: - Progress

    @Test func progressOneOverTotalOnFirstStep() {
        let state = makeState()
        let expected = 1.0 / Double(state.steps.count)
        #expect(abs(state.progress - expected) < 0.001)
    }

    @Test func progressIsOneOnLastStep() {
        var state = makeState()
        state.currentStepIndex = state.steps.count - 1
        #expect(state.progress == 1.0)
    }

    // MARK: - Game mode

    @Test func updateGameModToFollowTheChickenClearsFinalCenter() async {
        var state = makeState()
        state.$game.withLock { $0.zone.finalCenter = GeoPoint(latitude: 51.0, longitude: 5.0) }
        let store = makeStore(state: state)
        await store.send(.gameModChanged(.followTheChicken)) {
            $0.$game.withLock {
                $0.gameMode = .followTheChicken
                $0.zone.finalCenter = nil
            }
            $0.mapConfigState.pinMode = .start
        }
    }

    @Test func updateGameModToStayInTheZoneDoesNotClearFinalCenter() async {
        var state = makeState()
        state.$game.withLock {
            $0.gameMode = .followTheChicken
            $0.zone.finalCenter = GeoPoint(latitude: 51.0, longitude: 5.0)
        }
        let store = makeStore(state: state)
        await store.send(.gameModChanged(.stayInTheZone)) {
            $0.$game.withLock { $0.gameMode = .stayInTheZone }
        }
    }

    // MARK: - Zone configuration

    @Test func isZoneConfiguredFalseWithDefaultLocation() {
        let state = makeState()
        #expect(state.isZoneConfigured == false)
    }

    @Test func isZoneConfiguredFalseForStayInTheZoneWithoutFinalCenter() {
        var state = makeState()
        state.$game.withLock {
            $0.gameMode = .stayInTheZone
            $0.zone.center = GeoPoint(latitude: 50.9, longitude: 4.4)
        }
        #expect(state.isZoneConfigured == false)
    }

    @Test func isZoneConfiguredTrueForStayInTheZoneWithBothLocations() {
        var state = makeState()
        state.$game.withLock {
            $0.gameMode = .stayInTheZone
            $0.zone.center = GeoPoint(latitude: 50.9, longitude: 4.4)
            $0.zone.finalCenter = GeoPoint(latitude: 51.0, longitude: 4.5)
        }
        #expect(state.isZoneConfigured == true)
    }

    @Test func isZoneConfiguredTrueForFollowTheChickenWithOnlyInitialLocation() {
        var state = makeState()
        state.$game.withLock {
            $0.gameMode = .followTheChicken
            $0.zone.center = GeoPoint(latitude: 50.9, longitude: 4.4)
        }
        #expect(state.isZoneConfigured == true)
    }

    // MARK: - Registration

    @Test func requiresRegistrationChangedEnablesAndSetsDefaultDeadline() async {
        let store = makeStore()
        await store.send(.requiresRegistrationChanged(true)) {
            $0.$game.withLock {
                $0.registration.required = true
                $0.registration.closesMinutesBefore = 15
            }
            // Clamping may push start date forward
            let minimum = $0.minimumStartDate
            if $0.game.startDate < minimum {
                $0.$game.withLock { $0.startDate = minimum }
            }
        }
    }

    @Test func requiresRegistrationChangedDisablesAndClearsDeadline() async {
        var state = makeState()
        state.$game.withLock {
            $0.registration.required = true
            $0.registration.closesMinutesBefore = 60
        }
        let store = makeStore(state: state)
        await store.send(.requiresRegistrationChanged(false)) {
            $0.$game.withLock {
                $0.registration.required = false
                $0.registration.closesMinutesBefore = nil
            }
        }
    }

    @Test func registrationClosesBeforeStartChangedUpdatesDeadline() async {
        var state = makeState()
        state.$game.withLock { $0.registration.required = true }
        let store = makeStore(state: state)
        await store.send(.registrationClosesBeforeStartChanged(60)) {
            $0.$game.withLock { $0.registration.closesMinutesBefore = 60 }
            let minimum = $0.minimumStartDate
            if $0.game.startDate < minimum {
                $0.$game.withLock { $0.startDate = minimum }
            }
        }
    }

    // MARK: - Minimum start date

    @Test func minimumStartDateOpenJoinIs1Minute() {
        let state = makeState()
        let min = state.minimumStartDate
        let expected = Date.now.addingTimeInterval(60)
        #expect(abs(min.timeIntervalSince(expected)) < 1)
    }

    @Test func minimumStartDateWith15MinDeadlineIs20Minutes() {
        var state = makeState()
        state.$game.withLock {
            $0.registration.required = true
            $0.registration.closesMinutesBefore = 15
        }
        let min = state.minimumStartDate
        let expected = Date.now.addingTimeInterval((15 + 5) * 60)
        #expect(abs(min.timeIntervalSince(expected)) < 1)
    }

    @Test func minimumStartDateWith1DayDeadlineIs1445Minutes() {
        var state = makeState()
        state.$game.withLock {
            $0.registration.required = true
            $0.registration.closesMinutesBefore = 1440
        }
        let min = state.minimumStartDate
        let expected = Date.now.addingTimeInterval((1440 + 5) * 60)
        #expect(abs(min.timeIntervalSince(expected)) < 1)
    }

    @Test func minimumStartDateRequiredButNoDeadlineIs5Minutes() {
        var state = makeState()
        state.$game.withLock {
            $0.registration.required = true
            $0.registration.closesMinutesBefore = nil
        }
        let min = state.minimumStartDate
        let expected = Date.now.addingTimeInterval(5 * 60)
        #expect(abs(min.timeIntervalSince(expected)) < 1)
    }

    // MARK: - Chicken visibility

    @Test func chickenCanSeeHuntersChangedTrue() async {
        let store = makeStore()
        await store.send(.chickenCanSeeHuntersChanged(true)) {
            $0.$game.withLock { $0.chickenCanSeeHunters = true }
        }
    }

    @Test func chickenCanSeeHuntersChangedFalse() async {
        var state = makeState()
        state.$game.withLock { $0.chickenCanSeeHunters = true }
        let store = makeStore(state: state)
        await store.send(.chickenCanSeeHuntersChanged(false)) {
            $0.$game.withLock { $0.chickenCanSeeHunters = false }
        }
    }

    // MARK: - Power-ups

    @Test func powerUpsToggledEnables() async {
        let store = makeStore()
        await store.send(.powerUpsToggled(true)) {
            $0.$game.withLock { $0.powerUps.enabled = true }
        }
    }

    @Test func powerUpsToggledDisables() async {
        var state = makeState()
        state.$game.withLock { $0.powerUps.enabled = true }
        let store = makeStore(state: state)
        await store.send(.powerUpsToggled(false)) {
            $0.$game.withLock { $0.powerUps.enabled = false }
        }
    }

    @Test func powerUpTypeToggledRemovesExistingType() async {
        let store = makeStore()
        await store.send(.powerUpTypeToggled(.zonePreview)) {
            $0.$game.withLock {
                $0.powerUps.enabledTypes.removeAll { $0 == PowerUp.PowerUpType.zonePreview.rawValue }
            }
        }
    }

    @Test func powerUpTypeToggledReAddsRemovedType() async {
        var state = makeState()
        state.$game.withLock { $0.powerUps.enabledTypes.removeAll { $0 == PowerUp.PowerUpType.zonePreview.rawValue } }
        let store = makeStore(state: state)
        await store.send(.powerUpTypeToggled(.zonePreview)) {
            $0.$game.withLock { $0.powerUps.enabledTypes.append(PowerUp.PowerUpType.zonePreview.rawValue) }
        }
    }

    // MARK: - Duration

    @Test func gameDurationChangedUpdatesState() async {
        let store = makeStore()
        await store.send(.gameDurationChanged(120)) {
            $0.gameDurationMinutes = 120
            // Normal mode recalc for 120 min
            let effectiveDuration = max(120 - $0.game.timing.headStartMinutes, 1)
            let (interval, decline) = calculateNormalModeSettings(
                initialRadius: $0.game.zone.radius,
                gameDurationMinutes: effectiveDuration
            )
            $0.$game.withLock {
                $0.zone.shrinkIntervalMinutes = interval
                $0.zone.shrinkMetersPerUpdate = decline
            }
        }
    }

    // MARK: - Head start

    @Test func chickenHeadStartChangedUpdatesState() async {
        let store = makeStore()
        await store.send(.chickenHeadStartChanged(10)) {
            $0.$game.withLock { $0.timing.headStartMinutes = 10 }
            let effectiveDuration = max($0.gameDurationMinutes - 10, 1)
            let (interval, decline) = calculateNormalModeSettings(
                initialRadius: $0.game.zone.radius,
                gameDurationMinutes: effectiveDuration
            )
            $0.$game.withLock {
                $0.zone.shrinkIntervalMinutes = interval
                $0.zone.shrinkMetersPerUpdate = decline
            }
        }
    }

    // MARK: - Initial radius

    @Test func initialRadiusChangedUpdatesGameAndRecalcs() async {
        let store = makeStore()
        await store.send(.initialRadiusChanged(500)) {
            $0.$game.withLock { $0.zone.radius = 500 }
            let effectiveDuration = max($0.gameDurationMinutes - $0.game.timing.headStartMinutes, 1)
            let (interval, decline) = calculateNormalModeSettings(
                initialRadius: 500,
                gameDurationMinutes: effectiveDuration
            )
            $0.$game.withLock {
                $0.zone.shrinkIntervalMinutes = interval
                $0.zone.shrinkMetersPerUpdate = decline
            }
        }
    }

    // MARK: - Participation

    @Test func participationChangedUpdatesState() async {
        let store = makeStore()
        await store.send(.participationChanged(false)) {
            $0.isParticipating = false
        }
    }

    // MARK: - Start date

    @Test func startDateChangedUpdatesGame() async {
        let newDate = Date.now.addingTimeInterval(3600)
        let store = makeStore()
        await store.send(.startDateChanged(newDate)) {
            $0.$game.withLock { $0.startDate = newDate }
        }
    }

    // MARK: - Start game

    @Test func startGameButtonTappedCallsApiAndSendsGameCreated() async {
        var state = makeState()
        state.$game.withLock {
            $0.zone.center = GeoPoint(latitude: 50.9, longitude: 4.4)
            $0.gameMode = .followTheChicken
        }
        let store = TestStore(initialState: state) {
            GameCreationFeature()
        } withDependencies: {
            $0.analyticsClient = .testValue
            $0.apiClient.setConfig = { _ in }
        }
        store.exhaustivity = .off

        await store.send(.startGameButtonTapped)
        await store.receive(\.gameCreated)
    }

    @Test func startGameButtonTappedSendsConfigSaveFailedOnError() async {
        var state = makeState()
        state.$game.withLock {
            $0.zone.center = GeoPoint(latitude: 50.9, longitude: 4.4)
            $0.gameMode = .followTheChicken
        }
        let store = TestStore(initialState: state) {
            GameCreationFeature()
        } withDependencies: {
            $0.analyticsClient = .testValue
            $0.apiClient.setConfig = { _ in throw NSError(domain: "test", code: 0) }
        }
        store.exhaustivity = .off

        await store.send(.startGameButtonTapped)
        await store.receive(\.configSaveFailed)
    }

    // ═══════════════════════════════════════════════════
    // EDGE CASES
    // ═══════════════════════════════════════════════════

    // MARK: - Zone boundaries

    @Test func isZoneConfiguredFalseWhenLocationExactlyAtDefaultBrussels() {
        var state = makeState()
        state.$game.withLock {
            $0.gameMode = .followTheChicken
            $0.zone.center = GeoPoint(
                latitude: AppConstants.defaultLatitude,
                longitude: AppConstants.defaultLongitude
            )
        }
        #expect(state.isZoneConfigured == false)
    }

    @Test func isZoneConfiguredFalseWhenLocationWithinDefaultTolerance() {
        var state = makeState()
        state.$game.withLock {
            $0.gameMode = .followTheChicken
            $0.zone.center = GeoPoint(
                latitude: AppConstants.defaultLatitude + 0.0005,
                longitude: AppConstants.defaultLongitude + 0.0005
            )
        }
        #expect(state.isZoneConfigured == false)
    }

    @Test func isZoneConfiguredTrueJustBeyondDefaultTolerance() {
        var state = makeState()
        state.$game.withLock {
            $0.gameMode = .followTheChicken
            $0.zone.center = GeoPoint(
                latitude: AppConstants.defaultLatitude + 0.002,
                longitude: AppConstants.defaultLongitude + 0.002
            )
        }
        #expect(state.isZoneConfigured == true)
    }

    @Test func clearingFinalLocationSetsBackToNotConfiguredInStayInTheZone() {
        var state = makeState()
        state.$game.withLock {
            $0.gameMode = .stayInTheZone
            $0.zone.center = GeoPoint(latitude: 50.9, longitude: 4.4)
            $0.zone.finalCenter = GeoPoint(latitude: 51.0, longitude: 4.5)
        }
        #expect(state.isZoneConfigured == true)
        state.$game.withLock { $0.zone.finalCenter = nil }
        #expect(state.isZoneConfigured == false)
    }

    // MARK: - Power-up toggle constraints

    @Test func powerUpTypeToggledCannotRemoveLastAvailableInFollowTheChicken() async {
        var state = makeState()
        state.$game.withLock {
            $0.gameMode = .followTheChicken
            $0.powerUps.enabledTypes = [PowerUp.PowerUpType.radarPing.rawValue]
        }
        let store = makeStore(state: state)
        // Attempt to remove the last one — should not remove
        await store.send(.powerUpTypeToggled(.radarPing))
        #expect(store.state.game.powerUps.enabledTypes.count == 1)
    }

    @Test func powerUpTypeToggledCanRemoveUnavailableInStayInTheZone() async {
        var state = makeState()
        state.$game.withLock {
            $0.gameMode = .stayInTheZone
            $0.powerUps.enabledTypes = PowerUp.PowerUpType.allCases.map(\.rawValue)
        }
        let store = makeStore(state: state)
        await store.send(.powerUpTypeToggled(.invisibility)) {
            $0.$game.withLock {
                $0.powerUps.enabledTypes.removeAll { $0 == PowerUp.PowerUpType.invisibility.rawValue }
            }
        }
    }

    @Test func powerUpTypeToggledLastAvailableInStayInTheZoneCountsOnlyAvailable() async {
        var state = makeState()
        state.$game.withLock {
            $0.gameMode = .stayInTheZone
            // Only ZONE_FREEZE available (other 2 available types removed)
            $0.powerUps.enabledTypes = [
                PowerUp.PowerUpType.zoneFreeze.rawValue,
                PowerUp.PowerUpType.invisibility.rawValue,
                PowerUp.PowerUpType.decoy.rawValue,
                PowerUp.PowerUpType.jammer.rawValue
            ]
        }
        let store = makeStore(state: state)
        // Cannot remove last available
        await store.send(.powerUpTypeToggled(.zoneFreeze))
        #expect(store.state.game.powerUps.enabledTypes.contains(PowerUp.PowerUpType.zoneFreeze.rawValue))
    }

    // MARK: - Registration deadline edge cases

    @Test func toggleRegistrationOffThenOnRestoresDefault15Min() async {
        var state = makeState()
        state.$game.withLock {
            $0.registration.required = true
            $0.registration.closesMinutesBefore = 60
        }
        let store = makeStore(state: state)
        store.exhaustivity = .off
        await store.send(.requiresRegistrationChanged(false))
        #expect(store.state.game.registration.closesMinutesBefore == nil)
        await store.send(.requiresRegistrationChanged(true))
        #expect(store.state.game.registration.closesMinutesBefore == 15)
    }

    // MARK: - Start date clamping on navigation

    @Test func nextClampsStartDateToMinimum() async {
        var state = makeState()
        // Set registration requiring 60 min deadline → min = now + 65 min
        state.$game.withLock {
            $0.registration.required = true
            $0.registration.closesMinutesBefore = 60
            // Force startDate to well before minimum
            $0.startDate = Date.now.addingTimeInterval(60) // only 1 min
        }
        let store = makeStore(state: state)
        store.exhaustivity = .off
        await store.send(.nextTapped)
        // Note: startDate setter strips seconds, so allow up to 1 min tolerance
        let minExpected = Date.now.addingTimeInterval(65 * 60).addingTimeInterval(-60)
        #expect(store.state.game.startDate >= minExpected)
    }

    @Test func backClampsStartDateToMinimum() async {
        var state = makeState()
        state.currentStepIndex = 2
        state.$game.withLock {
            $0.registration.required = true
            $0.registration.closesMinutesBefore = 30
            $0.startDate = Date.now.addingTimeInterval(60)
        }
        let store = makeStore(state: state)
        store.exhaustivity = .off
        await store.send(.backTapped)
        // Note: startDate setter strips seconds, so allow up to 1 min tolerance
        let minExpected = Date.now.addingTimeInterval(35 * 60).addingTimeInterval(-60)
        #expect(store.state.game.startDate >= minExpected)
    }

    // MARK: - Duration edge cases

    @Test func durationZeroWithHeadStart0ProducesLargeDecline() async {
        let store = makeStore()
        store.exhaustivity = .off
        await store.send(.gameDurationChanged(0))
        // effective = max(0 - 0, 1) = 1 → numberOfShrinks = 0.2 → decline = 7000
        #expect(abs(store.state.game.zone.shrinkMetersPerUpdate - 7000) < 0.1)
    }

    @Test func durationEqualToHeadStartProducesEffectiveOne() async {
        var state = makeState()
        state.$game.withLock { $0.timing.headStartMinutes = 15 }
        let store = makeStore(state: state)
        store.exhaustivity = .off
        await store.send(.gameDurationChanged(15))
        // effective = max(15 - 15, 1) = 1 → large decline
        #expect(store.state.game.zone.shrinkMetersPerUpdate > 1000)
    }

    @Test func headStartGreaterThanDurationClampsEffectiveToOne() async {
        var state = makeState()
        state.gameDurationMinutes = 10
        let store = makeStore(state: state)
        store.exhaustivity = .off
        await store.send(.chickenHeadStartChanged(20))
        // effective = max(10 - 20, 1) = 1 → positive decline
        #expect(store.state.game.zone.shrinkMetersPerUpdate > 0)
    }

    @Test func veryLongDurationProducesSmallDecline() async {
        let store = makeStore()
        store.exhaustivity = .off
        await store.send(.gameDurationChanged(600))
        // 600/5 = 120 shrinks → (1500-100)/120 ≈ 11.67
        #expect(abs(store.state.game.zone.shrinkMetersPerUpdate - 11.67) < 0.1)
    }

    // MARK: - Initial radius edge cases

    @Test func updateInitialRadiusWithRadiusAtMinimumProducesZeroDecline() async {
        let store = makeStore()
        store.exhaustivity = .off
        await store.send(.initialRadiusChanged(100))
        #expect(abs(store.state.game.zone.shrinkMetersPerUpdate) < 0.01)
    }

    @Test func updateInitialRadiusWithRadiusBelowMinimumProducesZeroDecline() async {
        let store = makeStore()
        store.exhaustivity = .off
        await store.send(.initialRadiusChanged(50))
        #expect(abs(store.state.game.zone.shrinkMetersPerUpdate) < 0.01)
    }

    @Test func updateInitialRadiusWithVeryLargeValueProducesLargeDecline() async {
        let store = makeStore()
        store.exhaustivity = .off
        await store.send(.initialRadiusChanged(50000))
        // 90/5 = 18 shrinks → (50000-100)/18 ≈ 2772.22
        #expect(abs(store.state.game.zone.shrinkMetersPerUpdate - 2772.22) < 0.1)
    }

    // MARK: - Deposit game invariants

    @Test func depositGameRegistrationStaysRequiredAfterStart() async {
        var state = makeState(pricingModel: .deposit)
        state.$game.withLock {
            $0.zone.center = GeoPoint(latitude: 50.9, longitude: 4.4)
            $0.gameMode = .followTheChicken
        }
        let store = TestStore(initialState: state) {
            GameCreationFeature()
        } withDependencies: {
            $0.analyticsClient = .testValue
            $0.apiClient.setConfig = { _ in }
        }
        store.exhaustivity = .off
        await store.send(.startGameButtonTapped)
        await store.receive(\.gameCreated)
        #expect(store.state.game.registration.required == true)
    }

    // MARK: - Normal mode invariant

    @Test func normalModeRecalcAlwaysProducesNonNegativeDecline() async {
        let radii: [Double] = [100, 500, 1500, 50000]
        let durations: [Double] = [5, 60, 90, 180]
        let headStarts: [Double] = [0, 5, 15]
        for radius in radii {
            for duration in durations {
                for headStart in headStarts {
                    let store = makeStore()
                    store.exhaustivity = .off
                    await store.send(.initialRadiusChanged(radius))
                    await store.send(.gameDurationChanged(duration))
                    await store.send(.chickenHeadStartChanged(headStart))
                    let decline = store.state.game.zone.shrinkMetersPerUpdate
                    #expect(decline >= 0, "Decline must be >= 0 (r=\(radius) d=\(duration) hs=\(headStart)): \(decline)")
                }
            }
        }
    }

    // MARK: - Concurrency

    @Test func rapidNextCallsDoNotSkipOrGoPastMax() async {
        let store = makeStore()
        store.exhaustivity = .off
        let maxSteps = store.state.steps.count
        for _ in 0..<(maxSteps * 2) {
            await store.send(.nextTapped)
        }
        #expect(store.state.currentStepIndex == maxSteps - 1)
    }

    @Test func rapidBackCallsDoNotGoBelowZero() async {
        var state = makeState()
        state.currentStepIndex = 2
        let store = makeStore(state: state)
        store.exhaustivity = .off
        for _ in 0..<20 {
            await store.send(.backTapped)
        }
        #expect(store.state.currentStepIndex == 0)
    }

    @Test func rapidPowerUpTypeTogglesAlternateCorrectly() async {
        var state = makeState()
        state.$game.withLock { $0.gameMode = .followTheChicken }
        let store = makeStore(state: state)
        store.exhaustivity = .off
        let initial = store.state.game.powerUps.enabledTypes.contains(PowerUp.PowerUpType.zonePreview.rawValue)
        for _ in 0..<4 {
            await store.send(.powerUpTypeToggled(.zonePreview))
        }
        let after = store.state.game.powerUps.enabledTypes.contains(PowerUp.PowerUpType.zonePreview.rawValue)
        #expect(initial == after, "Even number of toggles → same state")
    }

    // MARK: - Game code

    @Test func gameCodeIsDerivedFromIdPrefix() {
        let state = makeState(gameId: "abcdef-1234-5678")
        #expect(state.game.gameCode == "ABCDEF")
    }

    @Test func foundCodeIsValidFourDigitFormat() {
        var state = makeState()
        state.$game.withLock { $0.foundCode = Game.generateFoundCode() }
        #expect(state.game.foundCode.count == 4)
        #expect(Int(state.game.foundCode) != nil)
    }
}
