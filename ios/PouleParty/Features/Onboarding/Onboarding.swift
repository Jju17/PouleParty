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
                // Block on location slide if not at least "when in use"
                if state.currentPage == Self.locationPageIndex {
                    let status = state.locationAuthorizationStatus
                    guard status == .authorizedAlways || status == .authorizedWhenInUse else { return .none }
                }
                // Notification page is non-blocking, always allow next
                // Block on nickname slide if nickname is empty or inappropriate
                if state.currentPage == Self.nicknamePageIndex {
                    let trimmed = state.nickname.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !trimmed.isEmpty else { return .none }
                    if ProfanityFilter.containsProfanity(trimmed) {
                        state.showProfanityAlert = true
                        return .none
                    }
                }

                if state.currentPage < Self.totalPages - 1 {
                    state.currentPage += 1
                } else {
                    // Last page: check location before completing
                    let status = state.locationAuthorizationStatus
                    guard status == .authorizedAlways || status == .authorizedWhenInUse else {
                        state.showLocationAlert = true
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
                let locAuthorized = state.locationAuthorizationStatus == .authorizedAlways ||
                    state.locationAuthorizationStatus == .authorizedWhenInUse
                let nicknameValid = !state.nickname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty

                // Block forward swipe past location page if not authorized
                if page > Self.locationPageIndex && !locAuthorized {
                    state.currentPage = page
                    return .run { send in
                        try? await Task.sleep(for: .milliseconds(50))
                        await send(.pageSnappedBack(Self.locationPageIndex))
                    }
                    .cancellable(id: CancelID.snapBack, cancelInFlight: true)
                }
                // Notification page is non-blocking
                // Block forward swipe past nickname page if empty
                if page > Self.nicknamePageIndex && !nicknameValid {
                    state.currentPage = page
                    return .run { send in
                        try? await Task.sleep(for: .milliseconds(50))
                        await send(.pageSnappedBack(Self.nicknamePageIndex))
                    }
                    .cancellable(id: CancelID.snapBack, cancelInFlight: true)
                }
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
                state.$savedNickname.withLock { $0 = trimmedNickname }
                state.$hasCompletedOnboarding.withLock { $0 = true }
                analyticsClient.onboardingCompleted()
                return .merge(
                    .cancel(id: CancelID.snapBack),
                    .run { [userClient] _ in
                        await userClient.saveNickname(trimmedNickname)
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

                        let isLocationPageBlocked = store.currentPage == 3 && store.locationAuthorizationStatus != .authorizedAlways && store.locationAuthorizationStatus != .authorizedWhenInUse
                        let isNicknamePageEmpty = store.currentPage == 5 && store.nickname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        let isNextDisabled = isLocationPageBlocked || isNicknamePageEmpty
                        Button {
                            store.send(.nextButtonTapped)
                        } label: {
                            BangerText(store.currentPage == OnboardingFeature.totalPages - 1 ? "Let's go!" : "Next", size: 22)
                                .foregroundStyle(.black.opacity(isNextDisabled ? 0.6 : 1.0))
                                .padding(.horizontal, 32)
                                .padding(.vertical, 12)
                                .background(
                                    Capsule()
                                        .fill(isNextDisabled ? AnyShapeStyle(Color.CROrange.opacity(0.4)) : AnyShapeStyle(Color.gradientFire))
                                                                        )
                                .shadow(color: .black.opacity(isNextDisabled ? 0 : 0.2), radius: 4, y: 2)
                        }
                        .disabled(isNextDisabled)
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
