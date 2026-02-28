//
//  SelectionFeatureTests.swift
//  PoulePartyTests
//

import ComposableArchitecture
import CoreLocation
import Foundation
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

    @Test func validatePasswordWithoutLocationShowsAlert() async {
        var state = SelectionFeature.State()
        state.isAuthenticating = true

        let store = TestStore(initialState: state) {
            SelectionFeature()
        } withDependencies: {
            $0.locationClient.authorizationStatus = { .denied }
        }

        await store.send(.validatePasswordButtonTapped) {
            $0.isAuthenticating = false
            $0.password = ""
        }
        await store.receive(\.locationRequired) {
            $0.destination = .alert(
                AlertState {
                    TextState("Location Required")
                } actions: {
                    ButtonState(role: .cancel) {
                        TextState("OK")
                    }
                } message: {
                    TextState("Location is the core of PouleParty! Your position is anonymous and only used during the game. Please enable location access to continue.")
                }
            )
        }
    }

    @Test func initialStateHasDefaultValues() {
        let state = SelectionFeature.State()
        #expect(state.gameCode == "")
        #expect(state.isJoiningGame == false)
        #expect(state.destination == nil)
    }

    @Test func initialStateHasEmptyHunterName() {
        let state = SelectionFeature.State()
        #expect(state.hunterName == "")
    }
}
