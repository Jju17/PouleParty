//
//  Onboarding.swift
//  PouleParty
//
//  Created by Julien Rahier on 14/02/2026.
//

import ComposableArchitecture
import CoreLocation
import Sharing
import SwiftUI
import UserNotifications

@Reducer
struct OnboardingFeature {

    @ObservableState
    struct State: Equatable {
        @Shared(.appStorage(AppConstants.prefOnboardingCompleted)) var hasCompletedOnboarding = false
        @Shared(.appStorage(AppConstants.prefUserNickname)) var savedNickname = ""
        var currentPage: Int = 0
        var locationAuthorizationStatus: CLAuthorizationStatus = .notDetermined
        var notificationAuthorizationStatus: UNAuthorizationStatus = .notDetermined
        var nickname: String = ""
        var showLocationAlert: Bool = false
        var showProfanityAlert: Bool = false
    }

    enum Action {
        case alwaysPermissionButtonTapped
        case backButtonTapped
        case locationAlertDismissed
        case locationAuthorizationUpdated(CLAuthorizationStatus)
        case nextButtonTapped
        case nicknameChanged(String)
        case notificationAuthorizationUpdated(UNAuthorizationStatus)
        case notificationPermissionButtonTapped
        case onboardingCompleted
        case onTask
        case pageChanged(Int)
        case pageSnappedBack(Int)
        case profanityAlertDismissed
        case whenInUsePermissionButtonTapped
    }

    static let totalPages = 7
    static let locationPageIndex = 3
    static let notificationPageIndex = 4
    static let nicknamePageIndex = 5
    enum CancelID { case snapBack }

    @Dependency(\.locationClient) var locationClient
    @Dependency(\.notificationClient) var notificationClient
    @Dependency(\.analyticsClient) var analyticsClient
    @Dependency(\.userClient) var userClient

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .onTask:
                state.locationAuthorizationStatus = locationClient.authorizationStatus()
                return .run { send in
                    let status = await notificationClient.authorizationStatus()
                    await send(.notificationAuthorizationUpdated(status))
                }
            case .nextButtonTapped:
                // Apple 5.1.5: every slide is skippable. Location and
                // nickname are no longer gates — location is requested
                // contextually at Create / Join / Start, and an empty
                // nickname is auto-generated in `.onboardingCompleted`.
                // The only remaining gate is profanity: if the user typed
                // something inappropriate, block until they fix it.
                if state.currentPage == Self.nicknamePageIndex {
                    let trimmed = state.nickname.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty && ProfanityFilter.containsProfanity(trimmed) {
                        state.showProfanityAlert = true
                        return .none
                    }
                }

                if state.currentPage < Self.totalPages - 1 {
                    state.currentPage += 1
                } else {
                    let trimmed = state.nickname.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty && ProfanityFilter.containsProfanity(trimmed) {
                        state.showProfanityAlert = true
                        return .none
                    }
                    return .send(.onboardingCompleted)
                }
                return .none
            case .backButtonTapped:
                if state.currentPage > 0 {
                    state.currentPage -= 1
                }
                return .none
            case let .nicknameChanged(name):
                state.nickname = String(name.prefix(AppConstants.nicknameMaxLength))
                return .none
            case let .pageChanged(page):
                // Apple 5.1.5: pager swipes are unblocked alongside the
                // Next button. Location and nickname are deferred (see
                // `.nextButtonTapped`).
                state.currentPage = page
                return .none
            case let .pageSnappedBack(page):
                state.currentPage = page
                return .none
            case .locationAlertDismissed:
                state.showLocationAlert = false
                return .none
            case .profanityAlertDismissed:
                state.showProfanityAlert = false
                return .none
            case .whenInUsePermissionButtonTapped:
                return .run { send in
                    await locationClient.requestWhenInUse()
                    let status = locationClient.authorizationStatus()
                    await send(.locationAuthorizationUpdated(status))
                }
            case .alwaysPermissionButtonTapped:
                return .run { send in
                    await locationClient.requestAlways()
                    let status = locationClient.authorizationStatus()
                    await send(.locationAuthorizationUpdated(status))
                }
            case .notificationPermissionButtonTapped:
                return .run { send in
                    let status = await notificationClient.requestAuthorization()
                    await send(.notificationAuthorizationUpdated(status))
                }
            case let .locationAuthorizationUpdated(status):
                state.locationAuthorizationStatus = status
                return .none
            case let .notificationAuthorizationUpdated(status):
                state.notificationAuthorizationStatus = status
                return .none
            case .onboardingCompleted:
                let trimmedNickname = state.nickname.trimmingCharacters(in: .whitespacesAndNewlines)
                // Apple 5.1.5: nickname is skippable; assign a random
                // `AdjectiveNoun##` pseudonym so the player always has a
                // teamName by the time they hit Home.
                let finalNickname = trimmedNickname.isEmpty ? RandomNickname.generate() : trimmedNickname
                state.nickname = finalNickname
                state.$savedNickname.withLock { $0 = finalNickname }
                state.$hasCompletedOnboarding.withLock { $0 = true }
                analyticsClient.onboardingCompleted()
                return .merge(
                    .cancel(id: CancelID.snapBack),
                    .run { [userClient] _ in
                        await userClient.saveNickname(finalNickname)
                    }
                )
            }
        }
    }
}

