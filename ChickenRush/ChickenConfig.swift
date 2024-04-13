//
//  ChickenConfig.swift
//  ChickenRush
//
//  Created by Julien Rahier on 15/03/2024.
//

import ComposableArchitecture
import CoreLocation
import FirebaseFirestore
import SwiftUI

@Reducer
struct ChickenConfigFeature {

    @ObservableState
    struct State {
        @Shared var game: Game
        var manager = LocationManager(isTrackingActive: false, updatingMethod: .alwaysUpdating)
        var path = StackState<ChickenMapConfigFeature.State>()
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case goBackButtonTriggered
        case path(StackAction<ChickenMapConfigFeature.State, ChickenMapConfigFeature.Action>)
        case startGameButtonTapped
        case startGameTriggered(Game)
    }

    @Dependency(\.apiClient) var apiClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case .goBackButtonTriggered:
                return .none
            case .path:
                return .none
            case .startGameButtonTapped:
                return .run { [state = state] send in
                    do {
                        try apiClient.setConfig(state.game)
                        await send(.startGameTriggered(state.game))
                    } catch {
                        print("Error adding document: \(error)")
                    }
                }
            case .startGameTriggered:
                return .none

            }
        }
        .forEach(\.path, action: \.path) {
            ChickenMapConfigFeature()
        }
    }
}


struct ChickenConfigView: View {
    @Bindable var store: StoreOf<ChickenConfigFeature>

    var body: some View {
        NavigationStack(path: $store.scope(state: \.path, action: \.path)) {
            Form {
                DatePicker(selection: $store.game.startDate, in: .now.addingTimeInterval(60)...){
                    Text("Start at")
                }
                .datePickerStyle(.compact)
                DatePicker(selection: $store.game.endDate, in: store.game.startDate.addingTimeInterval(300)..., displayedComponents: .hourAndMinute){
                    Text("End at")
                }
                .datePickerStyle(.compact)
                NavigationLink(state: ChickenMapConfigFeature.State(game: store.$game)) {
                    Text("Map setup")
                }
                VStack(alignment: .leading) {
                    HStack {
                        Text("Radius interval update")
                        Spacer()
                        Text("\(Int(self.store.game.radiusIntervalUpdate)) minutes")
                    }
                    Slider(value: self.$store.game.radiusIntervalUpdate, in: 1...60, step: 1)
                }
                VStack(alignment: .leading) {
                    HStack {
                        Text("Radius decline")
                        Spacer()
                        Text("\(Int(self.store.game.radiusDeclinePerUpdate)) meters")
                    }
                    Slider(value: self.$store.game.radiusDeclinePerUpdate, in: 50...1000, step: 10)
                }
                Button("Start game") {
                    store.send(.startGameButtonTapped)
                }
            }
            .navigationTitle("Game Settings")
        } destination: { store in
            ChickenMapConfigView(store: store)
        }
    }
}

#Preview {
    ChickenConfigView(store: Store(initialState: ChickenConfigFeature.State(game: Shared(Game.mock))) {
        ChickenConfigFeature()
    })
}
