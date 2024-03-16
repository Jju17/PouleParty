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
        case hunter(HunterFeature.State)
        case map(MapFeature.State)
        case selection(SelectionFeature.State)
    }

    enum Action {
        case chickenConfig(ChickenConfigFeature.Action)
        case hunter(HunterFeature.Action)
        case map(MapFeature.Action)
        case selection(SelectionFeature.Action)
    }

    var body: some ReducerOf<Self> {
        Reduce { state, action in
            switch action {
            case .selection(.chickenButtonTapped):
                state = AppFeature.State.chickenConfig(ChickenConfigFeature.State())
                return .none
            case .selection(.hunterButtonTapped):
                state = AppFeature.State.map(MapFeature.State())
                return .none
            case .chickenConfig, .hunter, .map, .selection:
                return .none
            }
        }
        .ifCaseLet(\.chickenConfig, action: \.chickenConfig) {
            ChickenConfigFeature()
        }
        .ifCaseLet(\.hunter, action: \.hunter) {
            HunterFeature()
        }
        .ifCaseLet(\.map, action: \.map) {
            MapFeature()
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
        case .hunter:
            if let store = store.scope(state: \.hunter, action: \.hunter) {
                HunterView(store: store)
            }
        case .map:
            if let store = store.scope(state: \.map, action: \.map) {
                MapView(store: store)
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



