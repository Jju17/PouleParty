//
//  GameCreationFeatureTests.swift
//  PoulePartyTests
//
//  Post-PP-11/12/13/14/90 wizard coverage. The pre-PP-90 file was
//  gated under `#if false` because it referenced retired APIs
//  (`Game.registration`, `requiresRegistrationChanged`, the legacy
//  3-step zone block, etc.). PP-64 rewrites it against the current
//  `GameCreationFeature` surface.
//

import ComposableArchitecture
import CoreLocation
import FirebaseFirestore
import Foundation
import Testing
@testable import PouleParty

@MainActor
struct GameCreationFeatureTests {

    // MARK: - Helpers

    private func makeState(
        gameId: String = "test-game-id",
        maxPlayers: Int = 5,
        gameMode: Game.GameMode = .stayInTheZone
    ) -> GameCreationFeature.State {
        var game = Game(id: gameId)
        game.maxPlayers = maxPlayers
        game.gameMode = gameMode
        game.foundCode = "1234"
        let shared = Shared(value: game)
        let mapConfig = ChickenMapConfigFeature.State(game: shared)
        return GameCreationFeature.State(
            game: shared,
            mapConfigState: mapConfig
        )
    }

    private func makeStore(state: GameCreationFeature.State? = nil) -> TestStore<GameCreationFeature.State, GameCreationFeature.Action> {
        let initial = state ?? makeState()
        return TestStore(initialState: initial) {
            GameCreationFeature()
        } withDependencies: {
            $0.analyticsClient = .testValue
            $0.apiClient.setConfig = { _ in }
            $0.apiClient.setGameMasterPassword = { _, _ in }
        }
    }

    // MARK: - Initial state (PP-11/12/13/90)

    @Test func initialStateHasDefaultValues() {
        let state = makeState()
        #expect(state.currentStepIndex == 0)
        #expect(state.isParticipating == true)
        #expect(state.gameDurationMinutes == 90)
        #expect(state.goingForward == true)
    }

    // MARK: - Step order (PP-11/12/13 + PP-88)

    @Test func stepsOrderInStayInTheZoneParticipating() {
        let state = makeState(gameMode: .stayInTheZone)
        let steps = state.steps
        #expect(steps[0] == .participation)
        #expect(steps[1] == .maxPlayers)
        // Wizard order: When → How long → Mode → Where → Rules.
        #expect(steps[2] == .startTime)
        #expect(steps[3] == .duration)
        #expect(steps[4] == .headStart)
        #expect(steps[5] == .gameMode)
        // PP-11 / PP-12 / PP-13: zone setup as three consecutive
        // sub-steps in stayInTheZone (default mode).
        #expect(steps[6] == .startZoneSetup)
        #expect(steps[7] == .finalZoneSetup)
        #expect(steps[8] == .zonesRecap)
        // PP-70 / PP-88: GameMaster password sits with the modifier
        // toggles at the tail of the wizard.
        #expect(steps[9] == .gameMasterPassword)
        #expect(steps[10] == .powerUps)
        #expect(steps[11] == .chickenSeesHunters)
        #expect(steps[12] == .recap)
        #expect(steps.count == 13)
    }

    @Test func stepsOrderInFollowTheChickenSkipsFinalZone() async {
        let store = makeStore()
        store.exhaustivity = .off
        await store.send(.gameModChanged(.followTheChicken))
        let steps = store.state.steps
        // PP-12: no `finalCenter` in followTheChicken — the zone
        // tracks the chicken's live position, so finalZoneSetup is
        // dropped from the sequence (forward + back). zonesRecap stays.
        #expect(steps.contains(.startZoneSetup))
        #expect(!steps.contains(.finalZoneSetup))
        #expect(steps.contains(.zonesRecap))
        #expect(steps.count == 12)
    }

    @Test func stepsIncludeChickenSelectionWhenNotParticipating() async {
        let store = makeStore()
        store.exhaustivity = .off
        await store.send(.participationChanged(false))
        let steps = store.state.steps
        #expect(steps[0] == .participation)
        #expect(steps[1] == .chickenSelection)
        #expect(steps[2] == .maxPlayers)
        // Same step count = base (13) + chickenSelection.
        #expect(steps.count == 14)
    }

    @Test func togglingParticipationKeepsStepListInSync() async {
        let store = makeStore()
        store.exhaustivity = .off
        #expect(store.state.steps.count == 13)
        await store.send(.participationChanged(false))
        #expect(store.state.steps.count == 14)
        await store.send(.participationChanged(true))
        #expect(store.state.steps.count == 13)
        await store.send(.participationChanged(false))
        await store.send(.participationChanged(false))
        #expect(store.state.steps.count == 14)
    }

    // MARK: - PP-11 isStartZoneConfigured

    @Test func isStartZoneConfiguredFalseAtDefaultBrussels() {
        let state = makeState()
        // Brand-new wizard seeds zone.center on the Brussels default.
        #expect(state.isStartZoneConfigured == false)
    }

