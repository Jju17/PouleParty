//
//  Selection.swift
//  PouleParty
//
//  Created by Julien Rahier on 15/03/2024.
//

import AVFAudio
import ComposableArchitecture
import CoreLocation
import os
import Sharing
import SwiftUI

@Reducer
struct SelectionFeature {

    @ObservableState
    struct State: Equatable {
        @Presents var destination: Destination.State?
        @Shared(.appStorage(AppConstants.prefUserNickname)) var savedNickname = ""
        var gameCode: String = ""
        var isConfirmingChicken = false
        var isJoiningGame = false
        var activeGame: Game? = nil
        var activeGameRole: PlayerRole? = nil
    }

    enum Action: BindableAction {
        case activeGameFound(Game, PlayerRole)
        case binding(BindingAction<State>)
        case destination(PresentationAction<Destination.Action>)
        case dismissChickenConfig
        case gameInProgress
        case gameNotFound
        case confirmChickenTapped
        case goToChickenConfigTriggered
        case goToChickenMapTriggered(Game)
        case goToVictoryAsSpectator(Game)
        case initialLocationResolved(CLLocationCoordinate2D?)
        case goToHunterMapTriggered(Game, String)
        case locationRequired
        case networkError
        case dismissActiveGame
        case noActiveGameFound
        case onTask
        case rejoinGameTapped
        case startButtonTapped
        case joinGameButtonTapped
        case goToOnboarding
    }

    @Reducer
    struct Destination {
        @ObservableState
        enum State: Equatable {
            case chickenConfig(ChickenConfigFeature.State)
            case alert(AlertState<Action.Alert>)
        }

        enum Action {
            case chickenConfig(ChickenConfigFeature.Action)
            case alert(Alert)

            enum Alert: Equatable {
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
    @Dependency(\.userClient) var userClient
    @Dependency(\.locationClient) var locationClient
    @Dependency(\.uuid) var uuid

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case .goToOnboarding:
                return .none
            case .confirmChickenTapped:
                let chickenLocStatus = locationClient.authorizationStatus()
                guard chickenLocStatus == .authorizedAlways || chickenLocStatus == .authorizedWhenInUse else {
                    state.isConfirmingChicken = false
                    return .send(.locationRequired)
                }

                state.isConfirmingChicken = false

                return .send(.goToChickenConfigTriggered)
            case let .destination(.presented(.chickenConfig(.startGameTriggered(game)))):
                state.destination = nil
                return .send(.goToChickenMapTriggered(game))
            case .destination:
                return .none
            case .dismissActiveGame:
                state.activeGame = nil
                state.activeGameRole = nil
                return .none
            case .dismissChickenConfig:
                state.destination = nil
                return .none
            case .gameInProgress:
                state.destination = .alert(
                    AlertState {
                        TextState("Game in progress")
                    } actions: {
                        ButtonState(role: .cancel) {
                            TextState("OK")
                        }
                    } message: {
                        TextState("This game is already in progress. You cannot join anymore.")
                    }
                )
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
                return .run { [locationClient] send in
                    var location: CLLocationCoordinate2D? = nil
                    for await coordinate in locationClient.startTracking() {
                        location = coordinate
                        break
                    }
                    locationClient.stopTracking()
                    await send(.initialLocationResolved(location))
                }
            case let .initialLocationResolved(location):
                var game = Game(id: uuid().uuidString)
                game.foundCode = Game.generateFoundCode()
                game.chickenHeadStartMinutes = 5
                game.creatorId = userClient.currentUserId() ?? ""
                game.driftSeed = Int.random(in: 1...999_999)
                if let location {
                    game.initialLocation = location
                }
                state.destination = .chickenConfig(
                    ChickenConfigFeature.State(game: Shared(value: game))
                )
                return .none
            case .goToChickenMapTriggered:
                return .none
            case .goToVictoryAsSpectator:
                return .none
            case .goToHunterMapTriggered:
                return .none
            case .locationRequired:
                state.destination = .alert(
                    AlertState {
                        TextState("Location Required")
                    } actions: {
                        ButtonState(role: .cancel) {
                            TextState("OK")
                        }
                    } message: {
                        TextState("Location is the core of PouleParty! Your position is anonymous and only used during the game. Please enable location access to continue.")
                    }
                )
                return .none
            case .networkError:
                state.destination = .alert(
                    AlertState {
                        TextState("Network Error")
                    } actions: {
                        ButtonState(role: .cancel) {
                            TextState("OK")
                        }
                    } message: {
                        TextState("Could not reach the server. Check your connection and try again.")
                    }
                )
                return .none
            case let .activeGameFound(game, role):
                state.activeGame = game
                state.activeGameRole = role
                return .none
            case .noActiveGameFound:
                state.activeGame = nil
                state.activeGameRole = nil
                return .none
            case .onTask:
                let userId = userClient.currentUserId()
                guard let userId, !userId.isEmpty else {
                    return .none
                }
                return .run { [apiClient] send in
                    if let (game, role) = try await apiClient.findActiveGame(userId) {
                        await send(.activeGameFound(game, role))
                    } else {
                        await send(.noActiveGameFound)
                    }
                }
            case .rejoinGameTapped:
                guard let game = state.activeGame, let role = state.activeGameRole else {
                    return .none
                }
                state.activeGame = nil
                state.activeGameRole = nil
                switch role {
                case .chicken:
                    return .send(.goToChickenMapTriggered(game))
                case .hunter:
                    let savedNickname = state.savedNickname.trimmingCharacters(in: .whitespacesAndNewlines)
                    let finalName = savedNickname.isEmpty ? "Hunter" : savedNickname
                    return .send(.goToHunterMapTriggered(game, finalName))
                }
            case .startButtonTapped:
                // Block if location not authorized
                let startLocStatus = locationClient.authorizationStatus()
                guard startLocStatus == .authorizedAlways || startLocStatus == .authorizedWhenInUse else {
                    return .send(.locationRequired)
                }
                state.isJoiningGame = true
                return .none
            case .joinGameButtonTapped:
                let code = state.gameCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
                guard code.count == AppConstants.gameCodeLength,
                      code.allSatisfy({ $0.isLetter || $0.isNumber })
                else { return .none }

