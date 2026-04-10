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

struct PlanSelectionResult: Equatable {
    let model: Game.PricingModel
    let numberOfPlayers: Int
    let pricePerPlayerCents: Int
    let depositAmountCents: Int
}

struct PendingRegistration: Codable, Equatable {
    let gameId: String
    let gameCode: String
    let teamName: String
    var startDate: Date
    var isFinished: Bool = false
}

extension SharedKey where Self == FileStorageKey<PendingRegistration?>.Default {
    static var pendingRegistration: Self {
        Self[
            .fileStorage(.documentsDirectory.appending(component: "pending-registration.json")),
            default: nil
        ]
    }
}

@Reducer
struct HomeFeature {

    @ObservableState
    struct State: Equatable {
        @Presents var destination: Destination.State?
        @Shared(.appStorage(AppConstants.prefIsMusicMuted)) var isMusicMuted = false
        @Shared(.appStorage(AppConstants.prefUserNickname)) var savedNickname = ""
        @Shared(.pendingRegistration) var pendingRegistration: PendingRegistration?
        var gameCode: String = ""
        var musicMuted: Bool { isMusicMuted }
        var activeGame: Game? = nil
        var activeGameRole: GameRole? = nil
        var pendingPlanResult: PlanSelectionResult? = nil
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
        case pendingRegistrationDismissed
        case pendingRegistrationRefreshed(PendingRegistration?)
        case pendingRegistrationRejoinTapped
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
            case gameCreation(GameCreationFeature.State)
            case gameRules
            case joinFlow(JoinFlowFeature.State)
            case planSelection(PlanSelectionFeature.State)
            case settings(SettingsFeature.State)
        }

        enum Action {
            case alert(Alert)
            case gameCreation(GameCreationFeature.Action)
            case joinFlow(JoinFlowFeature.Action)
            case planSelection(PlanSelectionFeature.Action)
            case settings(SettingsFeature.Action)

            enum Alert: Equatable {
                case ok
            }
        }

