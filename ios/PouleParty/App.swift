//
//  App.swift
//  PouleParty
//
//  Created by Julien Rahier on 15/03/2024.
//

import ComposableArchitecture
import SwiftUI

@Reducer
struct AppFeature {

    @ObservableState
    enum State: Equatable {
        case onboarding(OnboardingFeature.State)
        case chickenMap(ChickenMapFeature.State)
        case hunterMap(HunterMapFeature.State)
        case selection(SelectionFeature.State)
        case victory(VictoryFeature.State)
    }

    enum Action {
        case onboarding(OnboardingFeature.Action)
        case chickenMap(ChickenMapFeature.Action)
        case hunterMap(HunterMapFeature.Action)
        case selection(SelectionFeature.Action)
        case victory(VictoryFeature.Action)
    }

    @Dependency(\.apiClient) var apiClient

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .onboarding(.onboardingCompleted):
                UserDefaults.standard.set(true, forKey: "hasCompletedOnboarding")
                state = .selection(SelectionFeature.State())
                return .none
            case .onboarding:
                return .none
            case .chickenMap(.goToMenu):
                let gameId: String
                if case let .chickenMap(chickenState) = state {
                    gameId = chickenState.game.id
                } else {
                    return .none
                }
                state = AppFeature.State.selection(SelectionFeature.State())
                return .run { _ in
                    try? await apiClient.updateGameStatus(gameId, .done)
                }
            case let .selection(.goToVictoryAsSpectator(game)):
                state = .victory(VictoryFeature.State(
                    game: game,
                    hunterId: "",
                    hunterName: ""
                ))
                return .none
            case let .selection(.goToHunterMapTriggered(game, hunterName)):
                state = AppFeature.State.hunterMap(HunterMapFeature.State(game: game, hunterName: hunterName))
                return .none
            case let .selection(.goToChickenMapTriggered(game)):
                state = AppFeature.State.chickenMap(ChickenMapFeature.State(game: game))
                return .none
            case .hunterMap(.goToMenu):
                state = AppFeature.State.selection(SelectionFeature.State())
                return .none
            case .hunterMap(.goToVictory):
                if case let .hunterMap(hunterState) = state {
                    state = .victory(VictoryFeature.State(
                        game: hunterState.game,
                        hunterId: hunterState.hunterId,
                        hunterName: hunterState.hunterName
                    ))
                }
                return .none
            case .victory(.goToMenu):
                state = AppFeature.State.selection(SelectionFeature.State())
                return .none
            case .chickenMap, .hunterMap, .selection, .victory:
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
        .ifCaseLet(\.selection, action: \.selection) {
            SelectionFeature()
        }
        .ifCaseLet(\.victory, action: \.victory) {
            VictoryFeature()
        }
    }
}

struct AppView: View {
    let store: StoreOf<AppFeature>

    var body: some View {
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
        case .selection:
            if let store = store.scope(state: \.selection, action: \.selection) {
                SelectionView(store: store)
            }
        case .victory:
            if let store = store.scope(state: \.victory, action: \.victory) {
                VictoryView(store: store)
            }
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

#Preview("Selection") {
    AppView(
        store: Store(initialState: AppFeature.State.selection(SelectionFeature.State())) {
            AppFeature()
        }
    )
}
