import ComposableArchitecture
import FirebaseFirestore
import MapboxMaps
import os
import SwiftUI

private let logger = Logger(category: "ChickenMap")

@Reducer
struct ChickenMapFeature {

    @ObservableState
    struct State: Equatable {
        @Presents var destination: Destination.State?
        @Presents var validationQueue: ValidationQueueFeature.State?
        @Presents var newChickenAlert: AlertState<Action.NewChickenAlert>?
        @Shared(.appStorage(AppConstants.prefUserNickname)) var savedNickname = ""
        var game: Game
        var hunterAnnotations: [HunterAnnotation] = []
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
        var userLocation: CLLocationCoordinate2D?
        var isOutsideZone: Bool = false
        var lastLiveActivityState: PoulePartyAttributes.ContentState?
        var powerUps: MapPowerUpsFeature.State = .init()

        var isGameOver: Bool = false

        /// Flipped to `true` when the chicken explicitly cancels the
        /// game. Distinguishes "chicken cancelled → go home" from
        /// "natural game end → go to Victory" once `status == .done`
        /// arrives via the gameConfig stream (both write the same
        /// terminal status, but the UX diverges).
        var isCancelling: Bool = false

        var pendingSubmissionsCount: Int = 0

        var chickenFoundCode: String = ""

        var isLaunching: Bool = false
        /// Last error string returned by `launchGame`. Rendered as an
        /// alert on top of the LAUNCH overlay. Cleared by
        /// `launchErrorDismissed`.
        var launchError: String?

        // MARK: - MapFeatureState passthroughs (child → parent surface)
        var availablePowerUps: [PowerUp] { powerUps.available }
        var collectedPowerUps: [PowerUp] { powerUps.collected }
        var showPowerUpInventory: Bool { powerUps.showInventory }
        var powerUpNotification: String? { powerUps.notification }
        var lastActivatedPowerUpType: PowerUp.PowerUpType? { powerUps.lastActivatedType }

        var hasGameStarted: Bool { nowDate >= game.startDate }
        var hasHuntStarted: Bool { nowDate >= game.hunterStartDate }

        var gamePhase: PoulePartyAttributes.ContentState.GamePhase {
            if isGameOver { return .gameOver }
            if !hasGameStarted { return .waitingToStart }
            if !hasHuntStarted { return .chickenHeadStart }
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
        case delegate(Delegate)
        case destination(PresentationAction<Destination.Action>)
        case `internal`(Internal)
        case newChickenAlert(PresentationAction<NewChickenAlert>)
        case powerUps(MapPowerUpsFeature.Action)
        case validationQueue(PresentationAction<ValidationQueueFeature.Action>)
        case view(View)

        enum NewChickenAlert: Equatable {}

        @CasePathable
        enum View {
            case retryScheduleTapped
            case appBecameActive
            case beenFoundButtonTapped
            case cancelGameButtonTapped
            case endGameCodeDismissed
            case gameInfoDismissed
            case gameInitialized
            case infoButtonTapped
            case launchTapped
            case launchErrorDismissed
            case onTask
            case validationQueueTapped
            /// Banner tap at game-end → navigate to the Victory /
            /// leaderboard page (parent handles via `gameEnded` delegate).
            case viewLeaderboardTapped
            /// QA panel (debug games only): force the game to end now.
            case debugEndNowTapped
            /// QA panel (debug games only): advance one lifecycle step
            /// (launch / shrink+spawn / collapse) without waiting on the clock.
            case debugAdvanceStepTapped
        }

        @CasePathable
        enum Internal {
            case countdownDismissed
            case foundCodeFetched(String)
            case gameUpdated(Game)
            case hunterLocationsUpdated([HunterLocation])
            case launchSucceeded
            case launchFailed(String)
            case newLocationFetched(CLLocationCoordinate2D)
            case pendingSubmissionsUpdated(Int)
            case powerUpCollected(PowerUp)
            case powerUpsUpdated([PowerUp])
            case scheduleLoaded([ZoneCircle])
            case scheduleLoadFailed(String)
            case timerTicked
            case winnerNotificationDismissed
        }

        @CasePathable
        enum Delegate {
            case returnedToMenu
            /// Natural game end, server-confirmed `status == .done` for any
            /// reason other than the chicken's own cancel (timer expired,
            /// all hunters found, server task fired). Parent navigates to
            /// the Victory / leaderboard screen.
            case gameEnded(Game)
            case becameHunter(Game, teamName: String)
        }
    }

