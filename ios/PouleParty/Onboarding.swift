//
//  Onboarding.swift
//  PouleParty
//
//  Created by Julien Rahier on 14/02/2026.
//

import ComposableArchitecture
import CoreLocation
import SwiftUI

@Reducer
struct OnboardingFeature {

    @ObservableState
    struct State: Equatable {
        var currentPage: Int = 0
        var locationAuthorizationStatus: CLAuthorizationStatus = .notDetermined
        var nickname: String = ""
        var showLocationAlert: Bool = false
    }

    enum Action {
        case nextButtonTapped
        case backButtonTapped
        case pageChanged(Int)
        case nicknameChanged(String)
        case requestWhenInUsePermission
        case requestAlwaysPermission
        case locationAuthorizationUpdated(CLAuthorizationStatus)
        case dismissLocationAlert
        case onboardingCompleted
        case onTask
        case snapBackToPage(Int)
    }

    static let totalPages = 6
    static let nicknameMaxLength = 20

    @Dependency(\.locationClient) var locationClient

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .onTask:
                state.locationAuthorizationStatus = locationClient.authorizationStatus()
                return .none
            case .nextButtonTapped:
                // Block on location slide if not at least "when in use"
                if state.currentPage == 3 {
                    let status = state.locationAuthorizationStatus
                    guard status == .authorizedAlways || status == .authorizedWhenInUse else { return .none }
                }
                // Block on nickname slide if nickname is empty
                if state.currentPage == 4 {
                    let trimmed = state.nickname.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !trimmed.isEmpty else { return .none }
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
                    return .run { send in
                        await send(.onboardingCompleted)
                    }
                }
                return .none
            case .backButtonTapped:
                if state.currentPage > 0 {
                    state.currentPage -= 1
                }
                return .none
            case let .nicknameChanged(name):
                state.nickname = String(name.prefix(Self.nicknameMaxLength))
                return .none
            case let .pageChanged(page):
                let locAuthorized = state.locationAuthorizationStatus == .authorizedAlways ||
                    state.locationAuthorizationStatus == .authorizedWhenInUse
                let nicknameValid = !state.nickname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty

                // Block forward swipe past location page if not authorized
                if page > 3 && !locAuthorized {
                    state.currentPage = page
                    return .run { send in
                        try? await Task.sleep(for: .milliseconds(50))
                        await send(.snapBackToPage(3))
                    }
                }
                // Block forward swipe past nickname page if empty
                if page > 4 && !nicknameValid {
                    state.currentPage = page
                    return .run { send in
                        try? await Task.sleep(for: .milliseconds(50))
                        await send(.snapBackToPage(4))
                    }
                }
                state.currentPage = page
                return .none
            case let .snapBackToPage(page):
                state.currentPage = page
                return .none
            case .dismissLocationAlert:
                state.showLocationAlert = false
                return .none
            case .requestWhenInUsePermission:
                return .run { send in
                    await locationClient.requestWhenInUse()
                    let status = locationClient.authorizationStatus()
                    await send(.locationAuthorizationUpdated(status))
                }
            case .requestAlwaysPermission:
                return .run { send in
                    await locationClient.requestAlways()
                    let status = locationClient.authorizationStatus()
                    await send(.locationAuthorizationUpdated(status))
                }
            case let .locationAuthorizationUpdated(status):
                state.locationAuthorizationStatus = status
                return .none
            case .onboardingCompleted:
                return .none
            }
        }
    }
}

// MARK: - View

struct OnboardingView: View {
    let store: StoreOf<OnboardingFeature>

    var body: some View {
        ZStack {
            Color.CRBeige.ignoresSafeArea()

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
                        store.send(.requestWhenInUsePermission)
                    },
                    onRequestAlways: {
                        store.send(.requestAlwaysPermission)
                    }
                )
                .tag(3)

                OnboardingNicknameSlide(
                    nickname: Binding(
                        get: { store.nickname },
                        set: { store.send(.nicknameChanged($0)) }
                    ),
                    maxLength: OnboardingFeature.nicknameMaxLength
                )
                .tag(4)

                OnboardingReadySlide()
                    .tag(5)
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
                                .fill(index == store.currentPage ? Color.black : Color.black.opacity(0.2))
                                .frame(width: 8, height: 8)
                        }
                    }

                    // Buttons
                    HStack {
                        if store.currentPage > 0 {
                            Button {
                                store.send(.backButtonTapped)
                            } label: {
                                Text("Back")
                                    .font(.banger(size: 18))
                                    .foregroundStyle(.black.opacity(0.5))
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 12)
                                    .contentShape(Rectangle())
                            }
                        }

                        Spacer()

                        let isLocationPageBlocked = store.currentPage == 3 && store.locationAuthorizationStatus != .authorizedAlways && store.locationAuthorizationStatus != .authorizedWhenInUse
                        let isNicknamePageEmpty = store.currentPage == 4 && store.nickname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        let isNextDisabled = isLocationPageBlocked || isNicknamePageEmpty
                        Button {
                            store.send(.nextButtonTapped)
                        } label: {
                            Text(store.currentPage == OnboardingFeature.totalPages - 1 ? "Let's go!" : "Next")
                                .font(.banger(size: 22))
                                .foregroundStyle(.white.opacity(isNextDisabled ? 0.6 : 1.0))
                                .padding(.horizontal, 32)
                                .padding(.vertical, 12)
                                .background(
                                    Capsule()
                                        .fill(Color.CROrange.opacity(isNextDisabled ? 0.4 : 1.0))
                                )
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
        .alert("Location Required", isPresented: Binding(
            get: { store.showLocationAlert },
            set: { _ in store.send(.dismissLocationAlert) }
        )) {
            Button("OK") {
                store.send(.dismissLocationAlert)
            }
        } message: {
            Text("Location is the core of PouleParty! Your position is anonymous and only used during the game. Please enable location access to continue.")
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
