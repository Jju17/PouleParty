//
//  SelectionFeatureTests.swift
//  PoulePartyTests
//

import ComposableArchitecture
import Testing
@testable import PouleParty

@MainActor
struct SelectionFeatureTests {

    @Test func startButtonTappedShowsJoinDialog() async {
        let store = TestStore(initialState: SelectionFeature.State()) {
            SelectionFeature()
        }

        await store.send(.startButtonTapped) {
            $0.isJoiningGame = true
        }
    }

    @Test func wrongPasswordClearsField() async {
        var state = SelectionFeature.State()
        state.password = "wrongpassword"

        let store = TestStore(initialState: state) {
            SelectionFeature()
        }

        await store.send(.validatePasswordButtonTapped) {
            $0.password = ""
        }
    }

    @Test func initialStateHasDefaultValues() {
        let state = SelectionFeature.State()
        #expect(state.password == "")
        #expect(state.gameCode == "")
        #expect(state.isAuthenticating == false)
        #expect(state.isJoiningGame == false)
        #expect(state.destination == nil)
    }

    @Test func initialStateHasEmptyHunterName() {
        let state = SelectionFeature.State()
        #expect(state.hunterName == "")
    }
}
