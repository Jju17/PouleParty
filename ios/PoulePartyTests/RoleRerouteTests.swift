//
//  RoleRerouteTests.swift
//  PoulePartyTests
//
//  PP-107: when a GameMaster re-designates the chicken mid-`waiting`, both
//  affected players must re-route from the live game-config stream:
//  - the new chicken's hunter map emits `.delegate(.becameChicken)` and the
//    AppFeature swaps the root to `.chickenMap` (with the one-time alert);
//  - the demoted chicken's map emits `.delegate(.becameHunter)` and the
//    AppFeature swaps to `.hunterMap`.
//

import ComposableArchitecture
import Foundation
import Testing
@testable import PouleParty

@MainActor
struct RoleRerouteTests {

    // MARK: - Hunter → Chicken

    @Test func hunterWhoseUidBecomesChickenEmitsBecameChickenDelegate() async {
        var game = Game.mock
        game.id = "reroute-1"
        game.status = .waiting
        game.creatorId = "creator-uid"
        game.setChickenId("creator-uid")
        game.setHunterIds(["my-hunter-id", "other-hunter"])

        var state = HunterMapFeature.State(game: game)
        state.hunterId = "my-hunter-id"

        let store = TestStore(initialState: state) {
            HunterMapFeature()
        }
        store.exhaustivity = .off

        // GameMaster swap: this hunter is now the chicken.
        var swapped = game
        swapped.setChickenId("my-hunter-id")

        await store.send(.internal(.gameConfigUpdated(swapped)))
        await store.receive(\.delegate.becameChicken)
    }

    @Test func appFeatureRoutesBecameChickenToChickenMapWithAlert() async {
        var game = Game.mock
        game.id = "reroute-2"
        game.setChickenId("my-hunter-id")

        let store = TestStore(initialState: AppFeature.State.hunterMap(
            HunterMapFeature.State(game: game)
        )) {
            AppFeature()
        }
        store.exhaustivity = .off

        await store.send(.hunterMap(.delegate(.becameChicken(game))))

        if case let .chickenMap(chickenState) = store.state {
            #expect(chickenState.game.id == "reroute-2")
            #expect(chickenState.newChickenAlert != nil)
        } else {
            Issue.record("Expected `.chickenMap`, got \(store.state)")
        }
    }

    // MARK: - Chicken → Hunter

    @Test func appFeatureRoutesBecameHunterToHunterMap() async {
        var game = Game.mock
        game.id = "reroute-3"
        game.setChickenId("new-chicken")
        game.setHunterIds(["me"])

        let store = TestStore(initialState: AppFeature.State.chickenMap(
            ChickenMapFeature.State(game: game)
        )) {
            AppFeature()
        }
        store.exhaustivity = .off

        await store.send(.chickenMap(.delegate(.becameHunter(game, teamName: "Red Foxes"))))

        if case let .hunterMap(hunterState) = store.state {
            #expect(hunterState.game.id == "reroute-3")
            #expect(hunterState.hunterName == "Red Foxes")
        } else {
            Issue.record("Expected `.hunterMap`, got \(store.state)")
        }
    }
}
