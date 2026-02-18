//
//  Selection.swift
//  ChickenRush
//
//  Created by Julien Rahier on 15/03/2024.
//

import AVFAudio
import ComposableArchitecture
import SwiftUI

@Reducer
struct SelectionFeature {

    @ObservableState
    struct State {
        @Presents var destination: Destination.State?
        var password: String = ""
        var gameCode: String = ""
        var isAuthenticating = false
        var isJoiningGame = false
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case destination(PresentationAction<Destination.Action>)
        case dismissChickenConfig
        case gameNotFound
        case goToChickenConfigTriggered
        case goToChickenMapTriggered(Game)
        case goToHunterMapTriggered(Game)
        case onTask
        case startButtonTapped
        case joinGameButtonTapped
        case validatePasswordButtonTapped
    }

    @Reducer
    struct Destination {
        enum State {
            case chickenConfig(ChickenConfigFeature.State)
            case alert(AlertState<Action.Alert>)
        }

        enum Action {
            case chickenConfig(ChickenConfigFeature.Action)
            case alert(Alert)

            enum Alert {
                case ok
            }
        }

        var body: some ReducerOf<Self> {
            Scope(state: \.chickenConfig, action: \.chickenConfig) {
                ChickenConfigFeature()
            }
        }
    }

    @Dependency(\.apiClient) var apiClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case .validatePasswordButtonTapped:
                guard state.password == ""
                else {
                    state.password = ""
                    return .none
                }

                state.isAuthenticating = false

                return .run { send in
                    await send(.goToChickenConfigTriggered)
                }
            case let .destination(.presented(.chickenConfig(.startGameTriggered(game)))):
                state.destination = nil
                return .run { send in
                    await send(.goToChickenMapTriggered(game))
                }
            case .destination:
                return .none
            case .dismissChickenConfig:
                state.destination = nil
                return .none
            case .gameNotFound:
                state.destination = .alert(
                    AlertState {
                        TextState("Game not found")
                    } actions: {
                        ButtonState(role: .cancel) {
                            TextState("OK")
                        }
                    } message: {
                        TextState("No active game found with this code. Check the code and try again.")
                    }
                )
                return .none
            case .goToChickenConfigTriggered:
                state.destination = .chickenConfig(
                    ChickenConfigFeature.State(game: Shared(value: Game(id: UUID().uuidString)))
                )
                return .none
            case .goToChickenMapTriggered:
                return .none
            case .goToHunterMapTriggered:
                return .none
            case .onTask:
                return .none
            case .startButtonTapped:
                state.isJoiningGame = true
                return .none
            case .joinGameButtonTapped:
                let code = state.gameCode.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !code.isEmpty else { return .none }

                state.isJoiningGame = false
                return .run { send in
                    if let game = try? await apiClient.findGameByCode(code),
                       game.endDate > .now {
                        await send(.goToHunterMapTriggered(game))
                    } else {
                        await send(.gameNotFound)
                    }
                }
            }
        }
        .ifLet(\.$destination, action: \.destination) {
            Destination()
        }
    }
}

struct SelectionView: View {
    @Bindable var store: StoreOf<SelectionFeature>
    @State private var isVisible = true
    @State private var audioPlayer: AVAudioPlayer?
    @State private var showingGameRules = false

    var body: some View {
        ZStack {
            VStack(alignment: .center, spacing: 0) {
                Spacer()
                VStack(alignment: .center, spacing: 10) {
                    Image("logo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 200, height: 200)

                    Button {
                        self.store.send(.startButtonTapped)
                    } label: {
                        Text("START")
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .font(.gameboy(size: 22))
                            .padding()
                            .foregroundStyle(.black)
                            .background(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(.black, lineWidth: 4)
                            )
                            .opacity(self.isVisible ? 1 : 0)
                            .onAppear {
                                self.isVisible.toggle()
                            }
                    }
                    .frame(width: 200, height: 50)

                    Text("Press start to play")
                        .font(.gameboy(size: 12))
                        .foregroundStyle(Color.black)
                }
                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.CRBeige)

            VStack {
                HStack {
                    Spacer()
                    Button {
                        showingGameRules = true
                    } label: {
                        Text("Rules")
                            .padding()
                            .foregroundColor(.black)
                            .font(.gameboy(size: 8))
                            .background(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(.black, lineWidth: 1.5)
                            )
                    }
                    .padding()
                }
                Spacer()
                HStack {
                    Spacer()
                    Button {
                        store.isAuthenticating.toggle()
                    } label: {
                        Text("I am la poule")
                            .padding()
                            .foregroundColor(.black)
                            .font(.gameboy(size: 8))
                            .background(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(.black, lineWidth: 1.5)
                            )
                    }
                    .padding()
                }
            }
            .alert("Password", isPresented: $store.isAuthenticating) {
                SecureField("Password", text: $store.password)
                Button("Ok") {
                    self.store.send(.validatePasswordButtonTapped)
                }
                Button("Cancel", role: .cancel) { }
            } message: {
                Text("Please enter admin password.")
            }
            .alert("Join Game", isPresented: $store.isJoiningGame) {
                TextField("Game code", text: $store.gameCode)
                Button("Join") {
                    self.store.send(.joinGameButtonTapped)
                }
                Button("Cancel", role: .cancel) { }
            } message: {
                Text("Enter the game code from the chicken.")
            }
        }
        .onDisappear {
            self.audioPlayer?.stop()
            self.audioPlayer = nil
        }
        .task {
            self.store.send(.onTask)
            self.playSound()
            self.animateBlinking()
        }
        .sheet(
            item: $store.scope(
                state: \.destination?.chickenConfig,
                action: \.destination.chickenConfig
            )
        ) { store in
            NavigationStack {
                ChickenConfigView(store: store)
                    .toolbar {
                        ToolbarItem {
                            Button {
                                self.store.send(.dismissChickenConfig)
                            } label: {
                                Image(systemName: "xmark")
                            }

                        }
                    }
            }
        }
        .alert(
            $store.scope(
                state: \.destination?.alert,
                action: \.destination.alert
            )
        )
        .sheet(isPresented: $showingGameRules) {
            NavigationStack {
                GameRulesView()
                    .toolbar {
                        ToolbarItem {
                            Button {
                                showingGameRules = false
                            } label: {
                                Image(systemName: "xmark")
                                    .foregroundStyle(.black)
                            }
                        }
                    }
            }
        }
    }

    private func animateBlinking() {
        Task {
            while true {
                await MainActor.run {
                    withAnimation(Animation.easeInOut(duration: 0.5)) {
                        self.isVisible.toggle()
                    }
                }
                try? await Task.sleep(nanoseconds: UInt64(0.5 * 1_000_000_000))
            }
        }
    }

    private func playSound() {
        guard let path = Bundle.main.path(forResource: "background-music", ofType: "mp3"),
              false
        else { return }
        let url = URL(fileURLWithPath: path)

        do {
            self.audioPlayer = try AVAudioPlayer(contentsOf: url)
            self.audioPlayer?.numberOfLoops = -1
            self.audioPlayer?.volume = 0.1
            self.audioPlayer?.play()
        } catch {
            print("Failed to play sound: \(error.localizedDescription)")
        }
    }
}

#Preview {
    SelectionView(
        store:
            Store(initialState: SelectionFeature.State()) {
                SelectionFeature()
            }
    )
}