    @Test func isStartZoneConfiguredTrueAfterMovingPin() {
        var state = makeState()
        state.$game.withLock {
            $0.zone.center = GeoPoint(latitude: 50.9, longitude: 4.4)
        }
        #expect(state.isStartZoneConfigured == true)
    }

    // MARK: - PP-12 isFinalZoneConfigured (≥ 100 m haversine)

    @Test func isFinalZoneConfiguredFalseWithoutFinal() {
        var state = makeState()
        state.$game.withLock {
            $0.zone.center = GeoPoint(latitude: 50.9, longitude: 4.4)
        }
        #expect(state.isFinalZoneConfigured == false)
    }

    @Test func isFinalZoneConfiguredFalseWhenWithin100m() {
        // ~10 m offset: 0.00009° latitude near 51°N → ~10 m. Well below
        // PP-12's 100 m threshold so Next stays gated.
        var state = makeState()
        state.$game.withLock {
            $0.zone.center = GeoPoint(latitude: 50.9, longitude: 4.4)
            $0.zone.finalCenter = GeoPoint(latitude: 50.90009, longitude: 4.4)
        }
        #expect(state.isFinalZoneConfigured == false)
        #expect(state.isZoneConfigured == false)
    }

    @Test func isFinalZoneConfiguredTrueWhenAtLeast100mAway() {
        // ~166 m offset: 0.0015° latitude near 51°N.
        var state = makeState()
        state.$game.withLock {
            $0.zone.center = GeoPoint(latitude: 50.9, longitude: 4.4)
            $0.zone.finalCenter = GeoPoint(latitude: 50.9015, longitude: 4.4)
        }
        #expect(state.isFinalZoneConfigured == true)
        #expect(state.isZoneConfigured == true)
    }

    @Test func isZoneConfiguredTrueForFollowTheChickenWithoutFinal() {
        var state = makeState(gameMode: .followTheChicken)
        state.$game.withLock {
            $0.zone.center = GeoPoint(latitude: 50.9, longitude: 4.4)
        }
        // followTheChicken: no final pin needed — the zone tracks the
        // chicken's live position.
        #expect(state.isZoneConfigured == true)
    }

    // MARK: - Game mode change clears finalCenter on follow

    @Test func gameModChangedToFollowTheChickenClearsFinalCenter() async {
        var state = makeState()
        state.$game.withLock {
            $0.zone.finalCenter = GeoPoint(latitude: 51.0, longitude: 5.0)
        }
        let store = makeStore(state: state)
        store.exhaustivity = .off
        await store.send(.gameModChanged(.followTheChicken))
        #expect(store.state.game.gameMode == .followTheChicken)
        #expect(store.state.game.finalLocation == nil)
    }

    @Test func gameModChangedToStayInTheZonePreservesFinalCenter() async {
        var state = makeState(gameMode: .followTheChicken)
        state.$game.withLock {
            $0.zone.finalCenter = GeoPoint(latitude: 51.0, longitude: 5.0)
        }
        let store = makeStore(state: state)
        store.exhaustivity = .off
        await store.send(.gameModChanged(.stayInTheZone))
        #expect(store.state.game.finalLocation != nil)
    }

    // MARK: - PP-13 zonesRecapEntered computes radius + drift seed

    @Test func zonesRecapEnteredInStayInTheZoneComputesRadiusFromPins() async {
        var state = makeState()
        state.$game.withLock {
            $0.zone.startPin = GeoPoint(latitude: 50.85, longitude: 4.35)
            $0.zone.center = GeoPoint(latitude: 50.85, longitude: 4.35)
            // ~1.1 km offset → 1.5x = ~1668 m, dominates other branches.
            $0.zone.finalCenter = GeoPoint(latitude: 50.86, longitude: 4.35)
            $0.zone.driftSeed = 0
        }
        let store = makeStore(state: state)
        store.exhaustivity = .off
        await store.send(.zonesRecapEntered)
        // Recomputed by `computeZoneRadius` to 1.5 × ~1112 m ≈ 1668 m.
        #expect(abs(store.state.game.zone.radius - 1668) < 5)
        // Drift seed allocated on first visit.
        #expect(store.state.game.zone.driftSeed != 0)
    }

    @Test func zonesRecapEnteredKeepsExistingDriftSeed() async {
        var state = makeState()
        state.$game.withLock {
            $0.zone.startPin = GeoPoint(latitude: 50.85, longitude: 4.35)
            $0.zone.center = GeoPoint(latitude: 50.85, longitude: 4.35)
            $0.zone.finalCenter = GeoPoint(latitude: 50.86, longitude: 4.35)
            $0.zone.driftSeed = 42
        }
        let store = makeStore(state: state)
        store.exhaustivity = .off
        await store.send(.zonesRecapEntered)
        // PP-14: keep the seed across back-navigations; only the
        // Shuffle button generates a new one.
        #expect(store.state.game.zone.driftSeed == 42)
    }