                let savedNickname = state.savedNickname.trimmingCharacters(in: .whitespacesAndNewlines)
                let finalName = savedNickname.isEmpty ? "Hunter" : savedNickname
                state.isJoiningGame = false
                return .run { send in
                    let game: Game?
                    do {
                        game = try await apiClient.findGameByCode(code)
                    } catch {
                        await send(.networkError)
                        return
                    }
                    guard let game else {
                        await send(.gameNotFound)
                        return
                    }
                    switch game.status {
                    case .waiting, .inProgress:
                        await send(.goToHunterMapTriggered(game, finalName))
                    case .done:
                        await send(.goToVictoryAsSpectator(game))
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
    @State private var showingSettings = false

    var body: some View {
        NavigationStack {
        ZStack {
            VStack(alignment: .center, spacing: 0) {
                Spacer()
                VStack(alignment: .center, spacing: 10) {
                    Image("logo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 200, height: 200)
                        .accessibilityLabel("PouleParty logo")

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
                    .accessibilityLabel("Start game")
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
                        showingSettings = true
                    } label: {
                        Image(systemName: "gearshape")
                            .font(.system(size: 20))
                            .foregroundColor(.black)
                            .padding()
                    }
                    .accessibilityLabel("Settings")
                    .padding(.trailing, 4)
                }
                Spacer()

                if store.activeGame != nil {
                    rejoinBanner
                }

                HStack {
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
                    .accessibilityLabel("Game rules")
                    .padding()

                    Spacer()

                    Button {
                        store.isConfirmingChicken = true
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
                    .accessibilityLabel("I am the chicken")
                    .padding()
                }
            }
            .alert("Create a game", isPresented: $store.isConfirmingChicken) {
                Button("Continue") {
                    self.store.send(.confirmChickenTapped)
                }
                Button("Cancel", role: .cancel) { }
            } message: {
                Text("You will create a new game as the Chicken. Are you ready?")
            }
            .alert("Join Game", isPresented: $store.isJoiningGame) {
                TextField("Game code", text: $store.gameCode)
                Button("Join") {
                    self.store.send(.joinGameButtonTapped)
                }
                Button("Cancel", role: .cancel) { }
            } message: {
                Text("Enter the game code to join.")
            }
        }
        .onDisappear {
            self.audioPlayer?.stop()
            self.audioPlayer = nil
        }
        .task {
            self.store.send(.onTask)
            self.playSound()
            await self.animateBlinking()
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
        .navigationDestination(isPresented: $showingSettings) {
            SettingsView(
                store: Store(initialState: SettingsFeature.State()) {
                    SettingsFeature()
                },
                onAccountDeleted: {
                    store.send(.goToOnboarding)
                }
            )
        }
        .navigationBarHidden(true)
        }
    }

    private var rejoinBanner: some View {
        ZStack(alignment: .topTrailing) {
            VStack(spacing: 12) {
                Text("Game in progress")
                    .font(.gameboy(size: 14))
                    .foregroundStyle(.white)

                if let code = store.activeGame?.gameCode {
                    Text(code)
                        .font(.gameboy(size: 20))
                        .foregroundStyle(.white)
                }

                Button {
                    store.send(.rejoinGameTapped)
                } label: {
                    Text("Rejoin")
                        .font(.gameboy(size: 16))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(.white, lineWidth: 3)
                        )
                }
                .accessibilityLabel("Rejoin game")
            }
            .padding(20)
            .frame(maxWidth: .infinity)

            Button {
                withAnimation { _ = store.send(.dismissActiveGame) }
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(8)
            }
            .accessibilityLabel("Dismiss")
            .padding(8)
        }
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.CROrange)
        )
        .padding(.horizontal, 24)
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    private func animateBlinking() async {
        while !Task.isCancelled {
            withAnimation(Animation.easeInOut(duration: 0.5)) {
                self.isVisible.toggle()
            }
            try? await Task.sleep(nanoseconds: UInt64(0.5 * 1_000_000_000))
        }
    }

    private func playSound() {
        guard let path = Bundle.main.path(forResource: "background-music", ofType: "mp3")
        else { return }
        let url = URL(fileURLWithPath: path)

        do {
            try AVAudioSession.sharedInstance().setCategory(.ambient)
            try AVAudioSession.sharedInstance().setActive(true)
            self.audioPlayer = try AVAudioPlayer(contentsOf: url)
            self.audioPlayer?.numberOfLoops = -1
            self.audioPlayer?.volume = 0.1
            self.audioPlayer?.play()
        } catch {
            Logger(subsystem: "dev.rahier.pouleparty", category: "Selection").error("Failed to play sound: \(error.localizedDescription)")
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
