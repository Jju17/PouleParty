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
        var hasChallenges: Bool = false

        // MARK: - MapFeatureState passthroughs (child → parent surface)
        var availablePowerUps: [PowerUp] { powerUps.available }
        var collectedPowerUps: [PowerUp] { powerUps.collected }
        var showPowerUpInventory: Bool { powerUps.showInventory }
        var powerUpNotification: String? { powerUps.notification }
        var lastActivatedPowerUpType: PowerUp.PowerUpType? { powerUps.lastActivatedType }

        var hasChickenStarted: Bool { nowDate >= game.startDate }
        var hasGameStarted: Bool { nowDate >= game.hunterStartDate }
        var isCodeOnCooldown: Bool { codeCooldownUntil.map { nowDate < $0 } ?? false }

        var currentGamePhase: PoulePartyAttributes.ContentState.GamePhase {
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
                gamePhase: currentGamePhase
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
            case powerUpCollected(PowerUp)
            case powerUpsUpdated([PowerUp])
            case registrationRequiredDetected
            case timerTicked
            case userLocationUpdated(CLLocationCoordinate2D)
            case winnerNotificationDismissed
            case winnerRegistered
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
                case registrationRequired
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
                state.previewCircle = nil
                let gameId = state.game.id
                return .run { send in
                    await liveActivityClient.end(nil)
                    // Fallback: also update status from hunter side in case chicken didn't
                    do {
                        try await apiClient.updateGameStatus(gameId, .done)
                    } catch {
                        logger.error("Failed to update game status to done: \(error)")
                    }
                    // Show leaderboard instead of returning to menu directly
                    await send(.delegate(.allHuntersFound))
                }
            case .destination(.presented(.alert(.registrationRequired))):
                return .run { send in
                    await liveActivityClient.end(nil)
                    await send(.delegate(.returnedToMenu))
                }
            case .internal(.registrationRequiredDetected):
                state.destination = .alert(
                    AlertState {
                        TextState("Registration required")
                    } actions: {
                        ButtonState(action: .registrationRequired) {
                            TextState("OK")
                        }
                    } message: {
                        TextState("You must register for this game before joining. Ask the chicken for the registration link.")
                    }
                )
                return .none
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
                let gameId = state.game.id
                let hunterId = state.hunterId
                return .run { [analyticsClient] _ in
                    do {
                        try await apiClient.collectPowerUp(gameId, powerUp.id, hunterId)
                        analyticsClient.powerUpCollected(type: powerUp.type.rawValue, role: "hunter")
                    } catch {
                        logger.error("Failed to collect power-up: \(error)")
                    }
                }
            case let .powerUps(.delegate(.activated(powerUp))):
                let gameId = state.game.id
                let duration = powerUp.type.durationSeconds ?? 0
                let expiresAt = Timestamp(date: .now.addingTimeInterval(duration))

                // Zone Preview: compute next zone boundary client-side
                if powerUp.type == .zonePreview {
                    let nextRadius = state.radius - Int(state.game.zone.shrinkMetersPerUpdate)
                    if nextRadius > 0, let center = state.mapCircle?.center {
                        state.previewCircle = CircleOverlay(center: center, radius: CLLocationDistance(nextRadius))
                    }
                }

                return .run { [analyticsClient] send in
                    let effectField: String? = powerUp.type == .radarPing ? powerUp.type.firestoreEffectField : nil
                    try? await apiClient.activatePowerUp(gameId, powerUp.id, effectField, expiresAt)
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
                let code = state.enteredCode.trimmingCharacters(in: .whitespacesAndNewlines)
                state.enteredCode = ""
                state.isEnteringFoundCode = false

                guard code == state.game.foundCode else {
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
                }

                HapticManager.notification(.success)

                let winner = Winner(
                    hunterId: state.hunterId,
                    hunterName: state.hunterName,
                    timestamp: Timestamp(date: .now)
                )
                let gameId = state.game.id
                let totalAttempts = state.wrongCodeAttempts + 1
                return .run { [analyticsClient] send in
                    do {
                        try await apiClient.addWinner(gameId, winner)
                        analyticsClient.hunterFoundChicken(attempts: totalAttempts)
                        locationClient.stopTracking()
                        await send(.internal(.winnerRegistered))
                    } catch {
                        locationClient.stopTracking()
                        logger.error("Failed to add winner: \(error.localizedDescription)")
                        await send(.internal(.winnerRegistered))
                    }
                }
            case .delegate(.returnedToMenu):
                return .none
            case .delegate(.allHuntersFound):
                return .none
            case .internal(.winnerRegistered):
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
                    myTeamName: fallbackName
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
                state.mapCircle = CircleOverlay(
                    center: location,
                    radius: CLLocationDistance(state.radius)
                )
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

                let requiresRegistration = state.game.registration.required
                let gameMode = state.game.gameMode.rawValue
                let gameCode = state.game.gameCode
                var effects: [Effect<Action>] = [
                    .run { _ in
                        await liveActivityClient.start(attributes, initialLAState)
                    },
                    .run { [analyticsClient] send in
                        // Defensive gate: if game requires registration, ensure user has one before
                        // calling registerHunter (which would fail the Firestore rule and silently
                        // break the rest of the screen).
                        if requiresRegistration {
                            let registration = try? await apiClient.findRegistration(gameId, hunterId)
                            if registration == nil {
                                logger.warning("Hunter \(hunterId) not registered for game \(gameId) — bouncing back")
                                await send(.internal(.registrationRequiredDetected))
                                return
                            }
                        }
                        do {
                            try await apiClient.registerHunter(gameId, hunterId)
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
                        for await location in apiClient.chickenLocationStream(gameId) {
                            if let location {
                                await send(.internal(.newLocationFetched(location)))
                            }
                        }
                    }
                )

                // Hunter always tracks own location (for zone check).
                // When chickenCanSeeHunters, also writes to Firestore.
                // Gated behind hunterStartDate.
                let shouldWriteLocation = state.game.chickenCanSeeHunters
                effects.append(
                    .run { send in
                        let delay = hunterStartDate.timeIntervalSinceNow
                        if delay > 0 {
                            try await clock.sleep(for: .seconds(delay))
                        }
                        // Send current location immediately
                        if let currentLocation = locationClient.lastLocation() {
                            await send(.internal(.userLocationUpdated(currentLocation)))
                            if shouldWriteLocation {
                                do {
                                    try apiClient.setHunterLocation(gameId, hunterId, currentLocation)
                                } catch {
                                    logger.error("Failed to send initial hunter location: \(error)")
                                }
                            }
                        }
                        var lastWrite = Date.now
                        for await coordinate in locationClient.startTracking() {
                            await send(.internal(.userLocationUpdated(coordinate)))
                            if shouldWriteLocation,
                               Date.now.timeIntervalSince(lastWrite) >= AppConstants.locationThrottleSeconds {
                                do {
                                    try apiClient.setHunterLocation(gameId, hunterId, coordinate)
                                } catch {
                                    logger.error("Failed to send hunter location: \(error)")
                                }
                                lastWrite = .now
                            }
                        }
                    }
                )

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
                        // Deterministic fake location so all hunters see the same decoy
                        let decoyTimestamp = Int(game.powerUps.activeEffects.decoy?.dateValue().timeIntervalSince1970 ?? 0)
                        let seed = game.zone.driftSeed ^ decoyTimestamp
                        let angle = seededRandom(seed: seed, index: 0) * 2 * .pi
                        let distance = (200 + seededRandom(seed: seed, index: 1) * 300) / 111_320.0
                        state.decoyLocation = CLLocationCoordinate2D(
                            latitude: center.latitude + distance * cos(angle),
                            longitude: center.longitude + distance * sin(angle) / cos(center.latitude * .pi / 180)
                        )
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

                // Navigate to victory when all hunters have found the chicken
                // (Chicken is authoritative for setting game status to DONE)
                if state.destination == nil &&
                   !game.hunterIds.isEmpty &&
                   game.winners.count >= game.hunterIds.count {
                    locationClient.stopTracking()
                    effects.append(.run { send in
                        await liveActivityClient.end(nil)
                        await send(.delegate(.allHuntersFound))
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
}

