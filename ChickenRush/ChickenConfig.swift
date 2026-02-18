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
        enum State {
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
                return .run { [state = state] send in
                    do {
                        try apiClient.setConfig(state.game)
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
    @State private var codeCopied = false

    var body: some View {
        NavigationStack(path: $store.scope(state: \.path, action: \.path)) {
            Form {
                gameCodeSection
                dateSection
                gameSettingsSection
                Button("Start game") {
                    store.send(.startGameButtonTapped)
                }
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
            HStack {
                Spacer()
                Text(store.game.gameCode)
                    .font(.gameboy(size: 24))
                Spacer()
                Button {
                    UIPasteboard.general.string = store.game.gameCode
                    withAnimation {
                        codeCopied = true
                    }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
                        withAnimation {
                            codeCopied = false
                        }
                    }
                } label: {
                    Image(systemName: codeCopied ? "checkmark" : "doc.on.doc")
                        .foregroundStyle(codeCopied ? .green : .gray)
                        .contentTransition(.symbolEffect(.replace))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var dateSection: some View {
        Group {
            DatePicker(selection: $store.game.startDate, in: .now.addingTimeInterval(60)...) {
                Text("Start at")
            }
            .datePickerStyle(.compact)
            DatePicker(selection: $store.game.endDate, in: store.game.startDate.addingTimeInterval(300)..., displayedComponents: .hourAndMinute) {
                Text("End at")
            }
            .datePickerStyle(.compact)
        }
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
        }
    }
}

#Preview {
    ChickenConfigView(store: Store(initialState: ChickenConfigFeature.State(game: Shared(value: Game.mock))) {
        ChickenConfigFeature()
    })
}
