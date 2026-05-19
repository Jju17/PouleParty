//
//  HunterMap.swift
//  PouleParty
//
//  Created by Julien Rahier on 14/03/2024.
//

import ComposableArchitecture
import FirebaseFirestore
import MapboxMaps
import os
import SwiftUI

private let logger = Logger(category: "HunterMap")

@Reducer
struct HunterMapFeature {

    @ObservableState
    struct State: Equatable {
        @Presents var destination: Destination.State?
        @Presents var challenges: ChallengesFeature.State?
        var game: Game
        var hunterId: String = ""
        var hunterName: String = "Hunter"
        var enteredCode: String = ""
        var isEnteringFoundCode: Bool = false
        var nextRadiusUpdate: Date?
        var nowDate: Date = .now
        var previousWinnersCount: Int = -1
        var radius: Int = 1500
        var mapCircle: CircleOverlay?
        var showGameInfo: Bool = false
        var winnerNotification: String? = nil
        var countdownNumber: Int? = nil
        var countdownText: String? = nil
        var wrongCodeAttempts: Int = 0
        var codeCooldownUntil: Date? = nil
        var userLocation: CLLocationCoordinate2D?
        var isOutsideZone: Bool = false
        var lastLiveActivityState: PoulePartyAttributes.ContentState?
        var powerUps: MapPowerUpsFeature.State = .init()
        var previewCircle: CircleOverlay? = nil
        var decoyLocation: CLLocationCoordinate2D? = nil
        // Latest known Chicken position broadcasted via
        // `chickenLocationsStream`. Tracked in every mode (not just
        // followTheChicken) so Radar Ping has a fresh point to reveal
        // the moment it's activated. Hunter-map rendering gates
        // visibility on `game.isRadarPingActive` — without that gate
        // this would be a free locator.
        var chickenLocation: CLLocationCoordinate2D? = nil
        var hasChallenges: Bool = false

        // Recomputed on every observable change; SwiftUI re-evaluates
        // when state changes (notably on challenges-sheet dismiss).
        var pendingChallengeCount: Int {
            PendingChallengeStore.ids(forGame: game.id).count
        }
        var hasPendingChallenges: Bool { pendingChallengeCount > 0 }
        // CRIT-3 (audit 2026-05-17): we now hold the typed-in code +
        // attempt count between a failed `submitFoundCode` CF call and a
        // user-triggered retry. The server stamps the winner's timestamp
        // at success, so we no longer pre-build a Winner client-side
        // (which previously caused arrayUnion to skip dedup when a retry
        // landed with a different timestamp).
        var pendingFoundCode: String? = nil
        var pendingWinnerAttempts: Int = 0
        // Raised while a `submitFoundCode` CF call is in flight so a fast
        // double-tap on the submit button can't enqueue the call twice.
        // The CF itself is also idempotent (returns `alreadyWinner` on
        // re-submission of the same hunterId), but this UX gate prevents
        // the second call entirely.
        var isSubmittingWinner: Bool = false

        /// PP-16: flipped to `true` when the game ends (time-out,
        /// zone collapse, all hunters found). `gamePhase` then
        /// reports `.gameOver`, the map stays visible, gameplay
        /// controls grey out, and the GPS effect cancels (no more
        /// `hunterLocations` writes).
        var isGameOver: Bool = false

        /// PP-36: mirrors the chicken-side `isDebugPreview` flag —
        /// when the hunter map is being driven by a previewer (Xcode
        /// canvas, debug tools, snapshot tests) the out-of-zone
        /// penalty must not fire. Live runs always leave this `false`.
        var isDebugPreview: Bool = false

        /// PP-36: timestamp of the last out-of-zone penalty tick.
        /// The 1 s timer fires the decrement only when the previous
        /// tick was ≥ `outOfZonePenaltyIntervalSeconds` ago, so the
        /// penalty is exactly -1 point per 5 s while the hunter
        /// stays outside. Reset to `nil` whenever the hunter is
        /// inside the zone, so re-exit starts a fresh 5 s window
        /// rather than firing immediately on re-cross.
        var lastPenaltyAt: Date? = nil

        // MARK: - MapFeatureState passthroughs (child → parent surface)
        var availablePowerUps: [PowerUp] { powerUps.available }
        var collectedPowerUps: [PowerUp] { powerUps.collected }
        var showPowerUpInventory: Bool { powerUps.showInventory }
        var powerUpNotification: String? { powerUps.notification }
        var lastActivatedPowerUpType: PowerUp.PowerUpType? { powerUps.lastActivatedType }

        var hasChickenStarted: Bool { nowDate >= game.startDate }
        var hasGameStarted: Bool { nowDate >= game.hunterStartDate }
        var isCodeOnCooldown: Bool { codeCooldownUntil.map { nowDate < $0 } ?? false }

        /// Resolves the live game phase, factoring in PP-16's
        /// post-game `isGameOver` flag.
        var gamePhase: PoulePartyAttributes.ContentState.GamePhase {
            if isGameOver { return .gameOver }
            if !hasChickenStarted { return .waitingToStart }
            if !hasGameStarted { return .chickenHeadStart }
            return .hunting
        }

        var liveActivityState: PoulePartyAttributes.ContentState {
            .init(
                radiusMeters: radius,
                nextShrinkDate: nextRadiusUpdate.map { $0.addingTimeInterval(2) },
                activeHunters: max(0, game.hunterIds.count - game.winners.count),
                winnersCount: game.winners.count,
                isOutsideZone: isOutsideZone,
                gamePhase: gamePhase
            )
        }
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case challenges(PresentationAction<ChallengesFeature.Action>)
        case delegate(Delegate)
        case destination(PresentationAction<Destination.Action>)
        case `internal`(Internal)
        case powerUps(MapPowerUpsFeature.Action)
        case view(View)

