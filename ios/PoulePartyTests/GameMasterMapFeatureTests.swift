//
//  GameMasterMapFeatureTests.swift
//  PoulePartyTests
//
//  PP-66: Parity tests for the GameMaster role. Covers
//  - routing into `GameMasterMap` from the AppFeature
//  - the `Game.isChicken` model contract used to pick the right map
//  - the teamName-everywhere rule (PP-90 / 2026-05-08): hunter markers
//    on the GameMaster map render `teamName`, never `nickname`
//  - the GameMasterMap reducer's read-only contract (info / drawer
//    toggles, leave game, designate-chicken confirm/cancel/error)
//

import ComposableArchitecture
import CoreLocation
import FirebaseFirestore
import Foundation
import Testing
@testable import PouleParty

@MainActor
struct GameMasterMapFeatureTests {

    // MARK: - Routing (AppFeature)

    @Test func gameMasterGameStartedRoutesToGameMasterMap() async {
        // When HomeFeature emits `.gameMasterGameStarted(game)`, the
        // AppFeature must transition the root state into
        // `.gameMasterMap` — not chickenMap, not hunterMap. This is the
        // routing contract for any user landing in `gameMasterIds`.
        var game = Game.mock
        game.id = "gm-routing-1"
        game.creatorId = "creator-uid"
        game.chickenId = "creator-uid"
        game.gameMasterIds = ["gm-uid"]

        let store = TestStore(initialState: AppFeature.State.home(HomeFeature.State())) {
            AppFeature()
        }
        store.exhaustivity = .off

        await store.send(.home(.gameMasterGameStarted(game)))
        // The state must be `.gameMasterMap` with the same game payload.
        if case let .gameMasterMap(gmState) = store.state {
            #expect(gmState.game.id == "gm-routing-1")
            #expect(gmState.game.gameMasterIds == ["gm-uid"])
        } else {
            Issue.record("Expected state to be `.gameMasterMap`, got \(store.state)")
        }
    }

    @Test func chickenGameStartedRoutesToChickenMapNotGameMasterMap() async {
        // Defensive: the chicken (creator) tapping their own banner
        // must land on the chicken map even if they happen to be in
        // `gameMasterIds` (priority rule per PP-24 / PP-66 spec). The
        // routing decision happens upstream in `findActiveGame` /
        // `JoinFlow`; AppFeature merely honours what Home decided.
        var game = Game.mock
        game.id = "chicken-routing-1"
        game.creatorId = "creator-uid"
        game.chickenId = "creator-uid"
        // Same UID also listed as a GameMaster — should NOT change the
        // routing because Home already picked `.chickenGameStarted`.
        game.gameMasterIds = ["creator-uid"]

        let store = TestStore(initialState: AppFeature.State.home(HomeFeature.State())) {
            AppFeature()
        }
        store.exhaustivity = .off

        await store.send(.home(.chickenGameStarted(game)))
        if case let .chickenMap(chickenState) = store.state {
            #expect(chickenState.game.id == "chicken-routing-1")
        } else {
            Issue.record("Expected state to be `.chickenMap`, got \(store.state)")
        }
    }

    @Test func gameMasterMapReturnedToMenuRoutesHome() async {
        // The "leave game" CTA on GameMasterMap fires the
        // `returnedToMenu` delegate; AppFeature must put us back at
        // Home so the GM banner / "Find game" CTAs become available
        // again.
        var game = Game.mock
        game.id = "gm-back-home"
        let initial = AppFeature.State.gameMasterMap(GameMasterMapFeature.State(game: game))

        let store = TestStore(initialState: initial) {
            AppFeature()
        }
        store.exhaustivity = .off

        await store.send(.gameMasterMap(.delegate(.returnedToMenu)))
        if case .home = store.state {
            // OK
        } else {
            Issue.record("Expected state to be `.home`, got \(store.state)")
        }
    }

    // MARK: - Role model (Game.isChicken)

    @Test func gameIsChickenMatchesChickenId() {
        // The routing decision boils down to `game.isChicken(userId)`.
        // Pinning the contract here so any drift between platforms
        // surfaces at unit-test time.
        var game = Game.mock
        game.creatorId = "creator-uid"
        game.chickenId = "designated-uid"   // PP-26: GM-re-designated chicken
        game.gameMasterIds = ["gm-uid"]
        game.hunterIds = ["hunter-uid"]

        #expect(game.isChicken("designated-uid"))
        #expect(!game.isChicken("creator-uid"))   // Creator stays admin; the chicken is the designated UID.
        #expect(!game.isChicken("gm-uid"))
        #expect(!game.isChicken("hunter-uid"))
        #expect(!game.isChicken(""))               // Empty UID never matches (defensive).
    }