    @Test func zonesRecapEnteredInFollowTheChickenUsesRadiusHint() async {
        var state = makeState(gameMode: .followTheChicken)
        state.$game.withLock {
            $0.zone.startPin = GeoPoint(latitude: 50.85, longitude: 4.35)
            $0.zone.center = GeoPoint(latitude: 50.85, longitude: 4.35)
            $0.zone.radius = 2000 // Large picker.
        }
        let store = makeStore(state: state)
        store.exhaustivity = .off
        await store.send(.zonesRecapEntered)
        #expect(store.state.game.zone.radius == 2000)
    }

    // MARK: - PP-14 Shuffle button

    @Test func shuffleDriftSeedReplacesSeedAndKeepsPins() async {
        var state = makeState()
        state.$game.withLock {
            $0.zone.startPin = GeoPoint(latitude: 50.85, longitude: 4.35)
            $0.zone.center = GeoPoint(latitude: 50.85, longitude: 4.35)
            $0.zone.finalCenter = GeoPoint(latitude: 50.86, longitude: 4.35)
            $0.zone.driftSeed = 42
            $0.zone.radius = 1668
        }
        let store = makeStore(state: state)
        store.exhaustivity = .off
        await store.send(.shuffleDriftSeed)
        // Seed changed.
        #expect(store.state.game.zone.driftSeed != 42)
        #expect(store.state.game.zone.driftSeed != 0)
        // Pins preserved.
        #expect(store.state.game.startPinLocation.latitude == 50.85)
        #expect(store.state.game.zone.finalCenter?.latitude == 50.86)
        // Radius preserved — a function of the pins, not the seed.
        #expect(store.state.game.zone.radius == 1668)
    }

    // MARK: - Max players (PP-42 / PP-45)

    @Test func defaultMaxPlayersRangeIs2To5() {
        let state = makeState()
        #expect(state.maxPlayersRange == 2...5)
    }

    @Test func adminCreationMaxPlayersRangeIs2To500() {
        var state = makeState()
        state.isAdminCreation = true
        #expect(state.maxPlayersRange == 2...500)
    }

    @Test func maxPlayersChangedClampsToUpperBoundForStandard() async {
        let store = makeStore()
        await store.send(.maxPlayersChanged(50)) {
            $0.$game.withLock { $0.maxPlayers = 5 }
        }
    }

    @Test func maxPlayersChangedClampsAdminUpperOverflow() async {
        var state = makeState()
        state.isAdminCreation = true
        let store = makeStore(state: state)
        await store.send(.maxPlayersChanged(9999)) {
            $0.$game.withLock { $0.maxPlayers = 500 }
        }
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
        store.exhaustivity = .off
        await store.send(.nextTapped)
        #expect(store.state.currentStepIndex == state.steps.count - 1)
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
        store.exhaustivity = .off
        await store.send(.backTapped)
        #expect(store.state.currentStepIndex == 0)
    }

    // MARK: - Power-ups (PP-35 default + filter)

    @Test func powerUpTypeToggledCannotRemoveLastAvailableTypeInFollowTheChicken() async {
        var state = makeState(gameMode: .followTheChicken)
        // PP-35 default ships only `zoneFreeze` + `zonePreview`. Seed
        // every other type ON so the guard below trips on the LAST one.
        state.$game.withLock { game in
            for type in PowerUp.PowerUpType.allCases {
                if !game.powerUps.enabledTypes.contains(type.rawValue) {
                    game.powerUps.enabledTypes.append(type.rawValue)
                }
            }
        }
        let store = makeStore(state: state)
        store.exhaustivity = .off
        #expect(store.state.game.powerUps.enabledTypes.count == PowerUp.PowerUpType.allCases.count)
        let allTypes = PowerUp.PowerUpType.allCases
        for type in allTypes.dropLast() {
            await store.send(.powerUpTypeToggled(type))
        }
        #expect(store.state.game.powerUps.enabledTypes.count == 1)
        // Now try to remove the last one — guard blocks.
        if let last = allTypes.last {
            await store.send(.powerUpTypeToggled(last))
        }
        #expect(store.state.game.powerUps.enabledTypes.count == 1)
    }

    @Test func powerUpTypeToggledCanRemoveUnavailableTypeInStayInTheZone() async {
        let state = makeState(gameMode: .stayInTheZone)
        // INVISIBILITY ships enabled by default. It's unavailable in
        // stayInTheZone, so the guard does not apply — one toggle removes it.
        #expect(state.game.powerUps.enabledTypes.contains(PowerUp.PowerUpType.invisibility.rawValue))
        let store = makeStore(state: state)
        store.exhaustivity = .off
        await store.send(.powerUpTypeToggled(.invisibility))
        #expect(!store.state.game.powerUps.enabledTypes.contains(PowerUp.PowerUpType.invisibility.rawValue))
    }

    // MARK: - Game code

    @Test func gameCodeIsDerivedFromIdPrefix() {
        let state = makeState(gameId: "abcdef-1234-5678")
        #expect(state.game.gameCode == "ABCDEF")
    }
}
