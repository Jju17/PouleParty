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
        /// PP-zone-stored: ordered circle schedule read once from
        /// `/games/{id}/zone/schedule`; runtime renders `circles[activeIndex]`.
        var circles: [ZoneCircle] = []
        var zoneScheduleError: String?
        var showGameInfo: Bool = false
        var winnerNotification: String?
        var countdownNumber: Int?
        var countdownText: String?
        var wrongCodeAttempts: Int = 0
        var codeCooldownUntil: Date?
        var isLeaving: Bool = false
        var userLocation: CLLocationCoordinate2D?
        var isOutsideZone: Bool = false
        var lastLiveActivityState: PoulePartyAttributes.ContentState?
        var powerUps: MapPowerUpsFeature.State = .init()
        var previewCircle: CircleOverlay?
        var decoyLocation: CLLocationCoordinate2D?
        // Latest known Chicken position broadcasted via
        // `chickenLocationsStream`. Tracked in every mode (not just
        // followTheChicken) so Radar Ping has a fresh point to reveal
        // the moment it's activated. Hunter-map rendering gates
        // visibility on `game.isRadarPingActive`, without that gate
        // this would be a free locator.
        var chickenLocation: CLLocationCoordinate2D?
        var hasChallenges: Bool = false
        var pendingFoundCode: String?
        var pendingWinnerAttempts: Int = 0
        // Raised while a `submitFoundCode` CF call is in flight so a fast
        // double-tap on the submit button can't enqueue the call twice.
        // The CF itself is also idempotent (returns `alreadyWinner` on
        // re-submission of the same hunterId), but this UX gate prevents
        // the second call entirely.
        var isSubmittingWinner: Bool = false

        var isGameOver: Bool = false

        var lastPenaltyAt: Date?

        // MARK: - MapFeatureState passthroughs (child → parent surface)
        var availablePowerUps: [PowerUp] { powerUps.available }
        var collectedPowerUps: [PowerUp] { powerUps.collected }
        var showPowerUpInventory: Bool { powerUps.showInventory }
        var powerUpNotification: String? { powerUps.notification }
        var lastActivatedPowerUpType: PowerUp.PowerUpType? { powerUps.lastActivatedType }

        var hasChickenStarted: Bool { nowDate >= game.startDate }
        var hasGameStarted: Bool { nowDate >= game.hunterStartDate }
        var isCodeOnCooldown: Bool { codeCooldownUntil.map { nowDate < $0 } ?? false }

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
            case retryScheduleTapped
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
            /// Banner tap at game-end → navigate to the Victory /
            /// leaderboard page (parent handles via `gameEnded` delegate).
            case viewLeaderboardTapped
        }

        @CasePathable
        enum Internal {
            case challengesAvailabilityUpdated(Bool)
            case countdownDismissed
            case gameConfigUpdated(Game)
            case newLocationFetched(CLLocationCoordinate2D)
            case chickenLocationMasked
            case powerUpCollected(PowerUp)
            case powerUpsUpdated([PowerUp])
            case scheduleLoaded([ZoneCircle])
            case scheduleLoadFailed(String)
            case timerTicked
            case userLocationUpdated(CLLocationCoordinate2D)
            case winnerNotificationDismissed
            case winnerRegistered
            case winnerRegistrationFailed
            case wrongCodeRejected(lockedUntil: Date?)
            case leaveFailed(String)
            case teamNameResolved(String)
        }

        @CasePathable
        enum Delegate {
            case returnedToMenu
            /// Game ended for any reason (chicken cancelled, timer
            /// expired, all hunters found). Parent navigates to the
            /// Victory / leaderboard screen.
            case gameEnded(Game)
            case becameChicken(Game)
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
                case leaveCancelled
                case wrongCode
                case retryWinnerRegistration
            }
        }
    }

    enum CancelID {
        case powerUpNotificationDismiss
        case timer
        case runtime
    }

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.now) var now
    @Dependency(\.userClient) var userClient
    @Dependency(\.continuousClock) var clock
    @Dependency(\.liveActivityClient) var liveActivityClient
    @Dependency(\.locationClient) var locationClient
    @Dependency(\.analyticsClient) var analyticsClient
    @Dependency(\.remoteConfigClient) var remoteConfigClient

    private func loadScheduleEffect(_ gameId: String) -> Effect<Action> {
        zoneScheduleEffect(
            gameId: gameId,
            apiClient: apiClient,
            clock: clock,
            loaded: { .internal(.scheduleLoaded($0)) },
            failed: { .internal(.scheduleLoadFailed($0)) }
        )
    }

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
                // "we have a known uid + a cached fix", any of these
                // failing is a silent no-op.
                let gameId = state.game.id
                let hunterId = state.hunterId
                guard state.game.chickenCanSeeHunters || !state.game.gameMasterIds.isEmpty,
                      !hunterId.isEmpty,
                      !state.isGameOver,
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
                guard !state.isLeaving else { return .none }
                state.isLeaving = true
                let gameId = state.game.id
                let needsServerLeave = state.game.status != .done
                return .run { send in
                    do {
                        if needsServerLeave { try await apiClient.leaveGame(gameId) }
                    } catch {
                        logger.warning("[leave] leaveGame failed: \(error.localizedDescription)")
                        await send(.internal(.leaveFailed(error.userMessage)))
                        return
                    }
                    locationClient.stopTracking()
                    await liveActivityClient.end(nil)
                    await send(.delegate(.returnedToMenu))
                }
            case let .internal(.leaveFailed(message)):
                state.isLeaving = false
                state.destination = .alert(
                    AlertState {
                        TextState("Could not leave the game")
                    } actions: {
                        ButtonState(role: .cancel, action: .leaveCancelled) {
                            TextState("Cancel")
                        }
                        ButtonState(action: .leaveGame) {
                            TextState("Try again")
                        }
                    } message: {
                        TextState(message)
                    }
                )
                return .none
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
            case .view(.viewLeaderboardTapped):
                return .send(.delegate(.gameEnded(state.game)))
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
                // insert in the same pass is race-free, subsequent ticks
                // for the same id short-circuit until `collectSucceeded` /
                // `collectFailed` clears the entry.
                guard let location = state.userLocation,
                      !state.powerUps.collectingIds.contains(powerUp.id) else { return .none }
                state.powerUps.collectingIds.insert(powerUp.id)
                let gameId = state.game.id
                let hunterId = state.hunterId
                logger.info("Collecting power-up id=\(powerUp.id) type=\(powerUp.type.rawValue) distance=\(String(format: "%.1fm", distanceMeters(location, powerUp.coordinate))) hunterId=\(hunterId)")
                return .run { [analyticsClient] send in
                    do {
                        try await apiClient.collectPowerUp(gameId, powerUp.id, location)
                        analyticsClient.powerUpCollected(type: powerUp.type.rawValue, role: "hunter")
                        logger.info("Collected power-up id=\(powerUp.id) type=\(powerUp.type.rawValue)")
                        await send(.powerUps(.collectSucceeded(powerUp)))
                    } catch {
                        logger.error("Failed to collect power-up id=\(powerUp.id) type=\(powerUp.type.rawValue): \(String(describing: error))")
                        await send(.powerUps(.collectFailed(powerUp, message: error.userMessage)))
                    }
                    try await clock.sleep(for: .seconds(2))
                    await send(.powerUps(.notificationCleared))
                }
                .cancellable(id: CancelID.powerUpNotificationDismiss, cancelInFlight: true)
            case let .powerUps(.delegate(.activated(powerUp))):
                // Block a second activation of the same timed effect while
                // the first is still running, otherwise the write
                // overwrites `powerUps.activeEffects.<field>` and shifts
                // the effect window mid-flight. See detailed comment in
                // `ChickenMap.swift`. Mirrored here for Hunter-side
                // effects (radarPing).
                if state.game.isActive(effectOf: powerUp.type) {
                    state.powerUps.notification = String(localized: "\(powerUp.type.displayName) is already active")
                    state.powerUps.lastActivatedType = powerUp.type
                    return .run { send in
                        try await clock.sleep(for: .seconds(2))
                        await send(.powerUps(.notificationCleared))
                    }
                    .cancellable(id: CancelID.powerUpNotificationDismiss, cancelInFlight: true)
                }
                let gameId = state.game.id

                // PP-zone-stored: the NEXT zone boundary is just the next entry
                // in the stored schedule, no client recompute. In
                // followTheChicken the next circle recentres on the Chicken's
                // live GPS, so keep the current center and preview only the
                // next radius.
                if powerUp.type == .zonePreview {
                    let active = selectActiveCircle(
                        hunterStartDate: state.game.hunterStartDate,
                        shrinkIntervalMinutes: state.game.zone.shrinkIntervalMinutes,
                        circleCount: state.circles.count,
                        freezeEnd: state.game.powerUps.activeEffects.zoneFreeze?.dateValue(),
                        freezeDuration: PowerUp.PowerUpType.zoneFreeze.durationSeconds ?? 0
                    )
                    if state.circles.indices.contains(active.circleIndex + 1) {
                        let nextCircle = state.circles[active.circleIndex + 1]
                        let previewCenter: CLLocationCoordinate2D
                        if state.game.gameMode == .stayInTheZone {
                            previewCenter = nextCircle.center
                        } else {
                            previewCenter = state.mapCircle?.center ?? nextCircle.center
                        }
                        state.previewCircle = CircleOverlay(
                            center: previewCenter,
                            radius: CLLocationDistance(nextCircle.radiusMeters)
                        )
                    }
                }

                return .run { [analyticsClient] send in
                    do {
                        try await apiClient.activatePowerUp(gameId, powerUp.id)
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
            case let .internal(.wrongCodeRejected(lockedUntil)):
                state.isSubmittingWinner = false
                state.pendingFoundCode = nil
                HapticManager.notification(.error)
                state.wrongCodeAttempts += 1
                analyticsClient.hunterWrongCode(attemptNumber: state.wrongCodeAttempts)
                if state.wrongCodeAttempts >= remoteConfigClient.codeMaxWrongAttempts() {
                    state.codeCooldownUntil = now.now.addingTimeInterval(remoteConfigClient.codeCooldownSeconds())
                    state.wrongCodeAttempts = 0
                }
                if let lockedUntil, lockedUntil > (state.codeCooldownUntil ?? .distantPast) {
                    state.codeCooldownUntil = lockedUntil
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
            case .delegate(.gameEnded):
                return .none
            case .delegate(.becameChicken):
                return .none
            case .internal(.winnerRegistered):
                state.isSubmittingWinner = false
                let endState = gameOverLiveActivityState(game: state.game, radius: state.radius)
                return .merge(.cancel(id: CancelID.runtime), .run { _ in
                    await liveActivityClient.end(endState)
                })
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
                    let registration: Registration?
                    do {
                        registration = try await apiClient.findRegistration(gameId, hunterId)
                    } catch {
                        logger.warning("[challenges] team name lookup failed: \(error.localizedDescription)")
                        registration = nil
                    }
                    if let registration, !registration.teamName.isEmpty {
                        await send(.internal(.teamNameResolved(registration.teamName)))
                    }
                }
            case .challenges:
                return .none
            case let .internal(.teamNameResolved(teamName)):
                state.challenges?.myTeamName = teamName
                return .none
            case let .internal(.newLocationFetched(location)):
                // `location` here is the chicken's broadcasted position,
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
                // connect) must not overwrite it, otherwise the hunter's
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
                    logger.error("hunterId is empty: cannot register hunter or write location. rawUid was: \(rawUid ?? "nil")")
                    return .none
                }
                let powerUpsEnabled = state.game.powerUps.enabled
                let hunterStartDate = state.game.hunterStartDate
                // PP-zone-stored: seed from the zone center; real geometry
                // arrives in `.scheduleLoaded` (fetch appended below).
                state.radius = Int(state.game.zone.radius)
                state.nextRadiusUpdate = state.game.hunterStartDate
                let scheduleGameId = state.game.id

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
                    .run { [analyticsClient, hunterName = state.hunterName] _ in
                        let teamName = hunterName.trimmingCharacters(in: .whitespacesAndNewlines)
                        do {
                            try await apiClient.joinGame(gameId, teamName)
                            analyticsClient.gameJoined(gameMode: gameMode, gameCode: gameCode)
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
                    }
                    .cancellable(id: CancelID.timer),
                    .run { send in
                        guard powerUpsEnabled else { return }
                        for await powerUps in apiClient.powerUpsStream(gameId) {
                            await send(.internal(.powerUpsUpdated(powerUps)))
                        }
                    },
                    loadScheduleEffect(scheduleGameId)
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
                                await send(.internal(.chickenLocationMasked))
                            }
                        }
                    }
                )

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
                        for await challenges in apiClient.challengesStream(gameId) {
                            await send(.internal(.challengesAvailabilityUpdated(!challenges.isEmpty)))
                        }
                    }
                )

                return .merge(effects).cancellable(id: CancelID.runtime, cancelInFlight: true)
            case let .internal(.challengesAvailabilityUpdated(hasChallenges)):
                state.hasChallenges = hasChallenges
                return .none
            case let .internal(.gameConfigUpdated(game)):
                if game.isChicken(state.hunterId) {
                    return .send(.delegate(.becameChicken(game)))
                }

                // React to game cancelled/ended by chicken or Cloud Function.
                // The hunter stays on the map with `isGameOver = true` so
                // the "Game ended" banner appears; tapping the banner
                // fires `viewLeaderboardTapped` → `.gameEnded` delegate
                // → AppFeature transitions to the Victory screen.
                if game.status == .done, !state.isGameOver {
                    locationClient.stopTracking()
                    state.game = game
                    state.isGameOver = true
                    let endState = gameOverLiveActivityState(game: game, radius: state.radius)
                    return .merge(.cancel(id: CancelID.runtime), .run { _ in
                        await liveActivityClient.end(endState)
                    })
                }

                let activatedPowerUp = detectActivatedPowerUp(oldGame: state.game, newGame: game)

                state.game = game
                // Safety net: if `status == .done` arrived while another
                // modal was up (sheet, alert), the early branch above didn't
                // get to flip `isGameOver`. Catch it here so the bottom-bar
                // trophy CTA still appears as soon as the modal closes.
                var effects: [Effect<Action>] = []
                if game.status == .done && !state.isGameOver {
                    state.isGameOver = true
                    locationClient.stopTracking()
                    effects.append(.cancel(id: CancelID.runtime))
                }

                // PP-zone-stored: re-resolve the active circle from the stored
                // schedule on every config tick (covers QA debug anchor-rewind).
                let zCfg = zoneRenderState(for: game, circles: state.circles, now: now.now)
                state.applyZone(zCfg)

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

                if !state.isGameOver &&
                   state.destination == nil &&
                   !game.hunterIds.isEmpty &&
                   game.winners.count >= game.hunterIds.count {
                    state.isGameOver = true
                    locationClient.stopTracking()
                    effects.append(.cancel(id: CancelID.runtime))
                    effects.append(.run { _ in
                        await liveActivityClient.end(nil)
                    })
                }

                return effects.isEmpty ? .none : .merge(effects)

            case let .internal(.scheduleLoadFailed(message)):
                state.zoneScheduleError = message
                return .none
            case .view(.retryScheduleTapped):
                state.zoneScheduleError = nil
                return loadScheduleEffect(state.game.id)
            case let .internal(.scheduleLoaded(circles)):
                state.zoneScheduleError = nil
                state.circles = circles
                let z = zoneRenderState(for: state.game, circles: circles, now: now.now)
                state.applyZone(z)
                return .none

            case .internal(.timerTicked):
                if state.isGameOver {
                    state.isOutsideZone = false
                    return .cancel(id: CancelID.timer)
                }
                state.nowDate = now.now

                // Countdown phases (hunter perspective). Same gate as
                // the chicken: in manual-start mode, nothing counts down
                // until the chicken/GM actually taps LAUNCH and the
                // server stamps `actualStart`.
                let countdownResult = evaluateCountdown(
                    phases: countdownPhases(for: .hunter, game: state.game),
                    now: state.nowDate,
                    currentCountdownNumber: state.countdownNumber,
                    currentCountdownText: state.countdownText
                )
                if state.applyCountdown(countdownResult) {
                    return .run { send in
                        try await clock.sleep(for: .seconds(AppConstants.countdownDisplaySeconds))
                        await send(.internal(.countdownDismissed))
                    }
                }

                guard state.destination == nil else { return .none }
                guard state.hasGameStarted else { return .none }

                if !state.isGameOver, checkGameOverByTime(endDate: state.game.endDate, now: state.nowDate) {
                    HapticManager.notification(.warning)
                    state.isGameOver = true
                    locationClient.stopTracking()
                    let endState = gameOverLiveActivityState(game: state.game, radius: state.radius)
                    return .merge(.cancel(id: CancelID.runtime), .run { _ in
                        await liveActivityClient.end(endState)
                    })
                }

                // PP-zone-stored: resolve the active circle from the stored
                // schedule. Zone shrinks to the 50m final circle and stays;
                // game ends by time / all-found / cancel, not "collapsed".
                // followTheChicken keeps the live chicken GPS center.
                let prevRadiusHM = state.radius
                let zTick = zoneRenderState(for: state.game, circles: state.circles, now: now.now)
                state.applyZone(zTick)
                // Clear zone preview once the zone actually shrinks past it.
                if zTick.radius != prevRadiusHM { state.previewCircle = nil }

                // Power-up proximity check, collect all nearby power-ups
                let nearbyPowerUps = findNearbyPowerUps(
                    userLocation: state.userLocation,
                    availablePowerUps: state.availablePowerUps
                )
                if !nearbyPowerUps.isEmpty {
                    return .merge(nearbyPowerUps.map { .send(.internal(.powerUpCollected($0))) })
                }

                // Zone check (visual warning only, no elimination)
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

                if state.isOutsideZone,
                   !state.isGameOver,
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
                        return .run { _ in
                            do {
                                try await apiClient.applyOutOfZonePenalty(gameId)
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
                    await send(.internal(.wrongCodeRejected(lockedUntil: nil)))
                case let .cooldown(until):
                    await send(.internal(.wrongCodeRejected(lockedUntil: until)))
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
