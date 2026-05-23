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
        state.locationAuthorizationStatus = .authorizedAlways
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
        let expectedName = String(repeating: "A", count: AppConstants.nicknameMaxLength)

        await store.send(.nicknameChanged(longName)) {
            $0.nickname = expectedName
        }
    }

    @Test func emptyNicknameAdvancesOnPage5() async {
        // Apple 5.1.5: nickname is skippable. An empty value just falls
        // through to the next slide; final auto-generation happens at
        // onboarding completion.
        var state = OnboardingFeature.State()
        state.currentPage = 5
        state.nickname = ""

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.nextButtonTapped) {
            $0.currentPage = 6
        }
    }

    @Test func whitespaceOnlyNicknameAdvancesOnPage5() async {
        var state = OnboardingFeature.State()
        state.currentPage = 5
        state.nickname = "   "

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.nextButtonTapped) {
            $0.currentPage = 6
        }
    }

    @Test func profaneNicknameBlocksNextOnPage5() async {
        // The only remaining nickname gate: a manually-typed profane
        // value still triggers the alert. Empty / random / clean names
        // all advance freely.
        var state = OnboardingFeature.State()
        state.currentPage = 5
        state.nickname = "fuck"

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.nextButtonTapped) {
            $0.showProfanityAlert = true
        }
    }

    @Test func validNicknameAllowsNextOnPage5() async {
        var state = OnboardingFeature.State()
        state.currentPage = 5
        state.nickname = "Alice"
        state.locationAuthorizationStatus = .authorizedAlways

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.nextButtonTapped) {
            $0.currentPage = 6
        }
    }

    // MARK: - Location slide is fully skippable (Apple 5.1.5)

    @Test func nextButtonAdvancesOnPage3WithNotDetermined() async {
        // Apple 5.1.5: location is requested contextually at Create /
        // Join / Start, not during onboarding. Every slide is skippable.
        var state = OnboardingFeature.State()
        state.currentPage = 3
        state.locationAuthorizationStatus = .notDetermined

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.nextButtonTapped) {
            $0.currentPage = 4
        }
    }

    @Test func nextButtonAdvancesOnPage3WithDenied() async {
        var state = OnboardingFeature.State()
        state.currentPage = 3
        state.locationAuthorizationStatus = .denied

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.nextButtonTapped) {
            $0.currentPage = 4
        }
    }

    @Test func nextButtonAdvancesOnPage3WithWhenInUse() async {
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

    @Test func nextButtonAdvancesOnPage3WithAlways() async {
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

    // MARK: - Onboarding Completion

    @Test func lastPageNextWithoutLocationCompletesOnboarding() async {
        // Apple 5.1.5: even without any location permission, hitting
        // "Let's go" on the last page completes onboarding. Location is
        // requested contextually at gameplay entry points.
        var state = OnboardingFeature.State()
        state.currentPage = OnboardingFeature.totalPages - 1
        state.locationAuthorizationStatus = .denied
        state.nickname = "Alice"

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }
        store.exhaustivity = .off

        await store.send(.nextButtonTapped)
        await store.receive(\.onboardingCompleted)
    }

    @Test func lastPageNextWithLocationTriggersCompletion() async {
        var state = OnboardingFeature.State()
        state.currentPage = OnboardingFeature.totalPages - 1
        state.locationAuthorizationStatus = .authorizedAlways
        state.nickname = "Alice"

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }
        store.exhaustivity = .off

        await store.send(.nextButtonTapped)
        await store.receive(\.onboardingCompleted)
    }

    @Test func lastPageNextWithEmptyNicknameAutoGenerates() async {
        // Empty nickname triggers `RandomNickname.generate()` in
        // `.onboardingCompleted`. The exact value is non-deterministic
        // (random PRNG), so we just assert completion fired and the
        // saved nickname is non-empty.
        var state = OnboardingFeature.State()
        state.currentPage = OnboardingFeature.totalPages - 1
        state.nickname = ""

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }
        store.exhaustivity = .off

        await store.send(.nextButtonTapped)
        await store.receive(\.onboardingCompleted)
        #expect(!store.state.nickname.isEmpty)
        #expect(!store.state.savedNickname.isEmpty)
    }

    @Test func lastPageNextWithWhitespaceNicknameAutoGenerates() async {
        var state = OnboardingFeature.State()
        state.currentPage = OnboardingFeature.totalPages - 1
        state.nickname = "   "

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }
        store.exhaustivity = .off

        await store.send(.nextButtonTapped)
        await store.receive(\.onboardingCompleted)
        #expect(!store.state.nickname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
    }

    @Test func lastPageNextWithProfaneNicknameShowsProfanityAlert() async {
        // The only remaining final-page gate: profanity. A manually-typed
        // inappropriate value still blocks completion.
        var state = OnboardingFeature.State()
        state.currentPage = OnboardingFeature.totalPages - 1
        state.nickname = "fuck"

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.nextButtonTapped) {
            $0.showProfanityAlert = true
        }
    }

    @Test func lastPageNextWithWhenInUseCompletesOnboarding() async {
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

    // MARK: - Page Swipes (Apple 5.1.5: never blocked)

    @Test func pageChangedAllowsForwardSwipePastLocationPage() async {
        // Pager swipes are unblocked alongside the Next button. Location
        // is requested contextually at gameplay entry points.
        var state = OnboardingFeature.State()
        state.currentPage = 3
        state.locationAuthorizationStatus = .notDetermined

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.pageChanged(4)) {
            $0.currentPage = 4
        }
    }

    @Test func pageChangedAllowsForwardSwipePastNicknamePage() async {
        var state = OnboardingFeature.State()
        state.currentPage = 5
        state.nickname = ""

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.pageChanged(6)) {
            $0.currentPage = 6
        }
    }

    @Test func pageChangedAllowsForwardSwipeWhenAuthorizedAndNamed() async {
        var state = OnboardingFeature.State()
        state.currentPage = 3
        state.locationAuthorizationStatus = .authorizedAlways
        state.nickname = "Alice"

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.pageChanged(6)) {
            $0.currentPage = 6
        }
    }

    @Test func pageChangedAllowsForwardSwipeFromLocationWithWhenInUse() async {
        // Apple 5.1.5: WhenInUse must let the user past the location
        // page via pager swipes too, matching the Next button gate.
        var state = OnboardingFeature.State()
        state.currentPage = 3
        state.locationAuthorizationStatus = .authorizedWhenInUse

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.pageChanged(4)) {
            $0.currentPage = 4
        }
    }

    @Test func pageChangedAllowsForwardSwipeFromLocationWithAlwaysAuth() async {
        var state = OnboardingFeature.State()
        state.currentPage = 3
        state.locationAuthorizationStatus = .authorizedAlways

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        }

        await store.send(.pageChanged(4)) {
            $0.currentPage = 4
        }
    }
}
