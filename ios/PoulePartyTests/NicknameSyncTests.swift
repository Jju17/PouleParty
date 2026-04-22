import ComposableArchitecture
import CoreLocation
import Testing
@testable import PouleParty

@MainActor
struct NicknameSyncTests {

    // MARK: - Onboarding saves nickname to Firestore

    @Test func onboardingCompletionSavesNicknameToFirestore() async {
        var savedNickname: String?

        var state = OnboardingFeature.State()
        state.currentPage = OnboardingFeature.totalPages - 1
        state.locationAuthorizationStatus = .authorizedAlways
        state.nickname = "Alice"

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        } withDependencies: {
            $0.analyticsClient = .testValue
            $0.userClient.saveNickname = { nickname in
                savedNickname = nickname
            }
        }
        store.exhaustivity = .off

        await store.send(.nextButtonTapped)
        await store.receive(\.onboardingCompleted)

        #expect(savedNickname == "Alice")
    }

    @Test func onboardingTrimsNicknameBeforeSaving() async {
        var savedNickname: String?

        var state = OnboardingFeature.State()
        state.currentPage = OnboardingFeature.totalPages - 1
        state.locationAuthorizationStatus = .authorizedAlways
        state.nickname = "  Bob  "

        let store = TestStore(initialState: state) {
            OnboardingFeature()
        } withDependencies: {
            $0.analyticsClient = .testValue
            $0.userClient.saveNickname = { nickname in
                savedNickname = nickname
            }
        }
        store.exhaustivity = .off

        await store.send(.nextButtonTapped)
        await store.receive(\.onboardingCompleted)

        #expect(savedNickname == "Bob")
    }

    // MARK: - Settings saves nickname to Firestore

    @Test func settingsNicknameSubmittedSavesToFirestore() async {
        var savedNickname: String?

        let store = TestStore(initialState: SettingsFeature.State()) {
            SettingsFeature()
        } withDependencies: {
            $0.userClient.saveNickname = { nickname in
                savedNickname = nickname
            }
        }
        store.exhaustivity = .off

        await store.send(.nicknameSubmitted("Charlie"))

        #expect(savedNickname == "Charlie")
    }

    @Test func settingsTrimsNicknameBeforeSaving() async {
        var savedNickname: String?

        let store = TestStore(initialState: SettingsFeature.State()) {
            SettingsFeature()
        } withDependencies: {
            $0.userClient.saveNickname = { nickname in
                savedNickname = nickname
            }
        }
        store.exhaustivity = .off

        await store.send(.nicknameSubmitted("  Dana  "))

        #expect(savedNickname == "Dana")
    }

    @Test func settingsProfanityNicknameDoesNotSaveToFirestore() async {
        var saveWasCalled = false

        let store = TestStore(initialState: SettingsFeature.State()) {
            SettingsFeature()
        } withDependencies: {
            $0.userClient.saveNickname = { _ in
                saveWasCalled = true
            }
        }
        store.exhaustivity = .off

        await store.send(.nicknameSubmitted("fuck"))

        #expect(saveWasCalled == false)
    }

    @Test func settingsEmptyNicknameDoesNotSaveToFirestore() async {
        var saveWasCalled = false

        let store = TestStore(initialState: SettingsFeature.State()) {
            SettingsFeature()
        } withDependencies: {
            $0.userClient.saveNickname = { _ in
                saveWasCalled = true
            }
        }
        store.exhaustivity = .off

        await store.send(.nicknameSubmitted(""))

        #expect(saveWasCalled == false)
    }

    @Test func settingsWhitespaceOnlyNicknameDoesNotSaveToFirestore() async {
        var saveWasCalled = false

        let store = TestStore(initialState: SettingsFeature.State()) {
            SettingsFeature()
        } withDependencies: {
            $0.userClient.saveNickname = { _ in
                saveWasCalled = true
            }
        }
        store.exhaustivity = .off

        await store.send(.nicknameSubmitted("   "))

        #expect(saveWasCalled == false)
    }
}
