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
import UIKit

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
        var pendingPlanResult: PlanSelectionResult? = nil
    }

    enum Action: BindableAction {
        case accountDeletionCompleted
        case activeGameBannerDismissed
        case activeGameFound(Game, GameRole, GamePhase)
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
        /// Emitted after a successful Forfait or Caution payment. Caught by
        /// `AppFeature` to swap root state to `.paymentConfirmed(...)`.
        case paymentConfirmationRequested(game: Game, kind: PaymentConfirmationFeature.Kind)
        case pendingRegistrationDismissed
        case pendingRegistrationRefreshed(PendingRegistration?)
        case pendingRegistrationRejoinTapped
        /// Background result from `verifyHunterRegistrationPaid` — fires
        /// after the Stripe PaymentSheet completed to confirm the webhook
        /// actually wrote the registration doc. When `false`, the optimistic
        /// pending banner is cleared + an alert surfaces.
        case hunterRegistrationVerified(gameId: String, confirmed: Bool)
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
                state.destination = .planSelection(PlanSelectionFeature.State())
                return .none
            case let .destination(.presented(.gameCreation(.gameCreated(game)))):
                state.destination = nil
                return .send(.chickenGameStarted(game))
            case let .destination(.presented(.gameCreation(.paidGameCreated(game)))):
                // Forfait game was created server-side in `.pendingPayment`; webhook
                // flips to `.waiting` within a couple of seconds. Route to the
                // confirmation screen so the creator gets immediate feedback
                // instead of being dumped back on Home.
                state.destination = nil
                return .send(.paymentConfirmationRequested(game: game, kind: .creatorForfait))
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
                case .pendingPayment, .paymentFailed:
                    // Paid-creator game whose Stripe webhook hasn't flipped to
                    // `waiting` (or was cancelled). Surface a visible alert
                    // instead of silently returning — previously the user was
                    // dropped back on Home with no feedback.
                    state.destination = .alert(
                        AlertState {
                            TextState("Game not ready")
                        } actions: {
                            ButtonState(role: .cancel) {
                                TextState("OK")
                            }
                        } message: {
                            TextState("This game is still awaiting payment. Try again in a few moments, or contact the organizer.")
                        }
                    )
                    return .none
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
                let gameId = game.id
                let userId = userClient.currentUserId() ?? ""
                // Show the confirmation screen optimistically, and kick off a
                // background verification that the Stripe webhook actually
                // wrote the registration doc. If it never appears (webhook
                // failure, rotated secret, etc.), clear the pending banner and
                // alert the user instead of leaving a ghost entry.
                return .merge(
                    .send(.paymentConfirmationRequested(game: game, kind: .hunterCaution)),
                    .run { [apiClient, clock] send in
                        guard !userId.isEmpty else { return }
                        let deadline = Date().addingTimeInterval(30)
                        while Date() < deadline {
                            if let registration = try? await apiClient.findRegistration(gameId, userId),
                               registration.paid {
                                await send(.hunterRegistrationVerified(gameId: gameId, confirmed: true))
                                return
                            }
                            do { try await clock.sleep(for: .seconds(3)) } catch { return }
                        }
                        await send(.hunterRegistrationVerified(gameId: gameId, confirmed: false))
                    }
                )
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
            case .hunterGameJoined:
                state.$pendingRegistration.withLock { $0 = nil }
                return .none
            case .accountDeletionCompleted:
                return .none
            case .completedGameFound:
                return .none
            case let .initialLocationResolved(location):
                guard let config = state.pendingPlanResult else { return .none }
                // Use a Firestore-style auto-ID so free-game IDs match the
                // format Cloud Functions produce for Forfait games.
                var game = Game(id: apiClient.newGameId())
                game.foundCode = Game.generateFoundCode()
                game.timing.headStartMinutes = 5
                guard let creatorId = userClient.currentUserId(), !creatorId.isEmpty else {
                    Logger(category: "Home").error("Cannot create game: no current user id (auth not ready)")
                    state.pendingPlanResult = nil
                    return .none
                }
                game.creatorId = creatorId
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
            case .paymentConfirmationRequested:
                // Terminal — `AppFeature` catches this and swaps root state.
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

            case let .hunterRegistrationVerified(gameId, confirmed):
                guard !confirmed else { return .none }
                // Webhook never wrote the registration within the window — the
                // user paid but the server never recorded them. Clear the
                // optimistic pending banner so it doesn't look like we
                // silently registered them, and surface an alert.
                state.$pendingRegistration.withLock { current in
                    if current?.gameId == gameId {
                        current = nil
                    }
                }
                state.destination = .alert(
                    AlertState {
                        TextState("Payment verification failed")
                    } actions: {
                        ButtonState(role: .cancel) {
                            TextState("OK")
                        }
                    } message: {
                        TextState("We received your payment but couldn't confirm your registration. If you were charged, please contact the organizer — your deposit is safe.")
                    }
                )
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
                        case .pendingPayment, .paymentFailed:
                            await send(.gameNotFound)
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