    @Test func gameIsChickenEmptyUidNeverMatches() {
        // An empty chickenId also never matches an empty userId
        // (guards against the both-empty-strings ambiguity that would
        // otherwise route un-authenticated users to the chicken map).
        var game = Game.mock
        game.chickenId = ""
        #expect(!game.isChicken(""))
        #expect(!game.isChicken("anyone"))
    }

    // MARK: - teamName display (PP-90 / 2026-05-08)

    @Test func hunterAnnotationsUseTeamNameWhenRegistrationKnown() async {
        // Hunter markers on the GameMaster map MUST surface the
        // registered `teamName` — never the underlying username /
        // nickname. The reducer rebuilds annotations on every
        // `hunterLocationsUpdated`.
        var game = Game.mock
        game.id = "gm-teamname"
        let store = TestStore(initialState: GameMasterMapFeature.State(game: game)) {
            GameMasterMapFeature()
        }
        store.exhaustivity = .off

        // Seed registrations first so the look-up table is populated.
        let registrations = [
            Registration(userId: "uid-1", teamName: "The Foxes"),
            Registration(userId: "uid-2", teamName: "Les Coyotes")
        ]
        await store.send(.internal(.registrationsLoaded(registrations)))

        let hunters = [
            HunterLocation(hunterId: "uid-1", location: GeoPoint(latitude: 50.0, longitude: 4.0), timestamp: Timestamp(date: .now)),
            HunterLocation(hunterId: "uid-2", location: GeoPoint(latitude: 50.1, longitude: 4.1), timestamp: Timestamp(date: .now))
        ]
        await store.send(.internal(.hunterLocationsUpdated(hunters)))

        let labels = store.state.hunterAnnotations.map(\.displayName)
        #expect(labels.contains("The Foxes"))
        #expect(labels.contains("Les Coyotes"))
        #expect(!labels.contains("Hunter 1"))
        #expect(!labels.contains("Hunter 2"))
    }

    @Test func hunterAnnotationsFallBackToHunterIndexWhenRegistrationMissing() async {
        // A hunter whose registration doc hasn't landed yet must
        // still get a stable index-based label so the marker isn't
        // blank. Sorting is by hunterId, which keeps the assignment
        // deterministic across platforms.
        var game = Game.mock
        game.id = "gm-noreg"
        let store = TestStore(initialState: GameMasterMapFeature.State(game: game)) {
            GameMasterMapFeature()
        }
        store.exhaustivity = .off

        let hunters = [
            HunterLocation(hunterId: "uid-zzz", location: GeoPoint(latitude: 50.0, longitude: 4.0), timestamp: Timestamp(date: .now)),
            HunterLocation(hunterId: "uid-aaa", location: GeoPoint(latitude: 50.1, longitude: 4.1), timestamp: Timestamp(date: .now))
        ]
        await store.send(.internal(.hunterLocationsUpdated(hunters)))

        // Sorted by hunterId: uid-aaa = Hunter 1, uid-zzz = Hunter 2.
        let byId = Dictionary(uniqueKeysWithValues: store.state.hunterAnnotations.map { ($0.id, $0.displayName) })
        #expect(byId["uid-aaa"] == "Hunter 1")
        #expect(byId["uid-zzz"] == "Hunter 2")
    }

    @Test func registrationsArrivingAfterLocationRebuildLabels() async {
        // Late registrations must re-label markers that were
        // initially `Hunter N` placeholders. This is the parity check
        // for the "stream registrations, re-merge with hunterLocations"
        // behaviour both platforms implement.
        var game = Game.mock
        game.id = "gm-late-reg"
        let store = TestStore(initialState: GameMasterMapFeature.State(game: game)) {
            GameMasterMapFeature()
        }
        store.exhaustivity = .off

        // First: hunter location lands with no registration → fallback labels.
        let hunters = [
            HunterLocation(hunterId: "uid-1", location: GeoPoint(latitude: 50.0, longitude: 4.0), timestamp: Timestamp(date: .now))
        ]
        await store.send(.internal(.hunterLocationsUpdated(hunters)))
        #expect(store.state.hunterAnnotations.first?.displayName == "Hunter 1")

        // Then: registration arrives → label flips to teamName.
        let regs = [Registration(userId: "uid-1", teamName: "Apex Predators")]
        await store.send(.internal(.registrationsLoaded(regs)))
        #expect(store.state.hunterAnnotations.first?.displayName == "Apex Predators")
    }