        @CasePathable
        enum View {
            /// Sent from the view on `ScenePhase.active`. iOS can suspend
            /// the hunter-location writer coroutine while the app is in
            /// the background, which means the chicken sees a stale
            /// marker until the 5 s timer next ticks after resume. The
            /// handler performs one synchronous refresh against
            /// `locationClient.lastLocation()` so the chicken's map
            /// catches up immediately when the player re-opens the app.
            case appBecameActive
            case cancelGameButtonTapped
            case challengesButtonTapped
            case foundButtonTapped
            case gameInfoDismissed
            case infoButtonTapped
            case onTask
            case submitCodeButtonTapped
        }

        @CasePathable
        enum Internal {
            case challengesAvailabilityUpdated(Bool)
            case countdownDismissed
            case gameConfigUpdated(Game)
            case newLocationFetched(CLLocationCoordinate2D)
            /// PP-87: the chicken kept writing its position but with
            /// `invisible: true`. Treat client-side as "no doc" so the
            /// marker disappears for hunters.
            case chickenLocationMasked
            case powerUpCollected(PowerUp)
            case powerUpsUpdated([PowerUp])
            case timerTicked
            case userLocationUpdated(CLLocationCoordinate2D)
            case winnerNotificationDismissed
            case winnerRegistered
            case winnerRegistrationFailed
            /// CRIT-2 (audit 2026-05-17): the `submitFoundCode` CF
            /// rejected the code as wrong. Routes back to the
            /// "Wrong code" alert + cooldown logic that used to
            /// fire on the client-side comparison.
            case wrongCodeRejected
        }

        @CasePathable
        enum Delegate {
            case allHuntersFound
            case returnedToMenu
        }
    }

    @Reducer
    struct Destination {
        @ObservableState
        enum State: Equatable {
            case alert(AlertState<Action.Alert>)
        }

        enum Action {
            case alert(Alert)

            enum Alert: Equatable {
                case leaveGame
                case gameOver
                case wrongCode
                case retryWinnerRegistration
            }
        }
    }

