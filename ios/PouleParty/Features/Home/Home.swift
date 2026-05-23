//
//  Selection.swift
//  PouleParty
//
//  Created by Julien Rahier on 15/03/2024.
//

import AVFAudio
import ComposableArchitecture
import CoreLocation
import FirebaseFirestore
import os
import Sharing
import SwiftUI
import UIKit

extension SharedKey where Self == FileStorageKey<Set<String>>.Default {
    /// Game ids the user explicitly dismissed from the Home banner.
    /// Persisted cross-session so a dismiss sticks until the game phase
    /// changes (e.g. upcoming → inProgress resurfaces the banner).
    static var dismissedActiveGameIds: Self {
        Self[
            .fileStorage(.documentsDirectory.appending(component: "dismissed-active-game-ids.json")),
            default: []
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
        /// Set of game ids the user has dismissed from the Home banner. The
        /// banner skips these so it doesn't reappear on every resume for a
        /// game the user actively hid. Cleared individually when the same
        /// game transitions phases (e.g. upcoming → inProgress — we want
        /// the "Reprendre" banner to surface again then).
        @Shared(.dismissedActiveGameIds) var dismissedActiveGameIds: Set<String> = []
        var gameCode: String = ""
        var musicMuted: Bool { isMusicMuted }
        var activeGame: Game? = nil
        var activeGameRole: GameRole? = nil
        /// Distinguishes "Reprendre la partie" (inProgress) from "Prochaine
        /// partie" (upcoming, `.waiting`) so the banner copy + CTA matches
        /// the game state. Nil when no active game.
        var activeGamePhase: GamePhase? = nil
        /// PP-45 admin code modal state. The admin button on Home opens an
        /// alert with a TextField; on Validate, `adminCodeInput` is checked
        /// against `AdminCode.value` and either opens the wizard with
        /// `isAdminCreation = true` or surfaces a `wrongCode` alert.
        var isShowingAdminCodeAlert: Bool = false
        var adminCodeInput: String = ""
        /// True between `.adminCodeValidateTapped` (correct code) and
        /// `.initialLocationResolved`, so the seeded Game gets
        /// `isAdminCreation = true`.
        var pendingIsAdminCreation: Bool = false
    }

    enum Action: BindableAction {
        case accountDeletionCompleted
        case activeGameBannerDismissed
        case activeGameFound(Game, GameRole, GamePhase)
        case adminCodeAlertRequested
        case adminCodeDismissed
        case adminCodeValidateTapped
        case binding(BindingAction<State>)
        case chickenConfigLocationRequested
        case chickenGameStarted(Game)
        case gameMasterGameStarted(Game)
        case completedGameFound(Game)
        case createPartyTapped
        /// Long-press easter egg on the Create Party button. Hidden entry
        /// point to admin mode (PP-45): opens the admin code modal so the
        /// password isn't advertised via a visible button on Home.
        case createPartyLongPressed
        case destination(PresentationAction<Destination.Action>)
        case destinationDismissed
        case gameNotFound
        case hunterGameJoined(Game, String)
        case initialLocationResolved(CLLocationCoordinate2D?)
        case joinFlowAuthorized
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
            case gameCreation(GameCreationFeature.State)
            case gameRules
            case joinFlow(JoinFlowFeature.State)
            case settings(SettingsFeature.State)
        }

        enum Action {
            case alert(Alert)
            case gameCreation(GameCreationFeature.Action)
            case joinFlow(JoinFlowFeature.Action)
            case settings(SettingsFeature.Action)

            enum Alert: Equatable {
                case ok
                case openSettings
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
            case let .activeGameFound(game, role, phase):
                state.activeGame = game
                state.activeGameRole = role
                state.activeGamePhase = phase
                return .none
            case .binding:
                return .none
            case .createPartyTapped:
                MapWarmUp.warmUpIfNeeded()
                return .send(.chickenConfigLocationRequested)
            case .adminCodeDismissed:
                state.isShowingAdminCodeAlert = false
                state.adminCodeInput = ""
                return .none
            case .adminCodeValidateTapped:
                let entered = state.adminCodeInput.trimmingCharacters(in: .whitespaces)
                state.isShowingAdminCodeAlert = false
                state.adminCodeInput = ""
                guard entered == AdminCode.value else {
                    state.destination = .alert(
                        AlertState {
                            TextState("Wrong code")
                        } actions: {
                            ButtonState(role: .cancel) { TextState("OK") }
                        }
                    )
                    return .none
                }
                state.pendingIsAdminCreation = true
                MapWarmUp.warmUpIfNeeded()
                return .send(.chickenConfigLocationRequested)
            case .createPartyLongPressed:
                return .send(.adminCodeAlertRequested)
            case .adminCodeAlertRequested:
                state.adminCodeInput = ""
                state.isShowingAdminCodeAlert = true
                return .none
            case let .destination(.presented(.gameCreation(.gameCreated(game)))):
                state.destination = nil
                return .send(.chickenGameStarted(game))
            case .destination(.presented(.alert(.openSettings))):
                state.destination = nil
                return .run { _ in
                    await MainActor.run {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
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
            case let .destination(.presented(.joinFlow(.delegate(.joinedAsGameMaster(game))))):
                state.destination = nil
                return .send(.gameMasterGameStarted(game))
            case .destination:
                return .none
            case .activeGameBannerDismissed:
                // Record the dismissed gameId so the banner doesn't reappear
                // on the next onTask for the same game. The user can still
                // reach it via Settings → My Games.
                if let dismissed = state.activeGame?.id {
                    _ = state.$dismissedActiveGameIds.withLock { ids in
                        ids.insert(dismissed)
                    }
                }
                state.activeGame = nil
                state.activeGameRole = nil
                state.activeGamePhase = nil
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
            case .gameMasterGameStarted:
                return .none
            case .hunterGameJoined:
                return .none
            case .accountDeletionCompleted:
                return .none
            case .completedGameFound:
                return .none
            case let .initialLocationResolved(location):
                guard let creatorId = userClient.currentUserId(), !creatorId.isEmpty else {
                    Logger(category: "Home").error("Cannot create game: no current user id (auth not ready)")
                    return .none
                }
                // Free is the only client-creatable mode since PP-42 (the
                // Forfait/Caution flows were retired with PP-9). The maxPlayers
                // default seeds the wizard's stepper at the Free cap; the user
                // can dial it down to 2 from there.
                let isAdmin = state.pendingIsAdminCreation
                state.pendingIsAdminCreation = false
                var game = Game(id: apiClient.newGameId())
                game.foundCode = Game.generateFoundCode()
                game.timing.headStartMinutes = 5
                game.creatorId = creatorId
                game.chickenId = creatorId
                game.maxPlayers = 5
                game.isAdminCreation = isAdmin
                game.zone.driftSeed = withRandomNumberGenerator { generator in
                    Int.random(in: 1...999_999, using: &generator)
                }
                if let location {
                    game.initialLocation = location
                }
                let sharedGame = Shared(value: game)
                let mapConfig = ChickenMapConfigFeature.State(game: sharedGame)
                var creationState = GameCreationFeature.State(game: sharedGame, mapConfigState: mapConfig)
                creationState.isAdminCreation = isAdmin
                state.destination = .gameCreation(creationState)
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
                        ButtonState(action: .openSettings) {
                            TextState("Open Settings")
                        }
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
                state.activeGamePhase = nil
                return .none
            case .onTask:
                let userId = userClient.currentUserId()
                guard let userId, !userId.isEmpty else {
                    return .none
                }
                return .run { [apiClient, dismissedIds = state.dismissedActiveGameIds] send in
                    func emit(_ triple: (Game, GameRole, GamePhase)?) async {
                        guard let (game, role, phase) = triple else {
                            await send(.noActiveGameFound)
                            return
                        }
                        if dismissedIds.contains(game.id) {
                            await send(.noActiveGameFound)
                        } else {
                            await send(.activeGameFound(game, role, phase))
                        }
                    }
                    // First attempt may fail on cold start while auth token refreshes
                    if let triple = try? await apiClient.findActiveGame(userId) {
                        await emit(triple)
                        return
                    }
                    // Retry once after a short delay to allow auth token refresh
                    try? await clock.sleep(for: .seconds(2))
                    await emit(try? await apiClient.findActiveGame(userId))
                }
            case .rejoinGameTapped:
                guard let game = state.activeGame, let role = state.activeGameRole else {
                    return .none
                }
                // Clear dismiss for this game — if the user explicitly taps
                // the banner CTA, they no longer want it hidden.
                _ = state.$dismissedActiveGameIds.withLock { ids in
                    ids.remove(game.id)
                }
                state.activeGame = nil
                state.activeGameRole = nil
                state.activeGamePhase = nil
                switch role {
                case .chicken:
                    return .send(.chickenGameStarted(game))
                case .hunter:
                    let savedNickname = state.savedNickname.trimmingCharacters(in: .whitespacesAndNewlines)
                    let finalName = savedNickname.isEmpty ? "Hunter" : savedNickname
                    return .send(.hunterGameJoined(game, finalName))
                case .gameMaster:
                    return .send(.gameMasterGameStarted(game))
                }
            case .rulesButtonTapped:
                state.destination = .gameRules
                return .none
            case .settingsButtonTapped:
                state.destination = .settings(SettingsFeature.State())
                return .none
            case .startButtonTapped:
                MapWarmUp.warmUpIfNeeded()
                return .send(.joinFlowAuthorized)
            case .joinFlowAuthorized:
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
    @Environment(\.scenePhase) private var scenePhase
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

                if let activeGame = store.activeGame,
                   let role = store.activeGameRole,
                   let phase = store.activeGamePhase {
                    RejoinGameBanner(
                        gameCode: activeGame.gameCode,
                        phase: phase,
                        role: role,
                        startDate: phase == .upcoming ? activeGame.startDate : nil,
                        onRejoin: { store.send(.rejoinGameTapped) },
                        onDismiss: {
                            withAnimation { _ = store.send(.activeGameBannerDismissed) }
                        }
                    )
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
                    // Hidden admin-mode entry: long-press the Create Party
                    // button to open the admin code modal (PP-45). The
                    // password is intentionally not advertised via a visible
                    // button so Apple reviewers don't surface it.
                    .simultaneousGesture(
                        LongPressGesture(minimumDuration: 1.5)
                            .onEnded { _ in
                                store.send(.createPartyLongPressed)
                            }
                    )
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
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .background, .inactive:
                self.audioPlayer?.pause()
            case .active:
                if !store.musicMuted {
                    self.audioPlayer?.play()
                }
            @unknown default:
                break
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
            "Admin mode",
            isPresented: $store.isShowingAdminCodeAlert
        ) {
            SecureField("Admin code", text: $store.adminCodeInput)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            Button("Validate") { store.send(.adminCodeValidateTapped) }
            Button("Cancel", role: .cancel) { store.send(.adminCodeDismissed) }
        } message: {
            Text("Enter the admin code")
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


    private func animateBlinking() async {
        while !Task.isCancelled {
            withAnimation(Animation.easeInOut(duration: 0.5)) {
                self.isVisible.toggle()
            }
            do {
                try await Task.sleep(nanoseconds: UInt64(0.5 * 1_000_000_000))
            } catch {
                return
            }
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
            Logger(category: "Selection").error("Failed to play sound: \(error.localizedDescription)")
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