    @Reducer
    struct Destination {
        @ObservableState
        enum State: Equatable {
            case alert(AlertState<Action.Alert>)
            case endGameCode(String)
        }

        enum Action {
            case alert(Alert)

            enum Alert: Equatable {
                case cancelGame
                case noGameFound
            }
        }
    }

    enum CancelID {
        case powerUpNotificationDismiss
        case runtime
    }

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.now) var now
    @Dependency(\.continuousClock) var clock
    @Dependency(\.liveActivityClient) var liveActivityClient
    @Dependency(\.locationClient) var locationClient
    @Dependency(\.userClient) var userClient
    @Dependency(\.analyticsClient) var analyticsClient

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
            case .binding, .delegate:
                return .none
            case .destination(.presented(.alert(.cancelGame))):
                locationClient.stopTracking()
                state.isCancelling = true
                let gameId = state.game.id
                let winnersCount = state.game.winners.count
                return .concatenate(.cancel(id: CancelID.runtime), .run { [analyticsClient] send in
                    await liveActivityClient.end(nil)
                    do {
                        try await apiClient.updateGameStatus(gameId, .done)
                        analyticsClient.gameEnded(reason: "chicken_cancelled", winnersCount: winnersCount)
                    } catch {
                        Logger(category: "ChickenMapFeature")
                            .error("Failed to update game status to done: \(error.localizedDescription)")
                    }
                    await send(.delegate(.returnedToMenu))
                })
            case .destination:
                return .none
            case .newChickenAlert:
                return .none
            case .internal(.countdownDismissed):
                state.countdownNumber = nil
                state.countdownText = nil
                return .none
            case .view(.endGameCodeDismissed):
                state.destination = nil
                return .none
            case .internal(.winnerNotificationDismissed):
                state.winnerNotification = nil
                return .none
            case let .internal(.powerUpsUpdated(all)):
                // If auth dropped between launch and now, treat the inventory
                // as empty rather than matching everyone whose `collectedBy`
                // happens to be "" (which would otherwise show foreign power-ups
                // in this player's inventory).
                let userId = userClient.currentUserId()
                let available = all.filter { !$0.type.isHunterPowerUp && !$0.isCollected }
                let collected: [PowerUp]
                if let userId, !userId.isEmpty {
                    collected = all.filter { $0.collectedBy == userId && $0.activatedAt == nil }
                } else {
                    collected = []
                }
                return .send(.powerUps(.dataUpdated(available: available, collected: collected)))
            case let .internal(.powerUpCollected(powerUp)):
                let gameId = state.game.id
                guard let userId = userClient.currentUserId(), !userId.isEmpty else {
                    logger.error("Skipping powerUpCollected: no current user id")
                    return .none
                }
                // Atomic dedup, see HunterMap for the rationale. At 1 Hz
                // a stationary chicken would otherwise spam N duplicate
                // transactions while the first is still in flight.
                guard let location = state.userLocation,
                      !state.powerUps.collectingIds.contains(powerUp.id) else { return .none }
                state.powerUps.collectingIds.insert(powerUp.id)
                logger.info("Collecting power-up id=\(powerUp.id) type=\(powerUp.type.rawValue) distance=\(String(format: "%.1fm", distanceMeters(location, powerUp.coordinate))) chickenId=\(userId)")
                return .run { [analyticsClient] send in
                    do {
                        try await apiClient.collectPowerUp(gameId, powerUp.id, location)
                        analyticsClient.powerUpCollected(type: powerUp.type.rawValue, role: "chicken")
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
                    analyticsClient.powerUpActivated(type: powerUp.type.rawValue, role: "chicken")
                    try await clock.sleep(for: .seconds(2))
                    await send(.powerUps(.notificationCleared))
                }
                .cancellable(id: CancelID.powerUpNotificationDismiss, cancelInFlight: true)
            case .powerUps:
                return .none
            case let .internal(.gameUpdated(game)):
                let myUserId = userClient.currentUserId() ?? ""
                if !myUserId.isEmpty,
                   !game.isChicken(myUserId),
                   game.role(of: myUserId) != nil {
                    let teamName = state.savedNickname.trimmingCharacters(in: .whitespacesAndNewlines)
                    let resolvedName = teamName.isEmpty ? "Hunter" : teamName
                    return .send(.delegate(.becameHunter(game, teamName: resolvedName)))
                }

                // Detect newly activated power-ups (compare old vs new)
                let activatedPowerUp = detectActivatedPowerUp(oldGame: state.game, newGame: game)

                let wasDone = state.game.status == .done
                state.game = game

                // QA debug games drive zone shrinks server-side (the
                // `advanceStep` callable rewinds the start anchor), so re-derive
                // the radius / next-update / circle from the fresh timing on
                // every config tick. Real games keep the incremental timer path
                // (which handles the zone-freeze window) untouched.
                if game.isDebugGame {
                    let zDebug = zoneRenderState(for: game, circles: state.circles, now: now.now)
                    state.applyZone(zDebug)
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

                if state.previousWinnersCount >= 0 {
                    if let notification = detectNewWinners(
                        winners: game.winners,
                        previousCount: state.previousWinnersCount
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
                    let gameId = game.id
                    let winnersCount = game.winners.count
                    effects.append(.cancel(id: CancelID.runtime))
                    effects.append(.run { [analyticsClient] _ in
                        do {
                            try await apiClient.updateGameStatus(gameId, .done)
                            analyticsClient.gameEnded(reason: "all_hunters_found", winnersCount: winnersCount)
                        } catch {
                            Logger(category: "ChickenMapFeature")
                                .error("Failed to set game DONE when all hunters found: \(error.localizedDescription)")
                        }
                        await liveActivityClient.end(nil)
                    })
                }

                // Natural game end: server-confirmed status flipped to
                // `.done` and the chicken didn't cancel it themselves
                // (the cancel handler sets `isCancelling` and navigates
                // home directly via `returnedToMenu`). The map stays
                // on screen with `isGameOver = true` so the "Game
                // ended" banner appears; tapping the banner fires
                // `viewLeaderboardTapped` which sends the parent the
                // `.gameEnded` delegate.
                if !wasDone, game.status == .done, !state.isCancelling, !state.isGameOver {
                    state.isGameOver = true
                    locationClient.stopTracking()
                    effects.append(.cancel(id: CancelID.runtime))
                    effects.append(.run { _ in await liveActivityClient.end(nil) })
                }

                return effects.isEmpty ? .none : .merge(effects)
            case .view(.launchTapped):
                guard state.game.status == .readyToLaunch,
                      !state.isLaunching else { return .none }
                state.isLaunching = true
                state.launchError = nil
                let gameId = state.game.id
                return .run { send in
                    do {
                        _ = try await apiClient.launchGame(gameId)
                        await send(.internal(.launchSucceeded))
                    } catch {
                        await send(.internal(.launchFailed(error.localizedDescription)))
                    }
                }

            case .view(.launchErrorDismissed):
                state.launchError = nil
                return .none

            case .internal(.launchSucceeded):
                state.isLaunching = false
                state.launchError = nil
                return .none

            case let .internal(.launchFailed(message)):
                state.isLaunching = false
                state.launchError = message
                return .none

            case .view(.cancelGameButtonTapped):
                guard !state.isGameOver else { return .none }
                state.destination = .alert(
                    AlertState {
                        TextState("Cancel game")
                    } actions: {
                        ButtonState(role: .cancel) {
                            TextState("Never mind")
                        }
                        ButtonState(role: .destructive, action: .cancelGame) {
                            TextState("Cancel game")
                        }
                    } message: {
                        TextState("Are you sure you want to cancel and finish the game now?")
                    }
                )
                return .none
            case .view(.appBecameActive):
                guard state.hasGameStarted, !state.isGameOver else { return .none }
                let gameId = state.game.id
                // Snapshot invisibility from the live game state at the
                // call site so we honor any active power-up.
                let isInvisible = state.game.isChickenInvisible
                return .run { _ in
                    guard let coord = locationClient.lastLocation() else { return }
                    do {
                        try apiClient.setChickenLocation(gameId, coord, isInvisible)
                    } catch {
                        logger.error("appBecameActive setChickenLocation failed: \(error.localizedDescription)")
                    }
                }
            case .view(.beenFoundButtonTapped):
                state.destination = .endGameCode(state.chickenFoundCode)
                return .none
            case let .internal(.foundCodeFetched(code)):
                state.chickenFoundCode = code
                return .none
            case .view(.infoButtonTapped):
                state.showGameInfo = true
                return .none
            case .view(.gameInfoDismissed):
                state.showGameInfo = false
                return .none
            case .view(.validationQueueTapped):
                state.validationQueue = ValidationQueueFeature.State(
                    gameId: state.game.id,
                    hunterIds: state.game.hunterIds
                )
                return .none
            case .view(.viewLeaderboardTapped):
                return .send(.delegate(.gameEnded(state.game)))
            case .view(.debugEndNowTapped):
                let gameId = state.game.id
                return .run { _ in
                    do {
                        try await apiClient.debugAdvanceGame(gameId, .endNow)
                    } catch {
                        logger.warning("[qa] debug action failed: \(error.localizedDescription)")
                    }
                }
            case .view(.debugAdvanceStepTapped):
                let gameId = state.game.id
                return .run { _ in
                    do {
                        try await apiClient.debugAdvanceGame(gameId, .advanceStep)
                    } catch {
                        logger.warning("[qa] debug action failed: \(error.localizedDescription)")
                    }
                }
            case .validationQueue:
                return .none
            case let .internal(.pendingSubmissionsUpdated(count)):
                state.pendingSubmissionsCount = count
                return .none
            case let .internal(.hunterLocationsUpdated(hunters)):
                let sorted = hunters.sorted { $0.hunterId < $1.hunterId }
                state.hunterAnnotations = sorted.enumerated().map { index, hunter in
                    HunterAnnotation(
                        id: hunter.hunterId,
                        coordinate: CLLocationCoordinate2D(
                            latitude: hunter.location.latitude,
                            longitude: hunter.location.longitude
                        ),
                        displayName: "Hunter \(index + 1)"
                    )
                }
                return .none
            case let .internal(.newLocationFetched(location)):
                state.userLocation = location
                // Only move the circle center when the chicken defines the zone
                if state.game.gameMode != .stayInTheZone {
                    state.mapCircle = CircleOverlay(
                        center: location,
                        radius: CLLocationDistance(state.radius)
                    )
                }
                return .none
            case .view(.onTask):
                let gameId = state.game.id
                let startDate = state.game.startDate
                let powerUpsEnabled = state.game.powerUps.enabled
                let driftSeed = state.game.zone.driftSeed

                // Shared references so the tracking loop can check active effects
                let invisibilityUntil = LockIsolated<Date?>(nil)
                let jammerUntil = LockIsolated<Date?>(nil)

                var effects: [Effect<Action>] = [
                    .run { send in
                        do {
                            let code = try await apiClient.getFoundCode(gameId)
                            await send(.internal(.foundCodeFetched(code)))
                        } catch {
                            logger.error("getFoundCode failed: \(error.localizedDescription)")
                        }
                    },
                    .run { send in
                        for await _ in self.clock.timer(interval: .seconds(1)) {
                            await send(.internal(.timerTicked))
                        }
                    },
                    .run { send in
                        for await game in apiClient.gameConfigStream(gameId) {
                            if let game {
                                invisibilityUntil.setValue(game.powerUps.activeEffects.invisibility?.dateValue())
                                jammerUntil.setValue(game.powerUps.activeEffects.jammer?.dateValue())
                                await send(.internal(.gameUpdated(game)))
                            }
                        }
                    },
                    .run { send in
                        guard powerUpsEnabled else { return }
                        for await powerUps in apiClient.powerUpsStream(gameId) {
                            await send(.internal(.powerUpsUpdated(powerUps)))
                        }
                    },
                    .run { send in
                        for await subs in apiClient.pendingSubmissionsStream(gameId) {
                            await send(.internal(.pendingSubmissionsUpdated(subs.count)))
                        }
                    }
                ]

                // Heartbeat: periodically write a timestamp so hunters can detect disconnect
                effects.append(
                    .run { _ in
                        let delay = startDate.timeIntervalSinceNow
                        if delay > 0 {
                            try await clock.sleep(for: .seconds(delay))
                        }
                        while !Task.isCancelled {
                            do {
                                try await apiClient.updateHeartbeat(gameId)
                            } catch {
                                logger.error("Failed to update heartbeat: \(error)")
                            }
                            try await clock.sleep(for: .seconds(AppConstants.heartbeatIntervalSeconds))
                        }
                    }
                )

                let latestLocation = LockIsolated<CLLocationCoordinate2D?>(nil)
                effects.append(
                    .run { send in
                        let delay = startDate.timeIntervalSinceNow
                        if delay > 0 {
                            try await clock.sleep(for: .seconds(delay))
                        }
                        if let currentLocation = locationClient.lastLocation() {
                            latestLocation.setValue(currentLocation)
                            await send(.internal(.newLocationFetched(currentLocation)))
                        }
                        for await coordinate in locationClient.startTracking() {
                            latestLocation.setValue(coordinate)
                            await send(.internal(.newLocationFetched(coordinate)))
                        }
                    }
                )
                // The only position writer: one write per throttle window, moving or not,
                // so a radar ping always finds a recent point.
                effects.append(
                    .run { _ in
                        let delay = startDate.timeIntervalSinceNow
                        if delay > 0 {
                            try await clock.sleep(for: .seconds(delay))
                        }
                        while !Task.isCancelled {
                            if let coordinate = latestLocation.value {
                                let now = self.now.now
                                let isInvisible = invisibilityUntil.value.map { now < $0 } ?? false
                                let isJammed = jammerUntil.value.map { now < $0 } ?? false
                                let sendCoordinate = isJammed ? applyJammerNoise(to: coordinate, driftSeed: driftSeed) : coordinate
                                do {
                                    try apiClient.setChickenLocation(gameId, sendCoordinate, isInvisible)
                                } catch {
                                    logger.error("Failed to send chicken location: \(error)")
                                }
                            }
                            try await clock.sleep(for: .seconds(AppConstants.locationThrottleSeconds))
                        }
                    }
                )

                // chickenCanSeeHunters: chicken can see all hunters
                // Gated behind hunterStartDate (hunters aren't active until then)
                if state.game.chickenCanSeeHunters {
                    let hunterStartDate = state.game.hunterStartDate
                    effects.append(
                        .run { send in
                            let delay = hunterStartDate.timeIntervalSinceNow
                            if delay > 0 {
                                try await clock.sleep(for: .seconds(delay))
                            }
                            for await hunters in apiClient.hunterLocationsStream(gameId) {
                                await send(.internal(.hunterLocationsUpdated(hunters)))
                            }
                        }
                    )
                }

                return .merge(effects).cancellable(id: CancelID.runtime, cancelInFlight: true)
            case .view(.gameInitialized):
                // PP-zone-stored: seed an initial circle from the zone center
                // so the map isn't empty before the schedule fetch lands; the
                // real geometry is applied in `.scheduleLoaded`.
                state.radius = Int(state.game.zone.radius)
                state.nextRadiusUpdate = state.game.hunterStartDate
                state.mapCircle = CircleOverlay(
                    center: state.game.zone.center.toCLCoordinates,
                    radius: CLLocationDistance(state.radius)
                )

                // Start Live Activity
                let attributes = PoulePartyAttributes(
                    gameName: state.game.name,
                    gameCode: state.game.gameCode,
                    playerRole: .chicken,
                    gameModeName: state.game.gameMode.title,
                    gameStartDate: state.game.startDate,
                    gameEndDate: state.game.endDate,
                    totalHunters: max(0, state.game.maxPlayers - 1)
                )
                let initialLAState = state.liveActivityState
                state.lastLiveActivityState = initialLAState

                let gameMode = state.game.gameMode.rawValue
                let wasWaiting = state.game.status == .waiting
                let scheduleGameId = state.game.id
                return .merge(
                    .run { [analyticsClient] _ in
                        await liveActivityClient.start(attributes, initialLAState)
                        if wasWaiting {
                            analyticsClient.gameStarted(gameMode: gameMode)
                        }
                    },
                    loadScheduleEffect(scheduleGameId)
                )

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
                state.applyZone(z, fallbackCenter: state.game.zone.center.toCLCoordinates)
                return .none

            case .internal(.timerTicked):
                state.nowDate = now.now

                // Countdown phases (chicken perspective).
                // In manual-start mode, the planned `startDate` is just
                // "when status flips to readyToLaunch", the real start is
                // whenever the chicken/GM taps LAUNCH and the server
                // stamps `actualStart`. Both phases gate on that so the
                // 3-2-1 RUN doesn't fire before LAUNCH.
                let countdownResult = evaluateCountdown(
                    phases: countdownPhases(for: .chicken, game: state.game),
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
                guard state.hasHuntStarted else { return .none }

                if !state.isGameOver, checkGameOverByTime(endDate: state.game.endDate, now: state.nowDate) {
                    HapticManager.notification(.warning)
                    state.isGameOver = true
                    locationClient.stopTracking()
                    let endState = gameOverLiveActivityState(game: state.game, radius: state.radius)
                    let gameId = state.game.id
                    let winnersCount = state.game.winners.count
                    return .merge(.cancel(id: CancelID.runtime), .run { [analyticsClient] _ in
                        await liveActivityClient.end(endState)
                        do {
                            try await apiClient.updateGameStatus(gameId, .done)
                            analyticsClient.gameEnded(reason: "time_expired", winnersCount: winnersCount)
                        } catch {
                            logger.error("Failed to update game status: \(error)")
                        }
                    })
                }

                // PP-zone-stored: resolve the active circle from the stored
                // schedule (no on-device recompute, no Int-truncation drift).
                // The zone shrinks to the 50m final circle and stays; the game
                // ends by time / all-found / cancel, not by "zone collapsed".
                // followTheChicken keeps the live chicken GPS center (set on
                // location updates); only the radius comes from the schedule.
                let zTick = zoneRenderState(for: state.game, circles: state.circles, now: now.now)
                state.applyZone(zTick)
                // Periodic power-ups are spawned by the `spawnPowerUpBatch`
                // Cloud Task scheduled at game creation, no client-side spawn.

                // Power-up proximity check, collect all nearby power-ups
                let nearbyPowerUps = findNearbyPowerUps(
                    userLocation: state.userLocation,
                    availablePowerUps: state.availablePowerUps
                )
                if !nearbyPowerUps.isEmpty {
                    return .merge(nearbyPowerUps.map { .send(.internal(.powerUpCollected($0))) })
                }

                // Zone check (visual warning only, no elimination)
                if shouldCheckZone(role: .chicken, gameMod: state.game.gameMode),
                   let userLoc = state.userLocation,
                   let circle = state.mapCircle {
                    let zoneResult = checkZoneStatus(
                        userLocation: userLoc,
                        zoneCenter: circle.center,
                        zoneRadius: circle.radius
                    )
                    state.isOutsideZone = zoneResult.isOutsideZone
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
            }
        }
        .ifLet(\.$destination, action: \.destination) {
          Destination()
        }
        .ifLet(\.$validationQueue, action: \.validationQueue) {
          ValidationQueueFeature()
        }
        .ifLet(\.$newChickenAlert, action: \.newChickenAlert)
    }
}

extension AlertState where Action == ChickenMapFeature.Action.NewChickenAlert {
    static var becameChicken: Self {
        AlertState {
            TextState("You are the new chicken! 🐔")
        } actions: {
            ButtonState {
                TextState("OK")
            }
        } message: {
            TextState("A GameMaster made you the chicken. Get ready to run and hide!")
        }
    }
}
