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
        case chickenMap(ChickenMapFeature.State)
        case hunterMap(HunterMapFeature.State)
        case selection(SelectionFeature.State)
    }

    enum Action {
        case chickenMap(ChickenMapFeature.Action)
        case hunterMap(HunterMapFeature.Action)
        case selection(SelectionFeature.Action)
    }

    @Dependency(\.apiClient) var apiClient

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .chickenMap(.goToMenu):
                state = AppFeature.State.selection(SelectionFeature.State())
                return .run { send in
                    try await apiClient.deleteConfig()
                }
            case .selection(.startButtonTapped):
                state = AppFeature.State.hunterMap(HunterMapFeature.State())
                return .none
            case let .selection(.goToChickenMapTriggered(game)):
                state = AppFeature.State.chickenMap(ChickenMapFeature.State(game: game))
                return .none
            case .chickenMap, .hunterMap, .selection:
                return .none
            }
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
    }
}

struct AppView: View {
    let store: StoreOf<AppFeature>

    var body: some View {
        switch store.state {
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