    enum CancelID {
        case powerUpNotificationDismiss
    }

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.userClient) var userClient
    @Dependency(\.continuousClock) var clock
    @Dependency(\.liveActivityClient) var liveActivityClient
    @Dependency(\.locationClient) var locationClient
    @Dependency(\.analyticsClient) var analyticsClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Scope(state: \.powerUps, action: \.powerUps) {
            MapPowerUpsFeature()
        }

        Reduce { state, action in
            switch action {
            case .binding(\.enteredCode):
                state.enteredCode = String(state.enteredCode.prefix(AppConstants.foundCodeDigits))
                return .none
            case .binding:
                return .none
            case .view(.appBecameActive):
                // Force a single hunter-location refresh on foreground
                // resume. The periodic 5 s writer in `.onTask` is the
                // primary source of freshness while the app is alive, but
                // iOS may suspend the background task so the chicken's
                // map shows a stale marker until the first tick after
                // resume. This catches that gap. Guarded on all three of
                // "chicken can see hunters", "hunter phase started", and
                // "we have a known uid + a cached fix" — any of these
                // failing is a silent no-op.
                let gameId = state.game.id
                let hunterId = state.hunterId
                guard state.game.chickenCanSeeHunters || !state.game.gameMasterIds.isEmpty,
                      !hunterId.isEmpty,
                      state.hasGameStarted else {
                    return .none
                }
                return .run { _ in
                    guard let coord = locationClient.lastLocation() else { return }
                    do {
                        try apiClient.setHunterLocation(gameId, hunterId, coord)
                        logger.info("Hunter location refreshed on app resume")
                    } catch {
                        logger.error("Failed to refresh hunter location on resume: \(error)")
                    }
                }
            case .view(.cancelGameButtonTapped):
                state.destination = .alert(
                    AlertState {
                        TextState("Quit game")
                    } actions: {
                        ButtonState(role: .cancel) {
                            TextState("Never mind")
                        }
                        ButtonState(action: .leaveGame) {
                            TextState("Quit")
                        }
                    } message: {
                        TextState("Are you sure you want to quit the game?")
                    }
                )
                return .none
            case .destination(.presented(.alert(.leaveGame))):
                locationClient.stopTracking()
                state.previewCircle = nil
                return .run { send in
                    await liveActivityClient.end(nil)
                    await send(.delegate(.returnedToMenu))
                }
            case .destination(.presented(.alert(.gameOver))):
                // PP-16: the alert closes and the hunter stays on
                // the map (gamePhase is already .gameOver). No auto
                // transition to Victory — PP-18 wires the manual
                // leaderboard CTA.
                state.previewCircle = nil
                let gameId = state.game.id
                return .run { _ in
                    await liveActivityClient.end(nil)
                    do {
                        try await apiClient.updateGameStatus(gameId, .done)
                    } catch {
                        logger.error("Failed to update game status to done: \(error)")
                    }
                }
            case .destination(.presented(.alert(.retryWinnerRegistration))):
                // Must live above the catch-all `case .destination:` below,
                // otherwise the pattern never matches.
                guard let foundCode = state.pendingFoundCode else { return .none }
                guard !state.isSubmittingWinner else { return .none }
                let attempts = state.pendingWinnerAttempts
                let hunterName = state.hunterName
                state.isSubmittingWinner = true
                return submitFoundCodeEffect(
                    gameId: state.game.id,
                    foundCode: foundCode,
                    hunterName: hunterName,
                    attempts: attempts
                )
            case .destination:
                return .none
            case .internal(.countdownDismissed):
                state.countdownNumber = nil
                state.countdownText = nil
                return .none
            case .view(.gameInfoDismissed):
                state.showGameInfo = false
                return .none
            case .internal(.winnerNotificationDismissed):
                state.winnerNotification = nil
                return .none
            case let .internal(.powerUpsUpdated(all)):
                let hunterId = state.hunterId
                let available = all.filter { $0.type.isHunterPowerUp && !$0.isCollected }
                let collected = all.filter { $0.collectedBy == hunterId && $0.activatedAt == nil }
                return .send(.powerUps(.dataUpdated(available: available, collected: collected)))
            case let .internal(.powerUpCollected(powerUp)):
                // Atomic dedup: at 1 Hz a stationary hunter would otherwise
                // fire N duplicate transactions while the first one is still
                // in flight. The reducer runs synchronously, so check-and-
                // insert in the same pass is race-free — subsequent ticks
                // for the same id short-circuit until `collectSucceeded` /
                // `collectFailed` clears the entry.
                guard !state.powerUps.collectingIds.contains(powerUp.id) else { return .none }
                state.powerUps.collectingIds.insert(powerUp.id)
                let gameId = state.game.id
                let hunterId = state.hunterId
                let distance: Double? = state.userLocation.map { userLoc in
                    CLLocation(latitude: userLoc.latitude, longitude: userLoc.longitude)
                        .distance(from: CLLocation(latitude: powerUp.coordinate.latitude, longitude: powerUp.coordinate.longitude))
                }
                let distanceLog = distance.map { String(format: "%.1fm", $0) } ?? "unknown"
                logger.info("Collecting power-up id=\(powerUp.id) type=\(powerUp.type.rawValue) distance=\(distanceLog) hunterId=\(hunterId)")
                return .run { [analyticsClient] send in
                    do {
                        try await apiClient.collectPowerUp(gameId, powerUp.id, hunterId)
                        analyticsClient.powerUpCollected(type: powerUp.type.rawValue, role: "hunter")
                        logger.info("Collected power-up id=\(powerUp.id) type=\(powerUp.type.rawValue)")
                        await send(.powerUps(.collectSucceeded(powerUp)))
                    } catch {
                        logger.error("Failed to collect power-up id=\(powerUp.id) type=\(powerUp.type.rawValue): \(String(describing: error))")
                        await send(.powerUps(.collectFailed(powerUp)))
                    }
                    try await clock.sleep(for: .seconds(2))
                    await send(.powerUps(.notificationCleared))
                }
                .cancellable(id: CancelID.powerUpNotificationDismiss, cancelInFlight: true)
            case let .powerUps(.delegate(.activated(powerUp))):
                // Block a second activation of the same timed effect while
                // the first is still running — otherwise the write
                // overwrites `powerUps.activeEffects.<field>` and shifts
                // the effect window mid-flight. See detailed comment in
                // `ChickenMap.swift`. Mirrored here for Hunter-side
                // effects (radarPing).
                if state.game.isActive(effectOf: powerUp.type) {
                    state.powerUps.notification = "\(powerUp.type.displayName) is already active"
                    state.powerUps.lastActivatedType = powerUp.type
                    return .run { send in
                        try await clock.sleep(for: .seconds(2))
                        await send(.powerUps(.notificationCleared))
                    }
                    .cancellable(id: CancelID.powerUpNotificationDismiss, cancelInFlight: true)
                }
                let gameId = state.game.id
                let duration = powerUp.type.durationSeconds ?? 0
                let expiresAt = Timestamp(date: .now.addingTimeInterval(duration))

                // Zone Preview: compute the NEXT zone boundary client-side.
                // In followTheChicken the zone tracks the Chicken's live GPS,
                // so the next zone will recentre on wherever the Chicken is at
                // shrink time — the best approximation we have right now is
                // the current centre. In stayInTheZone the zone drifts
                // deterministically from `driftSeed`, so we MUST apply the
                // same drift + interpolation that `processRadiusUpdate`
                // applies on the next tick — otherwise the preview shows a
                // circle concentric with the current one, which is exactly
                // the bug the live-test caught ("preview doesn't move").
                if powerUp.type == .zonePreview {
                    let nextRadius = state.radius - Int(state.game.zone.shrinkMetersPerUpdate)
                    if nextRadius > 0, let currentCenter = state.mapCircle?.center {
                        let previewCenter: CLLocationCoordinate2D
                        if state.game.gameMode == .stayInTheZone {
                            let interpolated = interpolateZoneCenter(
                                initialCenter: state.game.zone.center.toCLCoordinates,
                                finalCenter: state.game.finalLocation,
                                initialRadius: state.game.zone.radius,
                                currentRadius: Double(nextRadius)
                            )
                            previewCenter = deterministicDriftCenter(
                                basePoint: interpolated,
                                oldRadius: Double(state.radius),
                                newRadius: Double(nextRadius),
                                driftSeed: state.game.zone.driftSeed
                            )
                        } else {
                            previewCenter = currentCenter
                        }
                        state.previewCircle = CircleOverlay(
                            center: previewCenter,
                            radius: CLLocationDistance(nextRadius)
                        )
                    }
                }

                return .run { [analyticsClient] send in
                    let effectField: String? = powerUp.type == .radarPing ? powerUp.type.firestoreEffectField : nil
                    do {
                        try await apiClient.activatePowerUp(gameId, powerUp.id, effectField, expiresAt)
                    } catch {
                        // Server rejected the activation (rule denied, offline, ...).
                        // The next `gameConfigStream` tick will reconcile the
                        // optimistic UI effect back to the real state; just
                        // log so we notice in production.
                        logger.error("Failed to activate power-up \(powerUp.type.rawValue): \(error.localizedDescription)")
                    }
                    analyticsClient.powerUpActivated(type: powerUp.type.rawValue, role: "hunter")
                    try await clock.sleep(for: .seconds(2))
                    await send(.powerUps(.notificationCleared))
                }
                .cancellable(id: CancelID.powerUpNotificationDismiss, cancelInFlight: true)
            case .powerUps:
                return .none
            case .view(.foundButtonTapped):
                state.isEnteringFoundCode = true
                return .none
            case .view(.submitCodeButtonTapped):
                guard !state.isCodeOnCooldown else {
                    return .none
                }
                // Lock against double-tap: if a winner submission is already
                // in flight, ignore further taps until it resolves.
                guard !state.isSubmittingWinner else {
                    return .none
                }
                let code = state.enteredCode.trimmingCharacters(in: .whitespacesAndNewlines)
                state.enteredCode = ""
                state.isEnteringFoundCode = false

                // CRIT-2 (audit 2026-05-17): the client used to compare
                // `code == state.game.foundCode` here, but foundCode is
                // no longer on the public Game doc (V2.3 moves it to
                // /private/security). The CF re-verifies the code
                // server-side and returns `invalidCode` for misses —
                // routed back to `.wrongCodeRejected` so the existing
                // wrong-code alert + cooldown logic still fires.
                let totalAttempts = state.wrongCodeAttempts + 1
                let hunterName = state.hunterName
                state.pendingFoundCode = code
                state.pendingWinnerAttempts = totalAttempts
                state.isSubmittingWinner = true
                return submitFoundCodeEffect(
                    gameId: state.game.id,
                    foundCode: code,
                    hunterName: hunterName,
                    attempts: totalAttempts
                )
            case .internal(.wrongCodeRejected):
                state.isSubmittingWinner = false
                state.pendingFoundCode = nil
                HapticManager.notification(.error)
                state.wrongCodeAttempts += 1
                analyticsClient.hunterWrongCode(attemptNumber: state.wrongCodeAttempts)
                if state.wrongCodeAttempts >= AppConstants.codeMaxWrongAttempts {
                    state.codeCooldownUntil = .now.addingTimeInterval(AppConstants.codeCooldownSeconds)
                    state.wrongCodeAttempts = 0
                }
                state.destination = .alert(
                    AlertState {
                        TextState("Wrong code")
                    } actions: {
                        ButtonState(action: .wrongCode) {
                            TextState("OK")
                        }
                    } message: {
                        TextState("That code is incorrect. Try again!")
                    }
                )
                return .none
            case .internal(.winnerRegistrationFailed):
                state.isSubmittingWinner = false
                state.destination = .alert(
                    AlertState {
                        TextState("Connection error")
                    } actions: {
                        ButtonState(action: .retryWinnerRegistration) {
                            TextState("Retry")
                        }
                    } message: {
                        TextState("Couldn't register your win. Check your connection and retry, your code was correct.")
                    }
                )
                return .none
            case .delegate(.returnedToMenu):
                return .none
            case .delegate(.allHuntersFound):
                return .none
            case .internal(.winnerRegistered):
                state.isSubmittingWinner = false
                let endState = PoulePartyAttributes.ContentState(
                    radiusMeters: state.radius,
                    nextShrinkDate: nil,
                    activeHunters: max(0, state.game.hunterIds.count - state.game.winners.count),
                    winnersCount: state.game.winners.count,
                    isOutsideZone: false,
                    gamePhase: .gameOver
                )
                return .run { _ in
                    await liveActivityClient.end(endState)
                }
            case .view(.infoButtonTapped):
                state.showGameInfo = true
                return .none
            case .view(.challengesButtonTapped):
                // Prefer the team name from the hunter's registration; fall back
                // to the stored hunter name so the leaderboard still has something
                // to show.
                let gameId = state.game.id
                let hunterId = state.hunterId
                let hunterIds = state.game.hunterIds
                let fallbackName = state.hunterName
                state.challenges = ChallengesFeature.State(
                    gameId: gameId,
                    hunterId: hunterId,
                    hunterIds: hunterIds,
                    myTeamName: fallbackName,
                    isClosedForSubmissions: state.isGameOver
                )
                return .run { [apiClient] send in
                    if let registration = try? await apiClient.findRegistration(gameId, hunterId),
                       !registration.teamName.isEmpty {
                        await send(.challenges(.presented(.binding(.set(\.myTeamName, registration.teamName)))))
                    }
                }
            case .challenges:
                return .none
            case let .internal(.newLocationFetched(location)):
                // `location` here is the chicken's broadcasted position —
                // `chickenLocationStream` is the only producer of this action.
                // Always cache it in `state.chickenLocation` so Radar Ping
                // has a fresh point to reveal; the UI gates rendering on
                // `game.isRadarPingActive`.
                state.chickenLocation = location
                // Treating the chicken's position as the zone center is
                // correct in `followTheChicken`, but wrong in `stayInTheZone`:
                // the zone center there is the deterministic drifted center
                // computed in `.gameUpdated` / `processRadiusUpdate`. A stray
                // chicken broadcast (radar-ping write, or a stale
                // `chickenLocations/latest` doc the listener replays on
                // connect) must not overwrite it — otherwise the hunter's
                // zone check fires against the chicken's position rather
                // than the real zone and flags the hunter as "outside"
                // even when they're standing inside the visible circle.
                // Mirrors the gate in `ChickenMap.swift:newLocationFetched`.
                if state.game.gameMode != .stayInTheZone {
                    state.mapCircle = CircleOverlay(
                        center: location,
                        radius: CLLocationDistance(state.radius)
                    )
                }
                return .none
            case .internal(.chickenLocationMasked):
                state.chickenLocation = nil
                return .none
            case .view(.onTask):
                let rawUid = userClient.currentUserId()
                let gameId = state.game.id
                if let uid = rawUid, !uid.isEmpty {
                    state.hunterId = uid
                }
                let hunterId = state.hunterId
                guard !hunterId.isEmpty else {
                    logger.error("hunterId is empty — cannot register hunter or write location. rawUid was: \(rawUid ?? "nil")")
                    return .none
                }
                let powerUpsEnabled = state.game.powerUps.enabled
                let hunterStartDate = state.game.hunterStartDate
                let (lastUpdate, lastRadius) = state.game.findLastUpdate()
                state.radius = lastRadius
                state.nextRadiusUpdate = lastUpdate

                // Start Live Activity
                let attributes = PoulePartyAttributes(
                    gameName: state.game.name,
                    gameCode: state.game.gameCode,
                    playerRole: .hunter,
                    gameModeName: state.game.gameMode.title,
                    gameStartDate: state.game.startDate,
                    gameEndDate: state.game.endDate,
                    totalHunters: max(0, state.game.maxPlayers - 1)
                )
                let initialLAState = state.liveActivityState
                state.lastLiveActivityState = initialLAState

                let gameMode = state.game.gameMode.rawValue
                let gameCode = state.game.gameCode
                var effects: [Effect<Action>] = [
                    .run { _ in
                        await liveActivityClient.start(attributes, initialLAState)
                    },
                    .run { [analyticsClient, hunterName = state.hunterName] send in
                        do {
                            try await apiClient.registerHunter(gameId, hunterId)
                            analyticsClient.gameJoined(gameMode: gameMode, gameCode: gameCode)
                            // Backfill the `registrations` doc so the GameMaster
                            // map can label the marker with the team name even
                            // when the hunter came in via "Reprendre la partie"
                            // or rejoined an old game that pre-dates PP-90.
                            // The JoinFlow already creates this doc on first
                            // join — this is the idempotent safety net.
                            let teamName = hunterName.trimmingCharacters(in: .whitespacesAndNewlines)
                            if !teamName.isEmpty {
                                let registration = Registration(userId: hunterId, teamName: teamName)
                                try? await apiClient.createRegistration(gameId, registration)
                            }
                        } catch {
                            logger.error("Failed to register hunter: \(error.localizedDescription)")
                        }
                    },
                    .run { send in
                        for await game in apiClient.gameConfigStream(gameId) {
                            if let game {
                                await send(.internal(.gameConfigUpdated( game)))
                            }
                        }
                    },
                    .run { send in
                        for await _ in self.clock.timer(interval: .seconds(1)) {
                            await send(.internal(.timerTicked))
                        }
                    },
                    .run { send in
                        guard powerUpsEnabled else { return }
                        for await powerUps in apiClient.powerUpsStream(gameId) {
                            await send(.internal(.powerUpsUpdated(powerUps)))
                        }
                    }
                ]

                // Subscribe to chicken location stream in all modes.
                // In followTheChicken: circle follows the chicken continuously.
                // In stayInTheZone: chicken only writes when radarPing is active,
                //   so hunter will only receive updates during pings.
                // Gated behind hunterStartDate to avoid leaking position early.
                effects.append(
                    .run { send in
                        let delay = hunterStartDate.timeIntervalSinceNow
                        if delay > 0 {
                            try await clock.sleep(for: .seconds(delay))
                        }
                        for await chickenLoc in apiClient.chickenLocationStream(gameId) {
                            if let chickenLoc, !(chickenLoc.invisible ?? false) {
                                let coordinate = CLLocationCoordinate2D(
                                    latitude: chickenLoc.location.latitude,
                                    longitude: chickenLoc.location.longitude
                                )
                                await send(.internal(.newLocationFetched(coordinate)))
                            } else {
                                // PP-87: doc missing OR `invisible: true`.
                                // Mask the marker so the hunter sees no
                                // chicken (preserves pre-PP-87 behavior).
                                await send(.internal(.chickenLocationMasked))
                            }
                        }
                    }
                )

                // Hunter always tracks own location (for zone check).
                // When chickenCanSeeHunters, also writes to Firestore.
                // Gated behind hunterStartDate.
                //
                // Pre-1.11.2 we wrote only when CoreLocation emitted a new
                // coord. With `distanceFilter = 10 m`, a stationary hunter
                // produced zero fixes and therefore zero writes — the
                // chicken saw a frozen marker for as long as the player
                // sat still. 1.11.2 splits the work across two parallel
                // effects:
                //   1. Tracker — pushes each incoming GPS fix into both
                //      reducer state (`userLocation`, for zone checks +
                //      power-up proximity) and a shared `LockIsolated`
                //      cache. No Firestore write happens here.
                //   2. Writer — a `clock.timer` that fires every
                //      `locationThrottleSeconds` and re-broadcasts the
                //      latest cached coord so a stationary hunter still
                //      refreshes on the chicken's map. Only started when
                //      `chickenCanSeeHunters` is true.
                // A separate `.view(.appBecameActive)` handler writes one
                // immediate refresh on every foreground resume in case
                // iOS suspended the writer during background.
                // PP-24: hunters also write their position when at least
                // one GameMaster has joined, so the GM observer map can
                // render them even in `stayInTheZone`. Firestore rules
                // restrict hunter location reads to creator + chicken +
                // hunters + gameMasters, so privacy is preserved.
                let shouldWriteLocation = state.game.chickenCanSeeHunters
                    || !state.game.gameMasterIds.isEmpty
                let latestLocation = LockIsolated<CLLocationCoordinate2D?>(locationClient.lastLocation())
                effects.append(
                    .run { send in
                        let delay = hunterStartDate.timeIntervalSinceNow
                        if delay > 0 {
                            try await clock.sleep(for: .seconds(delay))
                        }
                        if let currentLocation = locationClient.lastLocation() {
                            latestLocation.setValue(currentLocation)
                            await send(.internal(.userLocationUpdated(currentLocation)))
                        }
                        for await coordinate in locationClient.startTracking() {
                            latestLocation.setValue(coordinate)
                            await send(.internal(.userLocationUpdated(coordinate)))
                        }
                    }
                )
                if shouldWriteLocation {
                    effects.append(
                        .run { _ in
                            let delay = hunterStartDate.timeIntervalSinceNow
                            if delay > 0 {
                                try await clock.sleep(for: .seconds(delay))
                            }
                            // Poll at 100 ms until we have a coord to send.
                            // On a cold start without a cached `lastLocation()`
                            // we'd otherwise wait the full 5 s throttle
                            // window for `clock.timer` to fire before the
                            // chicken saw the hunter's first position.
                            while latestLocation.value == nil {
                                try await clock.sleep(for: .milliseconds(100))
                            }
                            if let coord = latestLocation.value {
                                do {
                                    try apiClient.setHunterLocation(gameId, hunterId, coord)
                                } catch {
                                    logger.error("Failed to send initial hunter location: \(error)")
                                }
                            }
                            for await _ in clock.timer(interval: .seconds(AppConstants.locationThrottleSeconds)) {
                                guard let coord = latestLocation.value else { continue }
                                do {
                                    try apiClient.setHunterLocation(gameId, hunterId, coord)
                                } catch {
                                    logger.error("Failed to send hunter location: \(error)")
                                }
                            }
                        }
                    )
                }

                effects.append(
                    .run { send in
                        for await challenges in apiClient.challengesStream() {
                            await send(.internal(.challengesAvailabilityUpdated(!challenges.isEmpty)))
                        }
                    }
                )

                return .merge(effects)
            case let .internal(.challengesAvailabilityUpdated(hasChallenges)):
                state.hasChallenges = hasChallenges
                return .none
            case let .internal(.gameConfigUpdated(game)):
                // React to game cancelled/ended by chicken or Cloud Function
                if game.status == .done, state.destination == nil {
                    locationClient.stopTracking()
                    state.game = game
                    let endState = PoulePartyAttributes.ContentState(
                        radiusMeters: state.radius,
                        nextShrinkDate: nil,
                        activeHunters: max(0, game.hunterIds.count - game.winners.count),
                        winnersCount: game.winners.count,
                        isOutsideZone: false,
                        gamePhase: .gameOver
                    )
                    state.destination = .alert(
                        AlertState {
                            TextState("Game Over")
                        } actions: {
                            ButtonState(action: .gameOver) {
                                TextState("OK")
                            }
                        } message: {
                            TextState("The game has ended!")
                        }
                    )
                    return .run { _ in
                        await liveActivityClient.end(endState)
                    }
                }

                let (lastUpdate, lastRadius) = game.findLastUpdate()

                let activatedPowerUp = detectActivatedPowerUp(oldGame: state.game, newGame: game)

                state.game = game
                state.radius = lastRadius
                state.nextRadiusUpdate = lastUpdate

                if game.gameMode != .stayInTheZone {
                    if let currentCircle = state.mapCircle {
                        state.mapCircle = CircleOverlay(
                            center: currentCircle.center,
                            radius: CLLocationDistance(state.radius)
                        )
                    }
                    // else: no chicken location yet, leave mapCircle nil
                } else {
                    let interpolatedCenter = interpolateZoneCenter(
                        initialCenter: game.zone.center.toCLCoordinates,
                        finalCenter: game.finalLocation,
                        initialRadius: game.zone.radius,
                        currentRadius: Double(lastRadius)
                    )
                    state.mapCircle = CircleOverlay(
                        center: interpolatedCenter,
                        radius: CLLocationDistance(state.radius)
                    )
                }

                // Decoy: show a fake chicken marker when decoy is active
                if game.isDecoyActive {
                    if state.decoyLocation == nil, let center = state.mapCircle?.center {
                        // Deterministic fake location so all hunters see the same decoy.
                        // If the decoy timestamp fails to decode for any reason, fall back
                        // to 0 so we still place a consistent fake rather than crashing.
                        let decoyDate = game.powerUps.activeEffects.decoy?.dateValue()
                        let decoyTimestamp = decoyDate.map { Int($0.timeIntervalSince1970) } ?? 0
                        let seed = game.zone.driftSeed ^ decoyTimestamp
                        let angle = seededRandom(seed: seed, index: 0) * 2 * .pi
                        let distance = (200 + seededRandom(seed: seed, index: 1) * 300) / 111_320.0
                        let cosLat = cos(center.latitude * .pi / 180)
                        // Near the poles cosLat → 0; guard against division blowing up.
                        let safeCosLat = abs(cosLat) > 1e-9 ? cosLat : 1e-9
                        let lat = center.latitude + distance * cos(angle)
                        let lng = center.longitude + distance * sin(angle) / safeCosLat
                        if lat.isFinite, lng.isFinite {
                            state.decoyLocation = CLLocationCoordinate2D(latitude: lat, longitude: lng)
                        }
                    }
                } else {
                    state.decoyLocation = nil
                }

                // Update Live Activity with new game state
                var effects: [Effect<Action>] = []
                if let laUpdate = checkLiveActivityUpdate(
                    currentState: state.liveActivityState,
                    lastState: state.lastLiveActivityState
                ) {
                    state.lastLiveActivityState = laUpdate.newState
                    effects.append(.run { _ in
                        await liveActivityClient.update(laUpdate.newState)
                    })
                }

                // Show global power-up notification
                if let activated = activatedPowerUp {
                    effects.append(.send(.powerUps(.notificationShown(text: activated.text, type: activated.type))))
                    effects.append(
                        .run { send in
                            try await clock.sleep(for: .seconds(2))
                            await send(.powerUps(.notificationCleared))
                        }
                        .cancellable(id: CancelID.powerUpNotificationDismiss, cancelInFlight: true)
                    )
                }

                // Detect new winners
                if state.previousWinnersCount >= 0 {
                    if let notification = detectNewWinners(
                        winners: game.winners,
                        previousCount: state.previousWinnersCount,
                        ownHunterId: state.hunterId
                    ) {
                        state.winnerNotification = notification
                        state.previousWinnersCount = game.winners.count
                        effects.append(.run { send in
                            try await clock.sleep(for: .seconds(AppConstants.winnerNotificationSeconds))
                            await send(.internal(.winnerNotificationDismissed))
                        })
                    }
                } else {
                    state.previousWinnersCount = game.winners.count
                }

                // PP-16: end the game when all hunters have found the
                // chicken. Stay on the map, grey controls via
                // `isGameOver`. The chicken is authoritative for the
                // `status = .done` Firestore write — the hunter just
                // flips its local phase + cancels GPS.
                if !state.isGameOver &&
                   state.destination == nil &&
                   !game.hunterIds.isEmpty &&
                   game.winners.count >= game.hunterIds.count {
                    state.isGameOver = true
                    locationClient.stopTracking()
                    effects.append(.run { _ in
                        await liveActivityClient.end(nil)
                    })
                }

                return effects.isEmpty ? .none : .merge(effects)
            case .internal(.timerTicked):
                state.nowDate = .now

                // Countdown phases (hunter perspective)
                let countdownResult = evaluateCountdown(
                    phases: [
                        CountdownPhase(
                            targetDate: state.game.startDate,
                            completionText: "🐔 is hiding!",
                            showNumericCountdown: true,
                            isEnabled: state.game.timing.headStartMinutes > 0
                        ),
                        CountdownPhase(
                            targetDate: state.game.hunterStartDate,
                            completionText: "LET'S HUNT! 🔍",
                            showNumericCountdown: true,
                            isEnabled: true
                        )
                    ],
                    currentCountdownNumber: state.countdownNumber,
                    currentCountdownText: state.countdownText
                )
                switch countdownResult {
                case .noChange:
                    break
                case .updateNumber(let n):
                    state.countdownNumber = n
                    state.countdownText = nil
                case .showText(let text):
                    state.countdownNumber = nil
                    state.countdownText = text
                    return .run { send in
                        try await clock.sleep(for: .seconds(AppConstants.countdownDisplaySeconds))
                        await send(.internal(.countdownDismissed))
                    }
                }

                guard state.destination == nil else { return .none }
                guard state.hasGameStarted else { return .none }

                // Game over by time
                if checkGameOverByTime(endDate: state.game.endDate) {
                    HapticManager.notification(.warning)
                    state.isGameOver = true
                    locationClient.stopTracking()
                    let endState = PoulePartyAttributes.ContentState(
                        radiusMeters: state.radius,
                        nextShrinkDate: nil,
                        activeHunters: max(0, state.game.hunterIds.count - state.game.winners.count),
                        winnersCount: state.game.winners.count,
                        isOutsideZone: false,
                        gamePhase: .gameOver
                    )
                    state.destination = .alert(
                        AlertState {
                            TextState("Game Over")
                        } actions: {
                            ButtonState(action: .gameOver) {
                                TextState("OK")
                            }
                        } message: {
                            TextState("Time's up! The Chicken survived!")
                        }
                    )
                    return .run { _ in
                        await liveActivityClient.end(endState)
                    }
                }

                // Radius update
                if let result = processRadiusUpdate(
                    nextRadiusUpdate: state.nextRadiusUpdate,
                    currentRadius: state.radius,
                    radiusDeclinePerUpdate: state.game.zone.shrinkMetersPerUpdate,
                    radiusIntervalUpdate: state.game.zone.shrinkIntervalMinutes,
                    gameMod: state.game.gameMode,
                    initialCoordinates: state.game.zone.center.toCLCoordinates,
                    currentCircle: state.mapCircle,
                    driftSeed: state.game.zone.driftSeed,
                    isZoneFrozen: state.game.isZoneFrozen,
                    finalCoordinates: state.game.finalLocation,
                    initialRadius: state.game.zone.radius
                ) {
                    if result.isGameOver {
                        HapticManager.notification(.warning)
                        state.isGameOver = true
                        locationClient.stopTracking()
                        let endState = PoulePartyAttributes.ContentState(
                            radiusMeters: 0,
                            nextShrinkDate: nil,
                            activeHunters: max(0, state.game.hunterIds.count - state.game.winners.count),
                            winnersCount: state.game.winners.count,
                            isOutsideZone: false,
                            gamePhase: .gameOver
                        )
                        state.destination = .alert(
                            AlertState {
                                TextState("Game Over")
                            } actions: {
                                ButtonState(action: .gameOver) {
                                    TextState("OK")
                                }
                            } message: {
                                TextState(result.gameOverMessage ?? "Game over")
                            }
                        )
                        return .run { _ in
                            await liveActivityClient.end(endState)
                        }
                    }
                    state.radius = result.newRadius
                    state.nextRadiusUpdate = result.newNextUpdate
                    state.mapCircle = result.newCircle
                    // Clear zone preview on actual zone shrink
                    state.previewCircle = nil
                }

                // Power-up proximity check — collect all nearby power-ups
                let nearbyPowerUps = findNearbyPowerUps(
                    userLocation: state.userLocation,
                    availablePowerUps: state.availablePowerUps
                )
                if !nearbyPowerUps.isEmpty {
                    return .merge(nearbyPowerUps.map { .send(.internal(.powerUpCollected($0))) })
                }

                // Zone check (visual warning only — no elimination)
                if shouldCheckZone(role: .hunter, gameMod: state.game.gameMode),
                   let userLoc = state.userLocation,
                   let circle = state.mapCircle {
                    let zoneResult = checkZoneStatus(
                        userLocation: userLoc,
                        zoneCenter: circle.center,
                        zoneRadius: circle.radius
                    )
                    state.isOutsideZone = zoneResult.isOutsideZone
                }

                // PP-36: out-of-zone penalty (-1 point / 5 s).
                // Phase gates: only while the hunt is actually running.
                // `hasGameStarted` (hunter start passed) is already
                // checked above; we additionally exclude `isGameOver`
                // and the chicken's debug preview screen. The
                // `lastPenaltyAt` guard is computed against `nowDate`
                // so the very first out-of-zone tick fires after a
                // full 5 s, not immediately on re-cross — matches the
                // 4 s → 0 / 12 s → -2 acceptance criteria.
                if state.isOutsideZone,
                   !state.isGameOver,
                   !state.isDebugPreview,
                   !state.hunterId.isEmpty {
                    let now = state.nowDate
                    let dueAt = state.lastPenaltyAt
                        .map { $0.addingTimeInterval(AppConstants.outOfZonePenaltyIntervalSeconds) }
                    if dueAt == nil {
                        // First out-of-zone tick: start the 5 s window.
                        state.lastPenaltyAt = now
                    } else if let due = dueAt, now >= due {
                        state.lastPenaltyAt = now
                        let gameId = state.game.id
                        let hunterId = state.hunterId
                        return .run { _ in
                            do {
                                try await apiClient.decrementTotalPoints(gameId, hunterId)
                            } catch {
                                logger.error("Out-of-zone penalty write failed: \(error.localizedDescription)")
                            }
                        }
                    }
                } else if !state.isOutsideZone, state.lastPenaltyAt != nil {
                    // Back inside the zone → reset the window so the
                    // next exit starts a fresh 5 s countdown.
                    state.lastPenaltyAt = nil
                }

                // Update Live Activity only when state meaningfully changes
                if let laUpdate = checkLiveActivityUpdate(
                    currentState: state.liveActivityState,
                    lastState: state.lastLiveActivityState
                ) {
                    state.lastLiveActivityState = laUpdate.newState
                    return .run { _ in
                        await liveActivityClient.update(laUpdate.newState)
                    }
                }
                return .none
            case let .internal(.userLocationUpdated(location)):
                state.userLocation = location
                return .none
            }
        }
        .ifLet(\.$destination, action: \.destination) {
          Destination()
        }
        .ifLet(\.$challenges, action: \.challenges) {
          ChallengesFeature()
        }
    }

    /// CRIT-3 (audit 2026-05-17): runs the `submitFoundCode` callable
    /// CF and branches to either the "registered" or "failed" internal
    /// action. `alreadyWinner` is treated as success — the server has
    /// the winner recorded from an earlier attempt and the UX should
    /// proceed to Victory.
    private func submitFoundCodeEffect(
        gameId: String,
        foundCode: String,
        hunterName: String,
        attempts: Int
    ) -> Effect<Action> {
        .run { [analyticsClient, apiClient, locationClient] send in
            do {
                try await apiClient.submitFoundCode(gameId, foundCode, hunterName)
                analyticsClient.hunterFoundChicken(attempts: attempts)
                locationClient.stopTracking()
                await send(.internal(.winnerRegistered))
            } catch let err as SubmitFoundCodeError {
                switch err {
                case .alreadyWinner:
                    // Idempotent: the server already has us as a
                    // winner. Treat as success so the UI proceeds to
                    // Victory.
                    analyticsClient.hunterFoundChicken(attempts: attempts)
                    locationClient.stopTracking()
                    await send(.internal(.winnerRegistered))
                case .invalidCode:
                    // CRIT-2 (audit 2026-05-17): the server rejected
                    // the code. Route back to the wrong-code UX (alert
                    // + cooldown) instead of the network-failure
                    // retry alert.
                    await send(.internal(.wrongCodeRejected))
                default:
                    logger.error("submitFoundCode rejected: \(String(describing: err))")
                    await send(.internal(.winnerRegistrationFailed))
                }
            } catch {
                logger.error("submitFoundCode failed: \(error.localizedDescription)")
                await send(.internal(.winnerRegistrationFailed))
            }
        }
    }
}

