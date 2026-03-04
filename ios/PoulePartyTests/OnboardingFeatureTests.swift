//
//  OnboardingFeatureTests.swift
//  PoulePartyTests
//

import ComposableArchitecture
import CoreLocation
import Testing
@testable import PouleParty

@MainActor
struct OnboardingFeatureTests {

    // MARK: - Initial State

    @Test func initialStateHasDefaultValues() {
        let state = OnboardingFeature.State()
        #expect(state.currentPage == 0)
        #expect(state.nickname == "")
        #expect(state.showLocationAlert == false)
        #expect(state.locationAuthorizationStatus == .notDetermined)
    }

    // MARK: - Page Navigation

    @Test func nextButtonIncrementsPage() async {
        let store = TestStore(initialState: OnboardingFeature.State()) {
            OnboardingFeature()
        }

        await store.send(.nextButtonTapped) {
            $0.currentPage = 1
        }
    }

    @Test func nextButtonIncrementsPageMultipleTimes() async {
        let store = TestStore(initialState: OnboardingFeature.State()) {
            OnboardingFeature()
        }

        await store.send(.nextButtonTapped) {
            $0.currentPage = 1
        }
        await store.send(.nextButtonTapped) {
            $0.currentPage = 2
        }
        await store.send(.nextButtonTapped) {
            $0.currentPage = 3
        }
    }

