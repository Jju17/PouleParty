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
    }

    enum Action {
        case nextButtonTapped
        case backButtonTapped
        case pageChanged(Int)
        case requestWhenInUsePermission
        case requestAlwaysPermission
        case locationAuthorizationUpdated(CLAuthorizationStatus)
        case onboardingCompleted
        case onTask
    }

    static let totalPages = 5

    @Dependency(\.locationClient) var locationClient

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .onTask:
                state.locationAuthorizationStatus = locationClient.authorizationStatus()
                return .none
            case .nextButtonTapped:
                if state.currentPage < Self.totalPages - 1 {
                    state.currentPage += 1
                } else {
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
            case let .pageChanged(page):
                state.currentPage = page
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
                OnboardingSlide1()
                    .tag(0)

                OnboardingSlide2()
                    .tag(1)

                OnboardingSlide3()
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

                OnboardingReadySlide()
                    .tag(4)
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .animation(.easeInOut(duration: 0.3), value: store.currentPage)

            // Navigation overlay
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
                            }
                        }

                        Spacer()

                        Button {
                            store.send(.nextButtonTapped)
                        } label: {
                            Text(store.currentPage == OnboardingFeature.totalPages - 1 ? "Let's go!" : "Next")
                                .font(.banger(size: 22))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 32)
                                .padding(.vertical, 12)
                                .background(
                                    Capsule()
                                        .fill(Color.CROrange)
                                )
                        }
                    }
                    .padding(.horizontal, 30)
                }
                .padding(.bottom, 40)
            }
        }
        .task {
            store.send(.onTask)
        }
    }
}

// MARK: - Slide 1: Welcome

private struct OnboardingSlide1: View {
    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image("logo")
                .resizable()
                .scaledToFit()
                .frame(width: 160, height: 160)

            Text("Welcome to\nPouleParty!")
                .font(.banger(size: 36))
                .multilineTextAlignment(.center)
                .foregroundStyle(.black)

            Text("The ultimate hide-and-seek\npub crawl game")
                .font(.banger(size: 18))
                .multilineTextAlignment(.center)
                .foregroundStyle(.black.opacity(0.6))

            Spacer()
            Spacer()
        }
        .padding(.horizontal, 40)
    }
}

// MARK: - Slide 2: Nominate a Chicken

private struct OnboardingSlide2: View {
    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Text("🐔")
                .font(.system(size: 80))

            Text("Nominate a Chicken")
                .font(.banger(size: 32))
                .multilineTextAlignment(.center)
                .foregroundStyle(.black)

            Text("Pick the Stag, the Birthday Girl, or whoever just really wants to be a Chicken.\n\nTheir job is to hide.")
                .font(.banger(size: 18))
                .multilineTextAlignment(.center)
                .foregroundStyle(.black.opacity(0.6))
                .padding(.horizontal, 10)

            Spacer()
            Spacer()
        }
        .padding(.horizontal, 40)
    }
}

// MARK: - Slide 3: Hunt Them Down

private struct OnboardingSlide3: View {
    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Text("🗺️")
                .font(.system(size: 80))

            Text("Hunt Them Down")
                .font(.banger(size: 32))
                .multilineTextAlignment(.center)
                .foregroundStyle(.black)

            Text("Split into squads. Use the map to track them.\n\nThe Chicken could be hiding in any pub or bar.")
                .font(.banger(size: 18))
                .multilineTextAlignment(.center)
                .foregroundStyle(.black.opacity(0.6))
                .padding(.horizontal, 10)

            Spacer()
            Spacer()
        }
        .padding(.horizontal, 40)
    }
}

// MARK: - Slide 4: Location Permission

private struct OnboardingLocationSlide: View {
    let status: CLAuthorizationStatus
    let onRequestWhenInUse: () -> Void
    let onRequestAlways: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Text(status == .authorizedAlways ? "📍" : (status == .authorizedWhenInUse ? "👀" : "😢"))
                .font(.system(size: 80))

            Text("We Need Your Location")
                .font(.banger(size: 32))
                .multilineTextAlignment(.center)
                .foregroundStyle(.black)

            switch status {
            case .authorizedAlways:
                Text("You're all set! The game will track location even in the background.\n\nMaximum fun guaranteed!")
                    .font(.banger(size: 18))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.black.opacity(0.6))
                    .padding(.horizontal, 10)

                HStack(spacing: 8) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                        .font(.system(size: 24))
                    Text("Always allowed!")
                        .font(.banger(size: 20))
                        .foregroundStyle(.green)
                }
                .padding(.top, 8)

            case .authorizedWhenInUse:
                Text("Almost there! The game needs to track the Chicken even when the app is in the background.\n\nPlease select \"Always Allow\" so the Hunters can find you!")
                    .font(.banger(size: 18))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.black.opacity(0.6))
                    .padding(.horizontal, 10)

                Button {
                    onRequestAlways()
                } label: {
                    Text("Allow Always")
                        .font(.banger(size: 20))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 28)
                        .padding(.vertical, 14)
                        .background(
                            Capsule()
                                .fill(Color.CROrange)
                        )
                }
                .padding(.top, 8)

            case .denied, .restricted:
                Text("Location access was denied.\nPlease enable it in Settings to play.")
                    .font(.banger(size: 18))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.black.opacity(0.6))
                    .padding(.horizontal, 10)

                Button {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    Text("Open Settings")
                        .font(.banger(size: 18))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 10)
                        .background(
                            Capsule()
                                .fill(Color.CROrange)
                        )
                }
                .padding(.top, 8)

            default:
                Text("Without it, the game can't work.\nNo location = no map = no fun.\n\nWe promise we only use it during the game!")
                    .font(.banger(size: 18))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.black.opacity(0.6))
                    .padding(.horizontal, 10)

                Button {
                    onRequestWhenInUse()
                } label: {
                    Text("Allow Location Access")
                        .font(.banger(size: 20))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 28)
                        .padding(.vertical, 14)
                        .background(
                            Capsule()
                                .fill(Color.CROrange)
                        )
                }
                .padding(.top, 8)
            }

            Spacer()
            Spacer()
        }
        .padding(.horizontal, 40)
    }
}

// MARK: - Slide 5: Ready (placeholder for future step)

private struct OnboardingReadySlide: View {
    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Text("🎉")
                .font(.system(size: 80))

            Text("The Endgame")
                .font(.banger(size: 32))
                .multilineTextAlignment(.center)
                .foregroundStyle(.black)

            Text("Close in on the Chicken. Complete challenges for points. Unleash weapons.\n\nIt's Mario Kart rules — anything goes.")
                .font(.banger(size: 18))
                .multilineTextAlignment(.center)
                .foregroundStyle(.black.opacity(0.6))
                .padding(.horizontal, 10)

            Spacer()
            Spacer()
        }
        .padding(.horizontal, 40)
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
