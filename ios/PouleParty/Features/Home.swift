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

struct PricingConfig: Equatable {
    let model: Game.PricingModel
    let numberOfPlayers: Int
    let pricePerPlayerCents: Int
    let depositAmountCents: Int
}

@Reducer
struct HomeFeature {

    @ObservableState
    struct State: Equatable {
        @Presents var destination: Destination.State?
        @Shared(.appStorage(AppConstants.prefIsMusicMuted)) var isMusicMuted = false
        @Shared(.appStorage(AppConstants.prefUserNickname)) var savedNickname = ""
        var gameCode: String = ""
        var musicMuted: Bool { isMusicMuted }
        var isJoiningGame = false
        var activeGame: Game? = nil
        var activeGameRole: GameRole? = nil
        var pendingPricingConfig: PricingConfig? = nil
    }

    enum Action: BindableAction {
        case accountDeletionCompleted
        case activeGameBannerDismissed
        case activeGameFound(Game, GameRole)
        case binding(BindingAction<State>)
        case chickenConfigLocationRequested
        case chickenGameStarted(Game)
        case completedGameFound(Game)
        case createPartyTapped
        case dailyFreeLimitChecked(allowed: Bool)
        case destination(PresentationAction<Destination.Action>)
        case destinationDismissed
        case gameNotFound
        case hunterGameJoined(Game, String)
        case initialLocationResolved(CLLocationCoordinate2D?)
        case joinGameButtonTapped
        case locationPermissionDenied
        case musicToggleTapped
        case networkRequestFailed
        case noActiveGameFound
        case onTask
        case rejoinGameTapped
        case rulesButtonTapped
        case settingsButtonTapped
        case startButtonTapped
    }

    @Reducer
    struct Destination {
        @ObservableState
        enum State: Equatable {
            case alert(AlertState<Action.Alert>)
            case chickenConfig(ChickenConfigFeature.State)
            case gameRules
            case planSelection(PlanSelectionFeature.State)
            case settings(SettingsFeature.State)
        }

        enum Action {
            case alert(Alert)
            case chickenConfig(ChickenConfigFeature.Action)
            case planSelection(PlanSelectionFeature.Action)
            case settings(SettingsFeature.Action)

            enum Alert: Equatable {
                case ok
            }
        }