// MARK: - View

struct OnboardingView: View {
    let store: StoreOf<OnboardingFeature>
    @FocusState private var isNicknameFocused: Bool

    var body: some View {
        ZStack {
            Color.gradientBackgroundWarmth.ignoresSafeArea()

            TabView(selection: Binding(
                get: { store.currentPage },
                set: { store.send(.pageChanged($0)) }
            )) {
                OnboardingWelcomeSlide()
                    .tag(0)

                OnboardingChickenSlide()
                    .tag(1)

                OnboardingHuntSlide()
                    .tag(2)

                OnboardingLocationSlide(
                    status: store.locationAuthorizationStatus,
                    onRequestWhenInUse: {
                        store.send(.whenInUsePermissionButtonTapped)
                    },
                    onRequestAlways: {
                        store.send(.alwaysPermissionButtonTapped)
                    }
                )
                .tag(3)

                OnboardingNotificationSlide(
                    status: store.notificationAuthorizationStatus,
                    onRequestPermission: {
                        store.send(.notificationPermissionButtonTapped)
                    }
                )
                .tag(4)

                OnboardingNicknameSlide(
                    nickname: Binding(
                        get: { store.nickname },
                        set: { store.send(.nicknameChanged($0)) }
                    ),
                    maxLength: AppConstants.nicknameMaxLength,
                    isFocused: $isNicknameFocused
                )
                .tag(5)

                OnboardingReadySlide()
                    .tag(6)
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .animation(.easeInOut(duration: 0.3), value: store.currentPage)
            .ignoresSafeArea(.keyboard)

            // Navigation overlay — pinned to bottom, ignores keyboard
            VStack {
                Spacer()

                // Page indicator + buttons
                VStack(spacing: 20) {
                    // Page dots
                    HStack(spacing: 8) {
                        ForEach(0..<OnboardingFeature.totalPages, id: \.self) { index in
                            Circle()
                                .fill(index == store.currentPage ? Color.onBackground : Color.onBackground.opacity(0.2))
                                .frame(width: 8, height: 8)
                        }
                    }

                    // Buttons
                    HStack {
                        if store.currentPage > 0 {
                            Button {
                                store.send(.backButtonTapped)
                            } label: {
                                BangerText("Back", size: 18)
                                    .foregroundStyle(Color.onBackground.opacity(0.5))
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 12)
                                    .contentShape(Rectangle())
                            }
                        }

                        Spacer()

                        Button {
                            store.send(.nextButtonTapped)
                        } label: {
                            BangerText(store.currentPage == OnboardingFeature.totalPages - 1 ? "Let's go!" : "Next", size: 22)
                                .foregroundStyle(.white)
                                .padding(.horizontal, 32)
                                .padding(.vertical, 12)
                                .background(
                                    Capsule()
                                        .fill(Color.gradientFire)
                                )
                                .shadow(color: .black.opacity(0.2), radius: 4, y: 2)
                        }
                    }
                    .padding(.horizontal, 30)
                }
                .padding(.bottom, 40)
            }
            .ignoresSafeArea(.keyboard)
        }
        .task {
            store.send(.onTask)
        }
        .onChange(of: store.currentPage) { _, newPage in
            // Flush any pending TextField binding updates before the view can
            // transition away. Leaving focus alive causes lingering
            // `nicknameChanged` actions after AppFeature swaps state to .home,
            // which trips the `ifCaseLet` runtime warning.
            if newPage != OnboardingFeature.nicknamePageIndex {
                isNicknameFocused = false
            }
        }
        .alert("Location Required", isPresented: Binding(
            get: { store.showLocationAlert },
            set: { _ in store.send(.locationAlertDismissed) }
        )) {
            Button("OK") {
                store.send(.locationAlertDismissed)
            }
        } message: {
            Text("Location is the core of PouleParty! Your position is anonymous and only used during the game. Please enable location access to continue.")
        }
        .alert("Inappropriate Nickname", isPresented: Binding(
            get: { store.showProfanityAlert },
            set: { _ in store.send(.profanityAlertDismissed) }
        )) {
            Button("OK") {
                store.send(.profanityAlertDismissed)
            }
        } message: {
            Text("Please choose a different nickname. This one contains inappropriate language.")
        }
    }
}

// MARK: - Preview

#Preview {
    OnboardingView(
        store: Store(
            initialState: OnboardingFeature.State()
        ) {
            OnboardingFeature()
        }
    )
}
