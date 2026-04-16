//
//  ChickenMap.swift
//  PouleParty
//
//  Created by Julien Rahier on 16/03/2024.
//

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
        var game: Game
        var hunterAnnotations: [HunterAnnotation] = []
        var nextRadiusUpdate: Date?
        var nowDate: Date = .now
        var previousWinnersCount: Int = -1
        var radius: Int = 1500
        var mapCircle: CircleOverlay?
        var showGameInfo: Bool = false
        var winnerNotification: String? = nil
        var countdownNumber: Int? = nil
        var countdownText: String? = nil
        var userLocation: CLLocationCoordinate2D?
        var isOutsideZone: Bool = false
        var lastLiveActivityState: PoulePartyAttributes.ContentState?
        var availablePowerUps: [PowerUp] = []
        var collectedPowerUps: [PowerUp] = []
        var showPowerUpInventory: Bool = false
        var powerUpNotification: String? = nil
        var lastActivatedPowerUpType: PowerUp.PowerUpType? = nil
        var lastSpawnBatchIndex: Int = 0

        var hasGameStarted: Bool { nowDate >= game.startDate }
        var hasHuntStarted: Bool { nowDate >= game.hunterStartDate }

        var currentGamePhase: PoulePartyAttributes.ContentState.GamePhase {
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
                gamePhase: currentGamePhase
            )
        }
    }

    enum Action: BindableAction {
        case beenFoundButtonTapped
        case binding(BindingAction<State>)
        case cancelGameButtonTapped
        case countdownDismissed
        case destination(PresentationAction<Destination.Action>)
        case endGameCodeDismissed
        case gameInfoDismissed
        case gameInitialized
        case gameUpdated(Game)
        case hunterLocationsUpdated([HunterLocation])
        case infoButtonTapped
        case newLocationFetched(CLLocationCoordinate2D)
        case onTask
        case returnedToMenu
        case timerTicked
        case winnerNotificationDismissed
        case powerUpsUpdated([PowerUp])
        case powerUpCollected(PowerUp)
        case powerUpActivated(PowerUp)
        case powerUpNotificationDismissed
        case powerUpInventoryTapped
        case powerUpInventoryDismissed
        case allHuntersFound
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
                case gameOver
                case noGameFound
            }
        }
    }

    enum CancelID {
        case powerUpNotificationDismiss
    }

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.continuousClock) var clock
    @Dependency(\.liveActivityClient) var liveActivityClient
    @Dependency(\.locationClient) var locationClient
    @Dependency(\.userClient) var userClient
    @Dependency(\.analyticsClient) var analyticsClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case .destination(.presented(.alert(.cancelGame))):
                locationClient.stopTracking()
                let gameId = state.game.id
                let winnersCount = state.game.winners.count
                return .run { [analyticsClient] send in
                    await liveActivityClient.end(nil)
                    do {
                        try await apiClient.updateGameStatus(gameId, .done)
                        analyticsClient.gameEnded(reason: "chicken_cancelled", winnersCount: winnersCount)
                    } catch {
                        Logger(category: "ChickenMapFeature")
                            .error("Failed to update game status to done: \(error.localizedDescription)")
                    }
                    await send(.returnedToMenu)
                }
            case .destination(.presented(.alert(.gameOver))):
                let gameId = state.game.id
                return .run { send in
                    await liveActivityClient.end(nil)
                    // Ensure status is set to done (the timer effect may have
                    // been cancelled if the user tapped OK before it completed)
                    do {
                        try await apiClient.updateGameStatus(gameId, .done)
                    } catch {
                        Logger(category: "ChickenMapFeature")
                            .error("Failed to update game status to done: \(error.localizedDescription)")
                    }
                    // Show leaderboard instead of returning to menu directly
                    await send(.allHuntersFound)
                }
            case .destination:
                return .none
            case .countdownDismissed:
                state.countdownNumber = nil
                state.countdownText = nil
                return .none
            case .endGameCodeDismissed:
                state.destination = nil
                return .none
            case .winnerNotificationDismissed:
                state.winnerNotification = nil
                return .none
            case let .powerUpsUpdated(allPowerUps):
                let userId = userClient.currentUserId() ?? ""
                state.availablePowerUps = allPowerUps.filter { !$0.type.isHunterPowerUp && !$0.isCollected }
                state.collectedPowerUps = allPowerUps.filter { $0.collectedBy == userId && $0.activatedAt == nil }
                return .none
            case let .powerUpCollected(powerUp):
                let gameId = state.game.id
                let userId = userClient.currentUserId() ?? ""
                return .run { [analyticsClient] send in
                    do {
                        try await apiClient.collectPowerUp(gameId, powerUp.id, userId)
                        analyticsClient.powerUpCollected(type: powerUp.type.rawValue, role: "chicken")
                    } catch {
                        logger.error("Failed to collect power-up: \(error)")
                    }
                }
            case let .powerUpActivated(powerUp):
                let gameId = state.game.id
                let duration = powerUp.type.durationSeconds ?? 0
                let expiresAt = Timestamp(date: .now.addingTimeInterval(duration))
                state.showPowerUpInventory = false
                state.powerUpNotification = "Activated: \(powerUp.type.displayName)!"
                state.lastActivatedPowerUpType = powerUp.type
                return .run { [analyticsClient] send in
                    try? await apiClient.activatePowerUp(gameId, powerUp.id, expiresAt)
                    switch powerUp.type {
                    case .invisibility, .zoneFreeze, .decoy, .jammer:
                        try? await apiClient.updateGameActiveEffect(gameId, powerUp.type.firestoreEffectField, expiresAt)
                    default:
                        break
                    }
                    analyticsClient.powerUpActivated(type: powerUp.type.rawValue, role: "chicken")
                    try await clock.sleep(for: .seconds(2))
                    await send(.powerUpNotificationDismissed)
                }
                .cancellable(id: CancelID.powerUpNotificationDismiss, cancelInFlight: true)
            case .powerUpNotificationDismissed:
                state.powerUpNotification = nil
                return .none
            case .powerUpInventoryTapped:
                state.showPowerUpInventory = true
                return .none
            case .powerUpInventoryDismissed:
                state.showPowerUpInventory = false
                return .none
            case let .gameUpdated(game):
                // Detect newly activated power-ups (compare old vs new)
                    _ = Date.now
                let activatedPowerUp = detectActivatedPowerUp(oldGame: state.game, newGame: game)

                state.game = game

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
                    state.powerUpNotification = activated.text
                    state.lastActivatedPowerUpType = activated.type
                    effects.append(
                        .run { send in
                            try await clock.sleep(for: .seconds(2))
                            await send(.powerUpNotificationDismissed)
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
                            await send(.winnerNotificationDismissed)
                        })
                    }
                } else {
                    state.previousWinnersCount = game.winners.count
                }

                // End the game when all hunters have found the chicken
                // Chicken is authoritative: it sets game status to DONE
                if state.destination == nil &&
                   !game.hunterIds.isEmpty &&
                   game.winners.count >= game.hunterIds.count {
                    locationClient.stopTracking()
                    let gameId = game.id
                    let winnersCount = game.winners.count
                    effects.append(.run { [analyticsClient] send in
                        do {
                            try await apiClient.updateGameStatus(gameId, .done)
                            analyticsClient.gameEnded(reason: "all_hunters_found", winnersCount: winnersCount)
                        } catch {
                            Logger(category: "ChickenMapFeature")
                                .error("Failed to set game DONE when all hunters found: \(error.localizedDescription)")
                        }
                        await liveActivityClient.end(nil)
                        await send(.allHuntersFound)
                    })
                }

                return effects.isEmpty ? .none : .merge(effects)
            case .cancelGameButtonTapped:
                state.destination = .alert(
                    AlertState {
                        TextState("Cancel game")
                    } actions: {
                        ButtonState(role: .cancel) {
                            TextState("Never mind")
                        }
                        ButtonState(role: .destructive ,action: .cancelGame) {
                            TextState("Cancel game")
                        }
                    } message: {
                        TextState("Are you sure you want to cancel and finish the game now?")
                    }
                )
                return .none
            case .beenFoundButtonTapped:
                state.destination = .endGameCode(state.game.foundCode)
                return .none
            case .returnedToMenu:
                return .none
            case .allHuntersFound:
                return .none
            case .infoButtonTapped:
                state.showGameInfo = true
                return .none
            case .gameInfoDismissed:
                state.showGameInfo = false
                return .none
            case let .hunterLocationsUpdated(hunters):
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
            case let .newLocationFetched(location):
                state.userLocation = location
                // Only move the circle center when the chicken defines the zone
                if state.game.gameMode != .stayInTheZone {
                    state.mapCircle = CircleOverlay(
                        center: location,
                        radius: CLLocationDistance(state.radius)
                    )
                }
                return .none
            case .onTask:
                let gameId = state.game.id
                let gameMod = state.game.gameMode
                let startDate = state.game.startDate
                let initialCenter = state.game.zone.center.toCLCoordinates
                let initialRadius = state.game.zone.radius
                let driftSeed = state.game.zone.driftSeed
                let powerUpsEnabled = state.game.powerUps.enabled
                // Filter out position-dependent power-ups in stayInTheZone (no position sharing in that mode)
                let enabledPowerUpTypes: [String] = {
                    var types = state.game.powerUps.enabledTypes
                    if gameMod == .stayInTheZone {
                        let uselessInZone: Set<String> = [
                            PowerUp.PowerUpType.invisibility.rawValue,
                            PowerUp.PowerUpType.decoy.rawValue,
                            PowerUp.PowerUpType.jammer.rawValue
                        ]
                        types.removeAll { uselessInZone.contains($0) }
                    }
                    return types
                }()

                // Shared references so the tracking loop can check active effects
                let invisibilityUntil = LockIsolated<Date?>(nil)
                let jammerUntil = LockIsolated<Date?>(nil)
                let radarPingUntil = LockIsolated<Date?>(nil)

                var effects: [Effect<Action>] = [
                    .run { send in
                        for await _ in self.clock.timer(interval: .seconds(1)) {
                            await send(.timerTicked)
                        }
                    },
                    .run { send in
                        for await game in apiClient.gameConfigStream(gameId) {
                            if let game {
                                invisibilityUntil.setValue(game.powerUps.activeEffects.invisibility?.dateValue())
                                jammerUntil.setValue(game.powerUps.activeEffects.jammer?.dateValue())
                                radarPingUntil.setValue(game.powerUps.activeEffects.radarPing?.dateValue())
                                await send(.gameUpdated(game))
                            }
                        }
                    },
                    .run { send in
                        guard powerUpsEnabled else { return }
                        for await powerUps in apiClient.powerUpsStream(gameId) {
                            await send(.powerUpsUpdated(powerUps))
                        }
                    },
                    .run { _ in
                        guard powerUpsEnabled else { return }
                        let generated = generatePowerUps(
                            center: initialCenter,
                            radius: initialRadius,
                            count: AppConstants.powerUpInitialBatchSize,
                            driftSeed: driftSeed,
                            batchIndex: 0,
                            enabledTypes: enabledPowerUpTypes
                        )
                        let powerUps = await snapPowerUpsToRoads(generated)
                        do {
                            try await apiClient.spawnPowerUps(gameId, powerUps)
                        } catch {
                            logger.error("Failed to spawn power-ups: \(error)")
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
                        while true {
                            do {
                                try apiClient.updateHeartbeat(gameId)
                            } catch {
                                logger.error("Failed to update heartbeat: \(error)")
                            }
                            try await clock.sleep(for: .seconds(30))
                        }
                    }
                )

                // followTheChicken: chicken sends position to hunters
                // stayInTheZone: track location for zone check only (no Firestore writes)
                // Gated behind startDate to avoid leaking position early
                if gameMod != .stayInTheZone {
                    effects.append(
                        .run { send in
                            let delay = startDate.timeIntervalSinceNow
                            if delay > 0 {
                                try await clock.sleep(for: .seconds(delay))
                            }
                            // Send current location immediately on connect
                            if let currentLocation = locationClient.lastLocation() {
                                await send(.newLocationFetched(currentLocation))
                                do {
                                    try apiClient.setChickenLocation(gameId, currentLocation)
                                } catch {
                                    logger.error("Failed to send initial chicken location: \(error)")
                                }
                            }
                            var lastWrite = Date.now
                            for await coordinate in locationClient.startTracking() {
                                await send(.newLocationFetched(coordinate))
                                let isInvisible = invisibilityUntil.value.map { Date.now < $0 } ?? false
                                if Date.now.timeIntervalSince(lastWrite) >= AppConstants.locationThrottleSeconds && !isInvisible {
                                    let isJammed = jammerUntil.value.map { Date.now < $0 } ?? false
                                    let sendCoordinate = isJammed ? applyJammerNoise(to: coordinate) : coordinate
                                    do {
                                        try apiClient.setChickenLocation(gameId, sendCoordinate)
                                    } catch {
                                        logger.error("Failed to send chicken location: \(error)")
                                    }
                                    lastWrite = .now
                                }
                            }
                        }
                    )
                } else {
                    // stayInTheZone: chicken needs GPS for zone check (no Firestore writes normally)
                    // When radar ping is active, force-write location so hunters can see the chicken
                    effects.append(
                        .run { send in
                            let delay = startDate.timeIntervalSinceNow
                            if delay > 0 {
                                try await clock.sleep(for: .seconds(delay))
                            }
                            if let currentLocation = locationClient.lastLocation() {
                                await send(.newLocationFetched(currentLocation))
                            }
                            var lastWrite = Date.now
                            for await coordinate in locationClient.startTracking() {
                                await send(.newLocationFetched(coordinate))
                                let isRadarPinged = radarPingUntil.value.map { Date.now < $0 } ?? false
                                if isRadarPinged && Date.now.timeIntervalSince(lastWrite) >= AppConstants.locationThrottleSeconds {
                                    let isJammed = jammerUntil.value.map { Date.now < $0 } ?? false
                                    let sendCoordinate = isJammed ? applyJammerNoise(to: coordinate) : coordinate
                                    do {
                                        try apiClient.setChickenLocation(gameId, sendCoordinate)
                                    } catch {
                                        logger.error("Failed to send chicken location during radar ping: \(error)")
                                    }
                                    lastWrite = .now
                                }
                            }
                        }
                    )
                }

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
                                await send(.hunterLocationsUpdated(hunters))
                            }
                        }
                    )
                }

                return .merge(effects)
            case .gameInitialized:
                let (lastUpdate, lastRadius) = state.game.findLastUpdate()

                state.radius = lastRadius
                state.nextRadiusUpdate = lastUpdate
                let circleCenter = interpolateZoneCenter(
                    initialCenter: state.game.zone.center.toCLCoordinates,
                    finalCenter: state.game.finalLocation,
                    initialRadius: state.game.zone.radius,
                    currentRadius: Double(lastRadius)
                )
                state.mapCircle = CircleOverlay(
                    center: circleCenter,
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

                guard state.game.status == .waiting else {
                    return .run { _ in
                        await liveActivityClient.start(attributes, initialLAState)
                    }
                }
                let gameId = state.game.id
                let gameMode = state.game.gameMode.rawValue
                return .run { [analyticsClient] _ in
                    await liveActivityClient.start(attributes, initialLAState)
                    do {
                        try await apiClient.updateGameStatus(gameId, .inProgress)
                        analyticsClient.gameStarted(gameMode: gameMode)
                    } catch {
                        logger.error("Failed to update game status to inProgress: \(error)")
                    }
                }
            case .timerTicked:
                state.nowDate = .now

                // Countdown phases (chicken perspective)
                let countdownResult = evaluateCountdown(
                    phases: [
                        CountdownPhase(
                            targetDate: state.game.startDate,
                            completionText: "RUN! 🐔",
                            showNumericCountdown: true,
                            isEnabled: true
                        ),
                        CountdownPhase(
                            targetDate: state.game.hunterStartDate,
                            completionText: "🔍 Hunters incoming!",
                            showNumericCountdown: false,
                            isEnabled: state.game.timing.headStartMinutes > 0
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
                        await send(.countdownDismissed)
                    }
                }

                guard state.destination == nil else { return .none }
                guard state.hasHuntStarted else { return .none }

                // Game over by time
                if checkGameOverByTime(endDate: state.game.endDate) {
                    HapticManager.notification(.warning)
                    locationClient.stopTracking()
                    let endState = PoulePartyAttributes.ContentState(
                        radiusMeters: state.radius,
                        nextShrinkDate: nil,
                        activeHunters: max(0, state.game.hunterIds.count - state.game.winners.count),
                        winnersCount: state.game.winners.count,
                        isOutsideZone: false,
                        gamePhase: .gameOver
                    )
                    let gameId = state.game.id
                    let winnersCount = state.game.winners.count
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
                    return .run { [analyticsClient] _ in
                        await liveActivityClient.end(endState)
                        do {
                            try await apiClient.updateGameStatus(gameId, .done)
                            analyticsClient.gameEnded(reason: "time_expired", winnersCount: winnersCount)
                        } catch {
                            logger.error("Failed to update game status: \(error)")
                        }
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
                        locationClient.stopTracking()
                        let endState = PoulePartyAttributes.ContentState(
                            radiusMeters: 0,
                            nextShrinkDate: nil,
                            activeHunters: max(0, state.game.hunterIds.count - state.game.winners.count),
                            winnersCount: state.game.winners.count,
                            isOutsideZone: false,
                            gamePhase: .gameOver
                        )
                        let gameId = state.game.id
                        let winnersCount = state.game.winners.count
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
                        return .run { [analyticsClient] _ in
                            await liveActivityClient.end(endState)
                            do {
                                try await apiClient.updateGameStatus(gameId, .done)
                                analyticsClient.gameEnded(reason: "zone_collapsed", winnersCount: winnersCount)
                            } catch {
                                logger.error("Failed to update game status to done: \(error.localizedDescription)")
                            }
                        }
                    }
                    state.radius = result.newRadius
                    state.nextRadiusUpdate = result.newNextUpdate
                    state.mapCircle = result.newCircle

                    // Spawn periodic power-ups on zone shrink
                    var spawnEffect: Effect<Action>? = nil
                    if !result.isGameOver, state.game.powerUps.enabled {
                        let nextBatch = state.lastSpawnBatchIndex + 1
                        state.lastSpawnBatchIndex = nextBatch
                        let center = state.mapCircle?.center ?? state.game.zone.center.toCLCoordinates
                        let currentRadius = Double(state.radius)
                        let seed = state.game.zone.driftSeed
                        let gId = state.game.id
                        let spawnTypes: [String]
                        if state.game.gameMode == .stayInTheZone {
                            let uselessInZone: Set<String> = [
                                PowerUp.PowerUpType.invisibility.rawValue,
                                PowerUp.PowerUpType.decoy.rawValue,
                                PowerUp.PowerUpType.jammer.rawValue
                            ]
                            spawnTypes = state.game.powerUps.enabledTypes.filter { !uselessInZone.contains($0) }
                        } else {
                            spawnTypes = state.game.powerUps.enabledTypes
                        }
                        spawnEffect = .run { _ in
                            let generated = generatePowerUps(
                                center: center,
                                radius: currentRadius,
                                count: AppConstants.powerUpPeriodicBatchSize,
                                driftSeed: seed,
                                batchIndex: nextBatch,
                                enabledTypes: spawnTypes
                            )
                            let newPowerUps = await snapPowerUpsToRoads(generated)
                            try? await apiClient.spawnPowerUps(gId, newPowerUps)
                        }
                    }
                    if let spawnEffect {
                        // Don't return early — fall through to zone check and LA update below
                        // We'll merge the spawn effect with any other effects at the end
                        var effects: [Effect<Action>] = [spawnEffect]

                        // Power-up proximity check — collect all nearby power-ups
                        let nearbyPowerUps = findNearbyPowerUps(
                            userLocation: state.userLocation,
                            availablePowerUps: state.availablePowerUps
                        )
                        if !nearbyPowerUps.isEmpty {
                            effects.append(contentsOf: nearbyPowerUps.map { .send(.powerUpCollected($0)) })
                        }

                        // Zone check (visual warning only — no elimination)
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
                            effects.append(.run { _ in
                                await liveActivityClient.update(laUpdate.newState)
                            })
                        }
                        return .merge(effects)
                    }
                }

                // Power-up proximity check — collect all nearby power-ups
                let nearbyPowerUps = findNearbyPowerUps(
                    userLocation: state.userLocation,
                    availablePowerUps: state.availablePowerUps
                )
                if !nearbyPowerUps.isEmpty {
                    return .merge(nearbyPowerUps.map { .send(.powerUpCollected($0)) })
                }

                // Zone check (visual warning only — no elimination)
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
    }
}