        var body: some ReducerOf<Self> {
            EmptyReducer()
                .ifCaseLet(\.chickenConfig, action: \.chickenConfig) {
                    ChickenConfigFeature()
                }
                .ifCaseLet(\.planSelection, action: \.planSelection) {
                    PlanSelectionFeature()
                }
                .ifCaseLet(\.settings, action: \.settings) {
                    SettingsFeature()
                }
        }
    }

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.locationClient) var locationClient
    @Dependency(\.userClient) var userClient
    @Dependency(\.uuid) var uuid
    @Dependency(\.withRandomNumberGenerator) var withRandomNumberGenerator

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case let .activeGameFound(game, role):
                state.activeGame = game
                state.activeGameRole = role
                return .none
            case .binding:
                return .none
            case let .dailyFreeLimitChecked(allowed):
                if allowed {
                    return .send(.chickenConfigLocationRequested)
                }
                state.destination = .alert(
                    AlertState {
                        TextState("Daily limit reached")
                    } actions: {
                        ButtonState(role: .cancel) {
                            TextState("OK")
                        }
                    } message: {
                        TextState("You can only create 1 free game per day. Upgrade to a paid plan for unlimited games.")
                    }
                )
                return .none
            case .createPartyTapped:
                let chickenLocStatus = locationClient.authorizationStatus()
                guard chickenLocStatus == .authorizedAlways || chickenLocStatus == .authorizedWhenInUse else {
                    return .send(.locationPermissionDenied)
                }
                state.destination = .planSelection(PlanSelectionFeature.State())
                return .none
            case let .destination(.presented(.chickenConfig(.gameCreated(game)))):
                state.destination = nil
                return .send(.chickenGameStarted(game))
            case let .destination(.presented(.planSelection(.planSelected(pricingModel, numberOfPlayers, pricePerPlayerCents, depositAmountCents)))):
                state.destination = nil
                state.pendingPricingConfig = PricingConfig(
                    model: pricingModel,
                    numberOfPlayers: numberOfPlayers,
                    pricePerPlayerCents: pricePerPlayerCents,
                    depositAmountCents: depositAmountCents
                )
                if pricingModel == .free {
                    return .run { [userClient] send in
                        try? await Task.sleep(for: .milliseconds(150))
                        let userId = userClient.currentUserId() ?? ""
                        let count = (try? await apiClient.countFreeGamesToday(userId)) ?? 0
                        await send(.dailyFreeLimitChecked(allowed: count < 1))
                    }
                }
                return .run { send in
                    try? await Task.sleep(for: .milliseconds(150))
                    await send(.chickenConfigLocationRequested)
                }
            case .destination(.presented(.settings(.deleteSuccessAlertDismissed))):
                state.destination = nil
                return .send(.accountDeletionCompleted)
            case .destination:
                return .none
            case .activeGameBannerDismissed:
                state.activeGame = nil
                state.activeGameRole = nil
                return .none
            case .destinationDismissed:
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
            case .chickenConfigLocationRequested:
                return .run { [locationClient] send in
                    var location: CLLocationCoordinate2D? = nil
                    for await coordinate in locationClient.startTracking() {
                        location = coordinate
                        break
                    }
                    locationClient.stopTracking()
                    await send(.initialLocationResolved(location))
                }
            case .chickenGameStarted:
                return .none
            case .hunterGameJoined:
                return .none
            case .accountDeletionCompleted:
                return .none
            case .completedGameFound:
                return .none
            case let .initialLocationResolved(location):
                guard let config = state.pendingPricingConfig else { return .none }
                var game = Game(id: uuid().uuidString)
                game.foundCode = Game.generateFoundCode()
                game.chickenHeadStartMinutes = 5
                game.creatorId = userClient.currentUserId() ?? ""
                game.pricingModel = config.model
                game.numberOfPlayers = config.numberOfPlayers
                game.pricePerPlayer = config.pricePerPlayerCents
                game.depositAmount = config.depositAmountCents
                game.driftSeed = withRandomNumberGenerator { generator in
                    Int.random(in: 1...999_999, using: &generator)
                }
                if let location {
                    game.initialLocation = location
                }
                state.pendingPricingConfig = nil
                state.destination = .chickenConfig(
                    ChickenConfigFeature.State(game: Shared(value: game))
                )
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
                        await send(.networkRequestFailed)
                        return
                    }
                    guard let game else {
                        await send(.gameNotFound)
                        return
                    }
                    switch game.status {
                    case .waiting, .inProgress:
                        await send(.hunterGameJoined(game, finalName))
                    case .done:
                        await send(.completedGameFound(game))
                    }
                }
            case .musicToggleTapped:
                state.$isMusicMuted.withLock { $0.toggle() }
                return .none
            case .locationPermissionDenied:
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
            case .networkRequestFailed:
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
                    // First attempt may fail on cold start while auth token refreshes
                    if let (game, role) = try? await apiClient.findActiveGame(userId) {
                        await send(.activeGameFound(game, role))
                        return
                    }
                    // Retry once after a short delay to allow auth token refresh
                    try? await Task.sleep(for: .seconds(2))
                    if let (game, role) = try? await apiClient.findActiveGame(userId) {
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
                    return .send(.chickenGameStarted(game))
                case .hunter:
                    let savedNickname = state.savedNickname.trimmingCharacters(in: .whitespacesAndNewlines)
                    let finalName = savedNickname.isEmpty ? "Hunter" : savedNickname
                    return .send(.hunterGameJoined(game, finalName))
                }
            case .rulesButtonTapped:
                state.destination = .gameRules
                return .none
            case .settingsButtonTapped:
                state.destination = .settings(SettingsFeature.State())
                return .none
            case .startButtonTapped:
                let startLocStatus = locationClient.authorizationStatus()
                guard startLocStatus == .authorizedAlways || startLocStatus == .authorizedWhenInUse else {
                    return .send(.locationPermissionDenied)
                }
                state.isJoiningGame = true
                return .none
            }
        }
        .ifLet(\.$destination, action: \.destination) {
            Destination()
        }
    }
}

struct HomeView: View {
    @Bindable var store: StoreOf<HomeFeature>
    @State private var isVisible = true
    @State private var audioPlayer: AVAudioPlayer?
    @State private var musicButtonScale: CGFloat = 1.0