    // MARK: - Read-only reducer surface

    @Test func infoButtonTogglesGameInfo() async {
        let store = TestStore(initialState: GameMasterMapFeature.State(game: .mock)) {
            GameMasterMapFeature()
        }
        store.exhaustivity = .off

        await store.send(.view(.infoButtonTapped)) {
            $0.showGameInfo = true
        }
        await store.send(.view(.gameInfoDismissed)) {
            $0.showGameInfo = false
        }
    }

    @Test func huntersDrawerToggle() async {
        let store = TestStore(initialState: GameMasterMapFeature.State(game: .mock)) {
            GameMasterMapFeature()
        }
        store.exhaustivity = .off

        await store.send(.view(.huntersDrawerTapped)) {
            $0.showHuntersDrawer = true
        }
        await store.send(.view(.huntersDrawerDismissed)) {
            $0.showHuntersDrawer = false
        }
    }

    @Test func leaveGameEmitsReturnedToMenuDelegate() async {
        let store = TestStore(initialState: GameMasterMapFeature.State(game: .mock)) {
            GameMasterMapFeature()
        }
        store.exhaustivity = .off

        await store.send(.view(.leaveGameTapped))
        await store.receive(\.delegate.returnedToMenu)
    }

    // MARK: - Designate chicken (PP-86) — read-only API surface

    @Test func designateHunterTappedSetsPendingRegistration() async {
        let store = TestStore(initialState: GameMasterMapFeature.State(game: .mock)) {
            GameMasterMapFeature()
        }
        store.exhaustivity = .off

        let reg = Registration(userId: "uid-1", teamName: "The Foxes")
        await store.send(.view(.designateHunterTapped(reg))) {
            $0.pendingChickenDesignation = reg
        }
    }

    @Test func designateCancelClearsPendingRegistration() async {
        var state = GameMasterMapFeature.State(game: .mock)
        state.pendingChickenDesignation = Registration(userId: "uid-1", teamName: "Foxes")

        let store = TestStore(initialState: state) {
            GameMasterMapFeature()
        }
        store.exhaustivity = .off

        await store.send(.view(.designateCancelTapped)) {
            $0.pendingChickenDesignation = nil
        }
    }

    @Test func designateConfirmCallsDesignateChickenWithRegistrationUid() async {
        var state = GameMasterMapFeature.State(game: .mock)
        state.game.id = "gm-designate-game"
        let reg = Registration(userId: "new-chicken-uid", teamName: "The Foxes")
        state.pendingChickenDesignation = reg

        let captured = LockIsolated<(String, String)?>(nil)
        let store = TestStore(initialState: state) {
            GameMasterMapFeature()
        } withDependencies: {
            $0.apiClient.designateChicken = { gameId, uid in
                captured.setValue((gameId, uid))
            }
        }
        store.exhaustivity = .off

        await store.send(.view(.designateConfirmTapped)) {
            $0.pendingChickenDesignation = nil
        }
        await store.receive(\.internal.designationSucceeded) {
            $0.showHuntersDrawer = false
        }

        #expect(captured.value?.0 == "gm-designate-game")
        #expect(captured.value?.1 == "new-chicken-uid")
    }

    @Test func designationErrorSurfacesMessageAndIsDismissable() async {
        var state = GameMasterMapFeature.State(game: .mock)
        state.designationError = "Network down"

        let store = TestStore(initialState: state) {
            GameMasterMapFeature()
        }
        store.exhaustivity = .off

        await store.send(.view(.designationErrorDismissed)) {
            $0.designationError = nil
        }
    }

    // MARK: - Read-only stream surface (no power-up tray)

    @Test func gameMasterStateExposesEmptyPowerUpTray() {
        // The GM is a pure observer — every power-up surface in the
        // shared MapUiState protocol must read empty / no-op so any
        // composable that's reused from ChickenMap / HunterMap won't
        // accidentally render an inventory or trigger collection.
        let state = GameMasterMapFeature.State(game: .mock)
        #expect(state.availablePowerUps.isEmpty)
        #expect(state.collectedPowerUps.isEmpty)
        #expect(state.showPowerUpInventory == false)
        #expect(state.powerUpNotification == nil)
        #expect(state.lastActivatedPowerUpType == nil)
        #expect(state.isOutsideZone == false)
    }
}
