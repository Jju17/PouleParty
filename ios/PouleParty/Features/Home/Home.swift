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
        @Shared(.pendingRegistration) var pendingRegistration: PendingRegistration?
        /// Set of game ids the user has dismissed from the Home banner. The
        /// banner skips these so it doesn't reappear on every resume for a
        /// game the user actively hid. Cleared individually when the same
        /// game transitions phases (e.g. upcoming → inProgress — we want
        /// the "Reprendre" banner to surface again then).
        @Shared(.dismissedActiveGameIds) var dismissedActiveGameIds: Set<String> = []
        var gameCode: String = ""
        var musicMuted: Bool { isMusicMuted }
        var currentPendingRegistration: PendingRegistration? { pendingRegistration }
        var activeGame: Game? = nil
        var activeGameRole: GameRole? = nil
        /// Distinguishes "Reprendre la partie" (inProgress) from "Prochaine
        /// partie" (upcoming, `.waiting`) so the banner copy + CTA matches
        /// the game state. Nil when no active game.
        var activeGamePhase: GamePhase? = nil
    }

    enum Action: BindableAction {
        case accountDeletionCompleted
        case activeGameBannerDismissed
        case activeGameFound(Game, GameRole, GamePhase)
        /// Placeholder for the future admin-mode entry point. Wired in PP-45.
        case adminModeTapped
        case binding(BindingAction<State>)
        case chickenConfigLocationRequested
        case chickenGameStarted(Game)
        case completedGameFound(Game)
        case createPartyTapped
        /// Long-press easter egg on the Create Party button. Skips the
        /// whole wizard and drops the user straight onto the chicken map
        /// with a preset game (stayInTheZone, starts in 1 min, 1 h long,
        /// current loc or Brussels fallback for both start and final
        /// center) so drift / shrink visuals can be eyeballed with every
        /// future circle rendered at once.
        case createPartyLongPressed
        case launchDebugPreviewTapped
        case chickenDebugGameStarted(Game)
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
        /// Placeholder for the "Envie de créer une partie ?" web CTA. Wired in PP-46.
        case webCreatePartyTapped
    }

    @Reducer
    struct Destination {
        @ObservableState
        enum State: Equatable {
            case alert(AlertState<Action.Alert>)
            case debugMapConfig(ChickenMapConfigFeature.State)
            case gameCreation(GameCreationFeature.State)
            case gameRules
            case joinFlow(JoinFlowFeature.State)
            case settings(SettingsFeature.State)
        }

        enum Action {
            case alert(Alert)
            case debugMapConfig(ChickenMapConfigFeature.Action)
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
                .ifCaseLet(\.debugMapConfig, action: \.debugMapConfig) {
                    ChickenMapConfigFeature()
                }
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
                return .run { [userClient, apiClient] send in
                    guard let userId = userClient.currentUserId() else { return }
                    let count = (try? await apiClient.countFreeGamesToday(userId)) ?? 0
                    await send(.dailyFreeLimitChecked(allowed: count < 1))
                }
            case .adminModeTapped:
                // PP-45 fills in: modal asking for the `jujurahier` admin code,
                // then opens the wizard with `isAdminCreation = true` to lift
                // the maxPlayers cap. Until then this button is a visible
                // placeholder per PP-42.
                return .none
            case .webCreatePartyTapped:
                // PP-46 fills in: opens the localized "create a party" landing
                // page in the device browser.
                return .none
            case .createPartyLongPressed:
                let chickenLocStatus = locationClient.authorizationStatus()
                guard chickenLocStatus == .authorizedAlways || chickenLocStatus == .authorizedWhenInUse else {
                    return .send(.locationPermissionDenied)
                }
                guard let creatorId = userClient.currentUserId(), !creatorId.isEmpty else {
                    Logger(category: "Home").error("Debug party: no current user id")
                    return .none
                }
                let center = locationClient.lastLocation() ?? CLLocationCoordinate2D(
                    latitude: AppConstants.defaultLatitude,
                    longitude: AppConstants.defaultLongitude
                )
                MapWarmUp.warmUpIfNeeded()
                // Seed just the fields the ChickenMapConfigView needs —
                // gameMode (so the start/final pin picker shows both
                // options), a default location and radius. Timing /
                // seed / foundCode are finalized when the user taps
                // "Launch Preview" so the countdown is always 60 s
                // from launch time, no matter how long they spent
                // dragging pins.
                var game = Game(id: apiClient.newGameId())
                game.name = "DEBUG PREVIEW"
                game.creatorId = creatorId
                game.gameMode = .stayInTheZone
                game.maxPlayers = 1
                game.initialLocation = center
                game.finalLocation = center
                game.zone.radius = 1500
                game.powerUps.enabled = false
                let sharedGame = Shared(value: game)
                state.destination = .debugMapConfig(
                    ChickenMapConfigFeature.State(game: sharedGame)
                )
                return .none
            case .launchDebugPreviewTapped:
                guard case let .debugMapConfig(configState) = state.destination else {
                    return .none
                }
                var game = configState.game
                game.foundCode = Game.generateFoundCode()
                let start = Date.now.addingTimeInterval(60)
                game.timing.start = Timestamp(date: start)
                game.timing.end = Timestamp(date: start.addingTimeInterval(3600))
                game.timing.headStartMinutes = 0
                let (interval, decline) = calculateNormalModeSettings(
                    initialRadius: game.zone.radius,
                    gameDurationMinutes: 60
                )
                game.zone.shrinkIntervalMinutes = interval
                game.zone.shrinkMetersPerUpdate = decline
                game.zone.driftSeed = withRandomNumberGenerator { generator in
                    Int.random(in: 1...999_999, using: &generator)
                }
                state.destination = nil
                return .run { [game] send in
                    do {
                        try await apiClient.setConfig(game)
                        await send(.chickenDebugGameStarted(game))
                    } catch {
                        Logger(category: "Home").error("Debug party: setConfig failed: \(error)")
                    }
                }
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
            case .chickenDebugGameStarted:
                return .none
            case .hunterGameJoined:
                state.$pendingRegistration.withLock { $0 = nil }
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
                var game = Game(id: apiClient.newGameId())
                game.foundCode = Game.generateFoundCode()
                game.timing.headStartMinutes = 5
                game.creatorId = creatorId
                game.maxPlayers = 5
                game.zone.driftSeed = withRandomNumberGenerator { generator in
                    Int.random(in: 1...999_999, using: &generator)
                }
                if let location {
                    game.initialLocation = location
                }
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
                let pending = state.pendingRegistration
                return .merge(
                    .run { [apiClient] send in
                        guard let pending else { return }
                        // TTL: a banner for a game whose start date was > 7 days
                        // ago is a zombie (user never opened the app after the
                        // game ended). Clear it instead of keeping it forever.
                        let sevenDaysAgo = Date().addingTimeInterval(-7 * 24 * 60 * 60)
                        if pending.startDate < sevenDaysAgo {
                            await send(.pendingRegistrationRefreshed(nil))
                            return
                        }
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
                    .run { [apiClient, dismissedIds = state.dismissedActiveGameIds] send in
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
                )
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
    @Environment(\.scenePhase) private var scenePhase
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
                } else if let pending = store.currentPendingRegistration {
                    if isPendingBannerCollapsed {
                        CollapsedPendingRegistrationTab(
                            onExpand: {
                                withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                                    isPendingBannerCollapsed = false
                                }
                            }
                        )
                    } else {
                        PendingRegistrationBanner(
                            pending: pending,
                            onJoin: { store.send(.pendingRegistrationRejoinTapped) },
                            onCollapse: {
                                withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                                    isPendingBannerCollapsed = true
                                }
                            }
                        )
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
                    // Hidden debug easter egg: long-press the Create Party
                    // button to skip the wizard and spawn a preset
                    // stayInTheZone game with every future shrunk circle
                    // rendered up front on the chicken map.
                    .simultaneousGesture(
                        LongPressGesture(minimumDuration: 1.5)
                            .onEnded { _ in
                                store.send(.createPartyLongPressed)
                            }
                    )
                    .padding()
                }

                // PP-42 placeholders: visible buttons whose behavior lands in
                // PP-45 (admin mode unlock) and PP-46 (web CTA).
                VStack(spacing: 8) {
                    Button {
                        store.send(.adminModeTapped)
                    } label: {
                        Text("Admin mode")
                            .font(.gameboy(size: 8))
                            .foregroundStyle(Color.onBackground.opacity(0.6))
                    }
                    .accessibilityLabel("Admin mode")

                    Button {
                        store.send(.webCreatePartyTapped)
                    } label: {
                        Text("Want to create a party?")
                            .font(.gameboy(size: 8))
                            .foregroundStyle(Color.onBackground.opacity(0.6))
                            .underline()
                    }
                    .accessibilityLabel("Want to create a party?")
                }
                .padding(.bottom, 12)
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
        .fullScreenCover(
            item: $store.scope(
                state: \.destination?.debugMapConfig,
                action: \.destination.debugMapConfig
            )
        ) { configStore in
            DebugMapSetupView(
                store: configStore,
                onLaunch: { self.store.send(.launchDebugPreviewTapped) },
                onCancel: { self.store.send(.destinationDismissed) }
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