    var body: some View {
        NavigationStack {
        ZStack {
            VStack(alignment: .center, spacing: 0) {
                Spacer()
                VStack(alignment: .center, spacing: 10) {
                    Image("chicken")
                        .resizable()
                        .interpolation(.none)
                        .scaledToFit()
                        .frame(width: 150, height: 150)
                        .accessibilityLabel("PouleParty logo")
                    Text("POULE PARTY")
                        .font(.gameboy(size: 20))
                        .foregroundStyle(Color.onBackground)

                    Button {
                        self.store.send(.startButtonTapped)
                    } label: {
                        Text("START")
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .font(.gameboy(size: 22))
                            .padding()
                            .foregroundStyle(Color.onBackground)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.onBackground, lineWidth: 4)
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
                        .foregroundStyle(Color.onBackground)
                }
                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.gradientBackgroundWarmth)

            VStack {
                HStack {
                    Button {
                        store.send(.musicToggleTapped)
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.5)) {
                            musicButtonScale = 1.3
                        }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.5)) {
                                musicButtonScale = 1.0
                            }
                        }
                    } label: {
                        Image(systemName: store.musicMuted ? "speaker.slash.fill" : "speaker.wave.2.fill")
                            .font(.system(size: 20))
                            .foregroundColor(Color.onBackground)
                            .padding()
                            .contentTransition(.symbolEffect(.replace))
                    }
                    .scaleEffect(musicButtonScale)
                    .accessibilityLabel(store.musicMuted ? "Unmute music" : "Mute music")
                    .padding(.leading, 4)

                    Spacer()
                    Button {
                        store.send(.settingsButtonTapped)
                    } label: {
                        Image(systemName: "gearshape")
                            .font(.system(size: 20))
                            .foregroundColor(Color.onBackground)
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
                        store.send(.rulesButtonTapped)
                    } label: {
                        Text("Rules")
                            .padding()
                            .foregroundColor(Color.onBackground)
                            .font(.gameboy(size: 8))
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.onBackground, lineWidth: 2)
                            )
                    }
                    .accessibilityLabel("Game rules")
                    .padding()

                    Spacer()

                    Button {
                        store.send(.createPartyTapped)
                    } label: {
                        Text("Create Party")
                            .padding()
                            .foregroundColor(Color.onBackground)
                            .font(.gameboy(size: 8))
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.onBackground, lineWidth: 2)
                            )
                    }
                    .accessibilityLabel("Create a party")
                    .padding()
                }
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
        .onChange(of: store.musicMuted) { _, isMuted in
            if isMuted {
                self.audioPlayer?.pause()
            } else {
                self.audioPlayer?.play()
            }
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
                                self.store.send(.destinationDismissed)
                            } label: {
                                Image(systemName: "xmark")
                            }

                        }
                    }
            }
        }
        .sheet(
            isPresented: Binding(
                get: { if case .planSelection = store.destination { true } else { false } },
                set: { if !$0 { store.send(.destinationDismissed) } }
            )
        ) {
            if let planStore = store.scope(
                state: \.destination?.planSelection,
                action: \.destination.planSelection
            ) {
                PlanSelectionView(store: planStore)
                    .presentationDetents([.medium])
            }
        }
        .alert(
            $store.scope(
                state: \.destination?.alert,
                action: \.destination.alert
            )
        )
        .sheet(
            isPresented: Binding(
                get: { if case .gameRules = store.destination { true } else { false } },
                set: { if !$0 { store.send(.destinationDismissed) } }
            )
        ) {
            NavigationStack {
                GameRulesView()
                    .toolbar {
                        ToolbarItem {
                            Button {
                                store.send(.destinationDismissed)
                            } label: {
                                Image(systemName: "xmark")
                                    .foregroundStyle(Color.onBackground)
                            }
                        }
                    }
            }
        }
        .navigationDestination(
            isPresented: Binding(
                get: { if case .settings = store.destination { true } else { false } },
                set: { if !$0 { store.send(.destinationDismissed) } }
            )
        ) {
            if let settingsStore = store.scope(
                state: \.destination?.settings,
                action: \.destination.settings
            ) {
                SettingsView(store: settingsStore)
            }
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
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(.white, lineWidth: 3)
                        )
                }
                .accessibilityLabel("Rejoin game")
            }
            .padding(20)
            .frame(maxWidth: .infinity)

            Button {
                withAnimation { _ = store.send(.activeGameBannerDismissed) }
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
                .fill(Color.gradientFire)
                .shadow(color: .black.opacity(0.2), radius: 4, y: 2)
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
            if !store.musicMuted {
                self.audioPlayer?.play()
            }
        } catch {
            Logger(subsystem: "dev.rahier.pouleparty", category: "Selection").error("Failed to play sound: \(error.localizedDescription)")
        }
    }
}

#Preview {
    HomeView(
        store:
            Store(initialState: HomeFeature.State()) {
                HomeFeature()
            }
    )
}
