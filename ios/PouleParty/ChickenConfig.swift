//
//  ChickenConfig.swift
//  PouleParty
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
    struct State: Equatable {
        @Presents var destination: Destination.State?
        @Shared var game: Game
        var path = StackState<ChickenMapConfigFeature.State>()
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case configSaveFailed
        case destination(PresentationAction<Destination.Action>)
        case goBackButtonTriggered
        case path(StackAction<ChickenMapConfigFeature.State, ChickenMapConfigFeature.Action>)
        case startGameButtonTapped
        case startGameTriggered(Game)
    }

    @Reducer
    struct Destination {
        @ObservableState
        enum State: Equatable {
            case alert(AlertState<Action.Alert>)
        }

        enum Action {
            case alert(Alert)

            enum Alert: Equatable { }
        }
    }

    @Dependency(\.apiClient) var apiClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case .configSaveFailed:
                state.destination = .alert(
                    AlertState {
                        TextState("Error")
                    } actions: {
                        ButtonState(role: .cancel) {
                            TextState("OK")
                        }
                    } message: {
                        TextState("Could not create the game. Please check your connection and try again.")
                    }
                )
                return .none
            case .destination:
                return .none
            case .goBackButtonTriggered:
                return .none
            case .path:
                return .none
            case .startGameButtonTapped:
                // Auto-calculate endDate from radius parameters
                state.$game.withLock { game in
                    let shrinks = ceil(game.initialRadius / game.radiusDeclinePerUpdate)
                    let duration = shrinks * game.radiusIntervalUpdate * 60
                    game.endDate = game.hunterStartDate.addingTimeInterval(duration)
                }
                return .run { [state = state] send in
                    do {
                        try await apiClient.setConfig(state.game)
                        await send(.startGameTriggered(state.game))
                    } catch {
                        await send(.configSaveFailed)
                    }
                }
            case .startGameTriggered:
                return .none
            }
        }
        .ifLet(\.$destination, action: \.destination) {
            Destination()
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
                gameCodeSection
                dateSection
                gameSettingsSection
            }
            .safeAreaInset(edge: .bottom) {
                Button {
                    store.send(.startGameButtonTapped)
                } label: {
                    Text("Start game")
                        .font(.banger(size: 24))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.CROrange)
                        .cornerRadius(12)
                }
                .padding(.horizontal)
                .padding(.bottom, 8)
                .background(.thinMaterial)
            }
            .navigationTitle("Game Settings")
        } destination: { store in
            ChickenMapConfigView(store: store)
        }
        .alert(
            $store.scope(
                state: \.destination?.alert,
                action: \.destination.alert
            )
        )
    }

    private var gameCodeSection: some View {
        Section("Game Code") {
            GameCodeRow(gameCode: store.game.gameCode)
        }
    }

    private var dateSection: some View {
        DatePicker(selection: $store.game.startDate, in: .now.addingTimeInterval(120)...) {
            Text("Start at")
        }
        .datePickerStyle(.compact)
    }

    private var gameSettingsSection: some View {
        Group {
            Picker("Game Mode", selection: $store.game.gameMod) {
                ForEach(Game.GameMod.allCases, id: \.self) { mode in
                    Text(mode.title).tag(mode)
                }
            }
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
            VStack(alignment: .leading) {
                HStack {
                    Text("Chicken head start")
                    Spacer()
                    Text("\(Int(self.store.game.chickenHeadStartMinutes)) minutes")
                }
                Slider(value: self.$store.game.chickenHeadStartMinutes, in: 0...45, step: 1)
            }
        }
    }
}

#Preview {
    ChickenConfigView(store: Store(initialState: ChickenConfigFeature.State(game: Shared(value: Game.mock))) {
        ChickenConfigFeature()
    })
}
