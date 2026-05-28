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
        case gameMasterMap(GameMasterMapFeature.State)
        case home(HomeFeature.State)
        case victory(VictoryFeature.State)
        case demoMode(DemoModeFeature.State)
    }

    enum Action {
        case appStarted
        case newUserSignedIn
        case chickenMap(ChickenMapFeature.Action)
        case hunterMap(HunterMapFeature.Action)
        case gameMasterMap(GameMasterMapFeature.Action)
        case onboarding(OnboardingFeature.Action)
        case home(HomeFeature.Action)
        case victory(VictoryFeature.Action)
        case demoMode(DemoModeFeature.Action)
    }

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.liveActivityClient) var liveActivityClient
    @Dependency(\.userClient) var userClient

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .appStarted:
                return .run { send in
                    await liveActivityClient.cleanupOrphaned()
                    do {
                        let result = try await userClient.signInAnonymously()
                        if result.isNewUser {
                            await send(.newUserSignedIn)
                        }
                        if let token = userClient.fcmToken() {
                            await FCMTokenManager.shared.saveToken(token)
                        }
                    } catch {
                        Logger(category: "AppFeature")
                            .error("Anonymous sign-in failed: \(error.localizedDescription)")
                    }
                }
            case .newUserSignedIn:
                UserDefaults.standard.set(false, forKey: AppConstants.prefOnboardingCompleted)
                state = .onboarding(OnboardingFeature.State())
                return .none
            case .onboarding(.onboardingCompleted):
                state = .home(HomeFeature.State())
                return .none
            case .onboarding:
                return .none
            case .chickenMap(.delegate(.returnedToMenu)):
                state = AppFeature.State.home(HomeFeature.State())
                return .none
            case let .chickenMap(.delegate(.gameEnded(game))):
                state = .victory(VictoryFeature.State(
                    game: game,
                    hunterId: game.chickenId,
                    hunterName: "",
                    isChicken: true
                ))
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
            case let .home(.gameMasterGameStarted(game)):
                state = AppFeature.State.gameMasterMap(GameMasterMapFeature.State(game: game))
                return .none
            case .gameMasterMap(.delegate(.returnedToMenu)):
                state = AppFeature.State.home(HomeFeature.State())
                return .none
            case let .gameMasterMap(.delegate(.gameEnded(game))):
                // The GM is a spectator — empty `hunterId` so the
                // Victory leaderboard doesn't highlight any row.
                state = .victory(VictoryFeature.State(
                    game: game,
                    hunterId: "",
                    hunterName: ""
                ))
                return .none
            case .hunterMap(.delegate(.returnedToMenu)):
                state = AppFeature.State.home(HomeFeature.State())
                return .none
            case let .hunterMap(.delegate(.gameEnded(game))):
                if case let .hunterMap(hunterState) = state {
                    state = .victory(VictoryFeature.State(
                        game: game,
                        hunterId: hunterState.hunterId,
                        hunterName: hunterState.hunterName
                    ))
                }
                return .none
            case .hunterMap(.internal(.winnerRegistered)):
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
            case .home(.demoModeRequested):
                state = .demoMode(DemoModeFeature.State())
                return .none
            case .demoMode(.delegate(.exitDemo)):
                state = .home(HomeFeature.State())
                return .none
            case .chickenMap, .hunterMap, .gameMasterMap, .home, .victory, .demoMode:
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
        .ifCaseLet(\.gameMasterMap, action: \.gameMasterMap) {
            GameMasterMapFeature()
        }
        .ifCaseLet(\.home, action: \.home) {
            HomeFeature()
        }
        .ifCaseLet(\.victory, action: \.victory) {
            VictoryFeature()
        }
        .ifCaseLet(\.demoMode, action: \.demoMode) {
            DemoModeFeature()
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
            case .gameMasterMap:
                if let store = store.scope(state: \.gameMasterMap, action: \.gameMasterMap) {
                    GameMasterMapView(store: store)
                }
            case .home:
                if let store = store.scope(state: \.home, action: \.home) {
                    HomeView(store: store)
                }
            case .victory:
                if let store = store.scope(state: \.victory, action: \.victory) {
                    VictoryView(store: store)
                }
            case .demoMode:
                if let store = store.scope(state: \.demoMode, action: \.demoMode) {
                    DemoModeView(store: store)
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
