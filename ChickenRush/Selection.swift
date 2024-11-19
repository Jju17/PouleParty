//
//  Selection.swift
//  ChickenRush
//
//  Created by Julien Rahier on 15/03/2024.
//

import ComposableArchitecture
import Combine
import CoreLocation
import SwiftUI
import AVFAudio

@Reducer
struct SelectionFeature {

    @ObservableState
    struct State {
        @Presents var destination: Destination.State?
        var password: String = ""
        var isAuthenticating = false
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case destination(PresentationAction<Destination.Action>)
        case dismissChickenConfig
        case goToChickenConfigTriggered
        case goToChickenMapTriggered(Game)
        case startButtonTapped
        case validatePasswordButtonTapped
    }

    @Reducer
    struct Destination {
        enum State {
            case alert(AlertState<Action.Alert>)
            case chickenConfig(ChickenConfigFeature.State)
        }

        enum Action {
            case alert(Alert)
            case chickenConfig(ChickenConfigFeature.Action)

            enum Alert {
                case askForPassword
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
                guard state.password == "jujurahier"
                else {
                    state.password = ""
                    return .none
                }

                state.isAuthenticating = false

                return .run { send in
                    let game = await apiClient.getConfig()

                    guard game != nil
                    else {
                        await send(.goToChickenConfigTriggered)
                        return
                    }

                    if game!.endDate > .now {
                        await send(.goToChickenMapTriggered(game!))
                    } else if game!.endDate < .now {
                        try await apiClient.deleteConfig()
                        await send(.goToChickenConfigTriggered)
                    }
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
            case .goToChickenConfigTriggered:
                state.destination = .chickenConfig(
                    ChickenConfigFeature.State(game:Shared(Game(id: UUID().uuidString)))
                )
                return .none
            case .goToChickenMapTriggered:
                return .none
            case .startButtonTapped:
                return .none
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

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
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


            Button {
                store.isAuthenticating.toggle()
            } label: {
                Text("C'est moi la poule")
                    .padding()
                    .foregroundColor(.black)
                    .font(.gameboy(size: 8))
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(.black, lineWidth: 1.5)
                    )
            }
            .padding()
            .alert("Password", isPresented: $store.isAuthenticating) {
                SecureField("Password", text: $store.password)
                Button("Ok") {
                    self.store.send(.validatePasswordButtonTapped)
                }
                Button("Cancel", role: .cancel) { }
            } message: {
                Text("Please enter admin password.")
            }
        }
        .task {
            self.playSound()
            await self.animateBlinking()
        }
        .alert(
            $store.scope(
                state: \.destination?.alert,
                action: \.destination.alert
            )
        )
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
    }

    private func animateBlinking() async {
        let animation = Animation.easeInOut(duration: 0.5)
        while true {
            await MainActor.run {
                withAnimation(animation) {
                    self.isVisible.toggle()
                }
            }
            try? await Task.sleep(nanoseconds: UInt64(0.5 * 1_000_000_000))
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