        var body: some ReducerOf<Self> {
            EmptyReducer()
                .ifCaseLet(\.gameCreation, action: \.gameCreation) {
                    GameCreationFeature()
                }
                .ifCaseLet(\.joinFlow, action: \.joinFlow) {
                    JoinFlowFeature()
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
    @Dependency(\.continuousClock) var clock
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
                MapWarmUp.warmUpIfNeeded()
                state.destination = .planSelection(PlanSelectionFeature.State())
                return .none
            case let .destination(.presented(.gameCreation(.gameCreated(game)))):
                state.destination = nil
                return .send(.chickenGameStarted(game))
            case let .destination(.presented(.planSelection(.planSelected(pricingModel, numberOfPlayers, pricePerPlayerCents, depositAmountCents)))):
                state.destination = nil
                state.pendingPlanResult = PlanSelectionResult(
                    model: pricingModel,
                    numberOfPlayers: numberOfPlayers,
                    pricePerPlayerCents: pricePerPlayerCents,
                    depositAmountCents: depositAmountCents
                )
                if pricingModel == .free {
                    return .run { [userClient] send in
                        try? await Task.sleep(for: .milliseconds(150))
                        guard let userId = userClient.currentUserId() else { return }
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
            case let .destination(.presented(.joinFlow(.delegate(.joinGame(game, hunterName))))):
                state.destination = nil
                switch game.status {
                case .waiting, .inProgress:
                    return .send(.hunterGameJoined(game, hunterName))
                case .done:
                    return .send(.completedGameFound(game))
                }
            case let .destination(.presented(.joinFlow(.delegate(.registered(game, teamName))))):
                state.destination = nil
                state.$pendingRegistration.withLock { value in
                    value = PendingRegistration(
                        gameId: game.id,
                        gameCode: game.gameCode,
                        teamName: teamName,
                        startDate: game.startDate
                    )
                }
                return .none
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
                state.$pendingRegistration.withLock { $0 = nil }
                return .none
            case .accountDeletionCompleted:
                return .none
            case .completedGameFound:
                return .none
            case let .initialLocationResolved(location):
                guard let config = state.pendingPlanResult else { return .none }
                var game = Game(id: uuid().uuidString)
                game.foundCode = Game.generateFoundCode()
                game.timing.headStartMinutes = 5
                game.creatorId = userClient.currentUserId() ?? ""
                game.pricing.model = config.model
                game.maxPlayers = config.numberOfPlayers
                game.pricing.pricePerPlayer = config.pricePerPlayerCents
                game.pricing.deposit = config.depositAmountCents
                if config.model == .deposit {
                    game.registration.required = true
                }
                game.zone.driftSeed = withRandomNumberGenerator { generator in
                    Int.random(in: 1...999_999, using: &generator)
                }
                if let location {
                    game.initialLocation = location
                }
                state.pendingPlanResult = nil
                let sharedGame = Shared(value: game)
                let mapConfig = ChickenMapConfigFeature.State(game: sharedGame)
                state.destination = .gameCreation(
                    GameCreationFeature.State(game: sharedGame, mapConfigState: mapConfig)
                )
                return .none
            case .joinGameButtonTapped:
                return .none

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
                let pending = state.pendingRegistration
                return .merge(
                    .run { [apiClient] send in
                        guard let pending else { return }
                        guard let game = try? await apiClient.getConfig(pending.gameId) else {
                            // Game no longer exists — clear the banner
                            await send(.pendingRegistrationRefreshed(nil))
                            return
                        }
                        let updated = PendingRegistration(
                            gameId: pending.gameId,
                            gameCode: game.gameCode,
                            teamName: pending.teamName,
                            startDate: game.startDate,
                            isFinished: game.status == .done
                        )
                        await send(.pendingRegistrationRefreshed(updated))
                    },
                    .run { [apiClient] send in
                    // First attempt may fail on cold start while auth token refreshes
                    if let (game, role) = try? await apiClient.findActiveGame(userId) {
                        await send(.activeGameFound(game, role))
                        return
                    }
                    // Retry once after a short delay to allow auth token refresh
                    try? await clock.sleep(for: .seconds(2))
                    if let (game, role) = try? await apiClient.findActiveGame(userId) {
                        await send(.activeGameFound(game, role))
                    } else {
                        await send(.noActiveGameFound)
                    }
                    }
                )
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
            case .pendingRegistrationDismissed:
                state.$pendingRegistration.withLock { $0 = nil }
                return .none

            case let .pendingRegistrationRefreshed(updated):
                state.$pendingRegistration.withLock { $0 = updated }
                return .none

            case .pendingRegistrationRejoinTapped:
                guard let pending = state.pendingRegistration else { return .none }
                let savedNickname = state.savedNickname.trimmingCharacters(in: .whitespacesAndNewlines)
                let finalName = savedNickname.isEmpty ? "Hunter" : savedNickname
                let gameId = pending.gameId
                return .run { send in
                    do {
                        guard let game = try await apiClient.getConfig(gameId) else {
                            await send(.gameNotFound)
                            return
                        }
                        switch game.status {
                        case .waiting, .inProgress:
                            await send(.hunterGameJoined(game, finalName))
                        case .done:
                            await send(.completedGameFound(game))
                        }
                    } catch {
                        await send(.networkRequestFailed)
                    }
                }

            case .startButtonTapped:
                let startLocStatus = locationClient.authorizationStatus()
                guard startLocStatus == .authorizedAlways || startLocStatus == .authorizedWhenInUse else {
                    return .send(.locationPermissionDenied)
                }
                MapWarmUp.warmUpIfNeeded()
                state.destination = .joinFlow(JoinFlowFeature.State())
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
    @State private var isPendingBannerCollapsed = false

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
                .offset(y: -60)
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
                } else if store.pendingRegistration != nil {
                    if isPendingBannerCollapsed {
                        collapsedPendingRegistrationTab
                    } else {
                        pendingRegistrationBanner
                    }
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
                state: \.destination?.gameCreation,
                action: \.destination.gameCreation
            )
        ) { creationStore in
            GameCreationView(
                store: creationStore,
                onDismiss: { self.store.send(.destinationDismissed) }
            )
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
        .sheet(
            isPresented: Binding(
                get: { if case .joinFlow = store.destination { true } else { false } },
                set: { if !$0 { store.send(.destinationDismissed) } }
            )
        ) {
            if let joinStore = store.scope(
                state: \.destination?.joinFlow,
                action: \.destination.joinFlow
            ) {
                JoinFlowView(store: joinStore)
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

    private var pendingRegistrationBanner: some View {
        ZStack(alignment: .topTrailing) {
            VStack(spacing: 12) {
                Text(store.pendingRegistration?.isFinished == true ? "Game ended" : "Registered to game")
                    .font(.gameboy(size: 12))
                    .foregroundStyle(.white)

                if let pending = store.pendingRegistration {
                    Text(pending.gameCode)
                        .font(.gameboy(size: 20))
                        .foregroundStyle(.white)
                    Text(pending.teamName)
                        .font(.gameboy(size: 9))
                        .foregroundStyle(.white.opacity(0.8))
                    if !pending.isFinished {
                        Text("Starting in \(pending.startDate, style: .relative)")
                            .font(.gameboy(size: 9))
                            .foregroundStyle(.white.opacity(0.8))
                    }
                }

                Button {
                    store.send(.pendingRegistrationRejoinTapped)
                } label: {
                    Text(store.pendingRegistration?.isFinished == true ? "View results" : "Join")
                        .font(.gameboy(size: 16))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(.white, lineWidth: 3)
                        )
                }
                .accessibilityLabel("Open registered game")
            }
            .padding(20)
            .frame(maxWidth: .infinity)

            Button {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                    isPendingBannerCollapsed = true
                }
            } label: {
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(8)
            }
            .accessibilityLabel("Collapse")
            .padding(8)
        }
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.gradientFire)
                .shadow(color: .black.opacity(0.2), radius: 4, y: 2)
        )
        .padding(.horizontal, 24)
        .transition(.move(edge: .trailing).combined(with: .opacity))
    }

    private var collapsedPendingRegistrationTab: some View {
        HStack(spacing: 0) {
            Spacer(minLength: 0)
            Button {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                    isPendingBannerCollapsed = false
                }
            } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.vertical, 16)
                    .padding(.leading, 14)
                    .padding(.trailing, 10)
                    .background(
                        UnevenRoundedRectangle(
                            topLeadingRadius: 16,
                            bottomLeadingRadius: 16,
                            bottomTrailingRadius: 0,
                            topTrailingRadius: 0
                        )
                        .fill(Color.gradientFire)
                        .shadow(color: .black.opacity(0.25), radius: 4, x: -2, y: 2)
                    )
            }
            .accessibilityLabel("Expand registered game banner")
        }
        .frame(maxWidth: .infinity, minHeight: 190, alignment: .topTrailing)
        .transition(.move(edge: .trailing))
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