    @Test func backButtonDecrementsPage() async {
        var state = OnboardingFeature.State()
        state.currentPage = 2

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.backButtonTapped) {
            $0.currentPage = 1
        }
    }

    @Test func backButtonDoesNotGoBelowZero() async {
        let store = TestStore(initialState: OnboardingFeature.State()) {
            OnboardingFeature()
        }

        await store.send(.backButtonTapped)
        // State should remain unchanged (currentPage stays 0)
    }

    @Test func pageChangedUpdatesCurrentPage() async {
        var state = OnboardingFeature.State()
        state.locationAuthorizationStatus = .authorizedWhenInUse
        state.nickname = "Player"

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.pageChanged(2)) {
            $0.currentPage = 2
        }
    }

    @Test func pageSnappedBackSetsPage() async {
        var state = OnboardingFeature.State()
        state.currentPage = 4

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.pageSnappedBack(3)) {
            $0.currentPage = 3
        }
    }

    // MARK: - Nickname

    @Test func nicknameChangedUpdatesNickname() async {
        let store = TestStore(initialState: OnboardingFeature.State()) {
            OnboardingFeature()
        }

        await store.send(.nicknameChanged("Alice")) {
            $0.nickname = "Alice"
        }
    }

    @Test func nicknameTruncatedToMaxLength() async {
        let store = TestStore(initialState: OnboardingFeature.State()) {
            OnboardingFeature()
        }

        let longName = String(repeating: "A", count: 30)
        let expectedName = String(repeating: "A", count: OnboardingFeature.nicknameMaxLength)

        await store.send(.nicknameChanged(longName)) {
            $0.nickname = expectedName
        }
    }

    @Test func emptyNicknameBlocksNextOnPage5() async {
        var state = OnboardingFeature.State()
        state.currentPage = 5
        state.nickname = ""
        state.locationAuthorizationStatus = .authorizedWhenInUse

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        // Tapping next with empty nickname should not change state
        await store.send(.nextButtonTapped)
    }

    @Test func whitespaceOnlyNicknameBlocksNextOnPage5() async {
        var state = OnboardingFeature.State()
        state.currentPage = 5
        state.nickname = "   "
        state.locationAuthorizationStatus = .authorizedWhenInUse

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        // Tapping next with whitespace-only nickname should not change state
        await store.send(.nextButtonTapped)
    }

    @Test func validNicknameAllowsNextOnPage5() async {
        var state = OnboardingFeature.State()
        state.currentPage = 5
        state.nickname = "Alice"
        state.locationAuthorizationStatus = .authorizedWhenInUse

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.nextButtonTapped) {
            $0.currentPage = 6
        }
    }

    // MARK: - Location Permission Gates

    @Test func nextButtonBlockedOnPage3WithoutLocationAuth() async {
        var state = OnboardingFeature.State()
        state.currentPage = 3
        state.locationAuthorizationStatus = .notDetermined

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        // Should not advance past location page without authorization
        await store.send(.nextButtonTapped)
    }

    @Test func nextButtonBlockedOnPage3WithDeniedLocation() async {
        var state = OnboardingFeature.State()
        state.currentPage = 3
        state.locationAuthorizationStatus = .denied

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.nextButtonTapped)
    }

    @Test func nextButtonAllowedOnPage3WithWhenInUse() async {
        var state = OnboardingFeature.State()
        state.currentPage = 3
        state.locationAuthorizationStatus = .authorizedWhenInUse

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.nextButtonTapped) {
            $0.currentPage = 4
        }
    }

    @Test func nextButtonAllowedOnPage3WithAlwaysAuth() async {
        var state = OnboardingFeature.State()
        state.currentPage = 3
        state.locationAuthorizationStatus = .authorizedAlways

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.nextButtonTapped) {
            $0.currentPage = 4
        }
    }

    // MARK: - Location Authorization Updates

    @Test func locationAuthorizationUpdatedSetsStatus() async {
        let store = TestStore(initialState: OnboardingFeature.State()) {
            OnboardingFeature()
        }

        await store.send(.locationAuthorizationUpdated(.authorizedWhenInUse)) {
            $0.locationAuthorizationStatus = .authorizedWhenInUse
        }
    }

    @Test func locationAuthorizationUpdatedToDenied() async {
        let store = TestStore(initialState: OnboardingFeature.State()) {
            OnboardingFeature()
        }

        await store.send(.locationAuthorizationUpdated(.denied)) {
            $0.locationAuthorizationStatus = .denied
        }
    }

    @Test func locationAuthorizationUpdatedToAlways() async {
        let store = TestStore(initialState: OnboardingFeature.State()) {
            OnboardingFeature()
        }

        await store.send(.locationAuthorizationUpdated(.authorizedAlways)) {
            $0.locationAuthorizationStatus = .authorizedAlways
        }
    }

    // MARK: - Location Alert

    @Test func locationAlertDismissedHidesAlert() async {
        var state = OnboardingFeature.State()
        state.showLocationAlert = true

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.locationAlertDismissed) {
            $0.showLocationAlert = false
        }
    }

    @Test func lastPageNextWithoutLocationShowsAlert() async {
        var state = OnboardingFeature.State()
        state.currentPage = OnboardingFeature.totalPages - 1
        state.locationAuthorizationStatus = .denied

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.nextButtonTapped) {
            $0.showLocationAlert = true
        }
    }

    // MARK: - Onboarding Completion

    @Test func lastPageNextWithLocationTriggersCompletion() async {
        var state = OnboardingFeature.State()
        state.currentPage = OnboardingFeature.totalPages - 1
        state.locationAuthorizationStatus = .authorizedWhenInUse
        state.nickname = "Alice"

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }
        store.exhaustivity = .off

        await store.send(.nextButtonTapped)
        await store.receive(\.onboardingCompleted)
    }

    // MARK: - Page Swipe Blocking

    @Test func pageChangedBlocksForwardSwipePastLocationPage() async {
        var state = OnboardingFeature.State()
        state.currentPage = 3
        state.locationAuthorizationStatus = .notDetermined

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }
        store.exhaustivity = .off

        // Swiping forward to page 4 without location should snap back
        await store.send(.pageChanged(4)) {
            $0.currentPage = 4
        }
        await store.receive(\.pageSnappedBack) {
            $0.currentPage = 3
        }
    }

    @Test func pageChangedBlocksForwardSwipePastNicknamePage() async {
        var state = OnboardingFeature.State()
        state.currentPage = 5
        state.locationAuthorizationStatus = .authorizedWhenInUse
        state.nickname = ""

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }
        store.exhaustivity = .off

        // Swiping forward to page 6 without nickname should snap back
        await store.send(.pageChanged(6)) {
            $0.currentPage = 6
        }
        await store.receive(\.pageSnappedBack) {
            $0.currentPage = 5
        }
    }

    @Test func pageChangedAllowsForwardSwipeWhenAuthorizedAndNamed() async {
        var state = OnboardingFeature.State()
        state.currentPage = 3
        state.locationAuthorizationStatus = .authorizedWhenInUse
        state.nickname = "Alice"

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.pageChanged(6)) {
            $0.currentPage = 6
        }
    }
}
