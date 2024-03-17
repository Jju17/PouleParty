//
//  App.swift
//  ChickenRush
//
//  Created by Julien Rahier on 15/03/2024.
//

import ComposableArchitecture
import SwiftUI

@Reducer
struct AppFeature {

    @ObservableState
    enum State {
        case chickenConfig(ChickenConfigFeature.State)
        case chickenMap(ChickenMapFeature.State)
        case hunter(HunterFeature.State)
        case hunterMap(HunterMapFeature.State)
        case selection(SelectionFeature.State)
    }

    enum Action {
        case chickenConfig(ChickenConfigFeature.Action)
        case chickenMap(ChickenMapFeature.Action)
        case goToChickenMapTriggered(Game)
        case goToSettingsTriggered
        case hunter(HunterFeature.Action)
        case hunterMap(HunterMapFeature.Action)
        case selection(SelectionFeature.Action)
    }

    @Dependency(\.apiClient) var apiClient

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case let .chickenConfig(.startGameTriggered(game)):
                state = AppFeature.State.chickenMap(ChickenMapFeature.State(game: game))
                return .none
            case .chickenMap(.stopGameButtonTapped):
                state = AppFeature.State.selection(SelectionFeature.State())
                return .run { send in
                    try await apiClient.deleteConfig()
                }
            case let .goToChickenMapTriggered(game):
                state = AppFeature.State.chickenMap(ChickenMapFeature.State(game: game))
                return .none
            case .goToSettingsTriggered:
                state = AppFeature.State.chickenConfig(ChickenConfigFeature.State())
                return .none
            case .selection(.chickenButtonTapped):
                return .run { send in
                    if let game = await apiClient.getConfig() {
                        await send(.goToChickenMapTriggered(game))
                    } else {
                        await send(.goToSettingsTriggered)
                    }
                }
            case .selection(.hunterButtonTapped):
                state = AppFeature.State.hunterMap(HunterMapFeature.State())
                return .none
            case .chickenConfig, .chickenMap, .hunter, .hunterMap, .selection:
                return .none
            }
        }
        .ifCaseLet(\.chickenConfig, action: \.chickenConfig) {
            ChickenConfigFeature()
        }
        .ifCaseLet(\.chickenMap, action: \.chickenMap) {
            ChickenMapFeature()
        }
        .ifCaseLet(\.hunter, action: \.hunter) {
            HunterFeature()
        }
        .ifCaseLet(\.hunterMap, action: \.hunterMap) {
            HunterMapFeature()
        }
        .ifCaseLet(\.selection, action: \.selection) {
            SelectionFeature()
        }
    }
}

struct AppView: View {
    let store: StoreOf<AppFeature>

    var body: some View {
        switch store.state {
        case .chickenConfig:
            if let store = store.scope(state: \.chickenConfig, action: \.chickenConfig) {
                ChickenConfigView(store: store)
            }
        case .chickenMap:
            if let store = store.scope(state: \.chickenMap, action: \.chickenMap) {
                ChickenMapView(store: store)
            }
        case .hunter:
            if let store = store.scope(state: \.hunter, action: \.hunter) {
                HunterView(store: store)
            }
        case .hunterMap:
            if let store = store.scope(state: \.hunterMap, action: \.hunterMap) {
                HunterMapView(store: store)
            }
        case .selection:
            if let store = store.scope(state: \.selection, action: \.selection) {
                SelectionView(store: store)
            }
        }
    }
}

#Preview {
    AppView(
        store: Store(initialState: AppFeature.State.selection(SelectionFeature.State())) {
            AppFeature()
        }
    )
}



