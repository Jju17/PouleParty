//
//  App.swift
//  PouleParty
//
//  Created by Julien Rahier on 15/03/2024.
//

import ComposableArchitecture
import os
import SwiftUI

@Reducer
struct AppFeature {

    @ObservableState
    enum State: Equatable {
        case onboarding(OnboardingFeature.State)
        case chickenMap(ChickenMapFeature.State)
        case hunterMap(HunterMapFeature.State)
        case home(HomeFeature.State)
        case victory(VictoryFeature.State)
    }

    enum Action {
        case appStarted
        case chickenMap(ChickenMapFeature.Action)
        case hunterMap(HunterMapFeature.Action)
        case onboarding(OnboardingFeature.Action)
        case home(HomeFeature.Action)
        case victory(VictoryFeature.Action)
    }

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.liveActivityClient) var liveActivityClient
    @Dependency(\.userClient) var userClient

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .appStarted:
                return .run { _ in
                    await liveActivityClient.cleanupOrphaned()
                    do {
                        _ = try await userClient.signInAnonymously()
                        if let token = userClient.fcmToken() {
                            await FCMTokenManager.shared.saveToken(token)
                        }
                    } catch {
                        Logger(subsystem: "dev.rahier.pouleparty", category: "AppFeature")
                            .error("Anonymous sign-in failed: \(error.localizedDescription)")
                    }
                }
            case .onboarding(.onboardingCompleted):
                state = .home(HomeFeature.State())
                return .none
            case .onboarding:
                return .none
            case .chickenMap(.returnedToMenu):
                state = AppFeature.State.home(HomeFeature.State())
                return .none
            case .chickenMap(.allHuntersFound):
                if case let .chickenMap(chickenState) = state {
                    state = .victory(VictoryFeature.State(
                        game: chickenState.game,
                        hunterId: "",
                        hunterName: "",
                        isChicken: true
                    ))
                }
                return .none
            case let .home(.completedGameFound(game)):
                state = .victory(VictoryFeature.State(
                    game: game,
                    hunterId: "",
                    hunterName: ""
                ))
                return .none
            case let .home(.hunterGameJoined(game, hunterName)):
                state = AppFeature.State.hunterMap(HunterMapFeature.State(game: game, hunterName: hunterName))
                return .none
            case let .home(.chickenGameStarted(game)):
                state = AppFeature.State.chickenMap(ChickenMapFeature.State(game: game))
                return .none
            case .hunterMap(.returnedToMenu):
                state = AppFeature.State.home(HomeFeature.State())
                return .none
            case .hunterMap(.allHuntersFound):
                if case let .hunterMap(hunterState) = state {
                    state = .victory(VictoryFeature.State(
                        game: hunterState.game,
                        hunterId: hunterState.hunterId,
                        hunterName: hunterState.hunterName
                    ))
                }
                return .none
            case .hunterMap(.winnerRegistered):
                if case let .hunterMap(hunterState) = state {
                    state = .victory(VictoryFeature.State(
                        game: hunterState.game,
                        hunterId: hunterState.hunterId,
                        hunterName: hunterState.hunterName
                    ))
                }
                return .none
            case .victory(.menuButtonTapped):
                state = AppFeature.State.home(HomeFeature.State())
                return .none
            case .home(.accountDeletionCompleted):
                state = .onboarding(OnboardingFeature.State())
                return .none
            case .chickenMap, .hunterMap, .home, .victory:
                return .none
            }
        }
        .ifCaseLet(\.onboarding, action: \.onboarding) {
            OnboardingFeature()
        }
        .ifCaseLet(\.chickenMap, action: \.chickenMap) {
            ChickenMapFeature()
        }
        .ifCaseLet(\.hunterMap, action: \.hunterMap) {
            HunterMapFeature()
        }
        .ifCaseLet(\.home, action: \.home) {
            HomeFeature()
        }
        .ifCaseLet(\.victory, action: \.victory) {
            VictoryFeature()
        }
    }
}

struct AppView: View {
    let store: StoreOf<AppFeature>

    var body: some View {
        Group {
            switch store.state {
            case .onboarding:
                if let store = store.scope(state: \.onboarding, action: \.onboarding) {
                    OnboardingView(store: store)
                }
            case .chickenMap:
                if let store = store.scope(state: \.chickenMap, action: \.chickenMap) {
                    ChickenMapView(store: store)
                }
            case .hunterMap:
                if let store = store.scope(state: \.hunterMap, action: \.hunterMap) {
                    HunterMapView(store: store)
                }
            case .home:
                if let store = store.scope(state: \.home, action: \.home) {
                    HomeView(store: store)
                }
            case .victory:
                if let store = store.scope(state: \.victory, action: \.victory) {
                    VictoryView(store: store)
                }
            }
        }
        .task {
            store.send(.appStarted)
        }
    }
}

#Preview("Onboarding") {
    AppView(
        store: Store(initialState: AppFeature.State.onboarding(OnboardingFeature.State())) {
            AppFeature()
        }
    )
}

#Preview("Home") {
    AppView(
        store: Store(initialState: AppFeature.State.home(HomeFeature.State())) {
            AppFeature()
        }
    )
}
