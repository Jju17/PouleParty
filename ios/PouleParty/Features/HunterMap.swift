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

private let logger = Logger(subsystem: "dev.rahier.pouleparty", category: "HunterMap")

@Reducer
struct HunterMapFeature {

    @ObservableState
    struct State: Equatable {
        @Presents var destination: Destination.State?
        var game: Game
        var hunterId: String = ""
        var hunterName: String = "Hunter"
        var enteredCode: String = ""
        var isEnteringFoundCode: Bool = false
        var nextRadiusUpdate: Date?
        var nowDate: Date = .now
        var previousWinnersCount: Int = 0
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
        var availablePowerUps: [PowerUp] = []
        var collectedPowerUps: [PowerUp] = []
        var showPowerUpInventory: Bool = false
        var powerUpNotification: String? = nil
        var lastActivatedPowerUpType: PowerUp.PowerUpType? = nil
        var previewCircle: CircleOverlay? = nil
        var decoyLocation: CLLocationCoordinate2D? = nil

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
        case cancelGameButtonTapped
        case countdownDismissed
        case destination(PresentationAction<Destination.Action>)
        case foundButtonTapped
        case gameConfigUpdated(Game)
        case gameInfoDismissed
        case infoButtonTapped
        case newLocationFetched(CLLocationCoordinate2D)
        case onTask
        case returnedToMenu
        case submitCodeButtonTapped
        case timerTicked
        case userLocationUpdated(CLLocationCoordinate2D)
        case winnerNotificationDismissed
        case winnerRegistered
        case powerUpsUpdated([PowerUp])
        case powerUpCollected(PowerUp)
        case powerUpActivated(PowerUp)
        case powerUpNotificationDismissed
        case powerUpInventoryTapped
        case powerUpInventoryDismissed
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
            }
        }
    }

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.userClient) var userClient
    @Dependency(\.continuousClock) var clock
    @Dependency(\.liveActivityClient) var liveActivityClient
    @Dependency(\.locationClient) var locationClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding(\.enteredCode):
                state.enteredCode = String(state.enteredCode.prefix(AppConstants.foundCodeDigits))
                return .none
            case .binding:
                return .none
            case .cancelGameButtonTapped:
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
                    await send(.returnedToMenu)
                }
            case .destination(.presented(.alert(.gameOver))):
                state.previewCircle = nil
                return .run { send in
                    await liveActivityClient.end(nil)
                    await send(.returnedToMenu)
                }
            case .destination:
                return .none
            case .countdownDismissed:
                state.countdownNumber = nil
                state.countdownText = nil
                return .none
            case .gameInfoDismissed:
                state.showGameInfo = false
                return .none
            case .winnerNotificationDismissed:
                state.winnerNotification = nil
                return .none
            case let .powerUpsUpdated(allPowerUps):
                let hunterId = state.hunterId
                state.availablePowerUps = allPowerUps.filter { $0.type.isHunterPowerUp && !$0.isCollected }
                state.collectedPowerUps = allPowerUps.filter { $0.collectedBy == hunterId && $0.activatedAt == nil }
                return .none
            case let .powerUpCollected(powerUp):
                let gameId = state.game.id
                let hunterId = state.hunterId
                return .run { send in
                    do {
                        try await apiClient.collectPowerUp(gameId, powerUp.id, hunterId)
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

                // Zone Preview: compute next zone boundary client-side
                if powerUp.type == .zonePreview {
                    let nextRadius = state.radius - Int(state.game.radiusDeclinePerUpdate)
                    if nextRadius > 0, let center = state.mapCircle?.center {
                        state.previewCircle = CircleOverlay(center: center, radius: CLLocationDistance(nextRadius))
                    }
                }

                return .run { send in
                    try? await apiClient.activatePowerUp(gameId, powerUp.id, expiresAt)
                    if powerUp.type == .radarPing {
                        try? await apiClient.updateGameActiveEffect(gameId, "activeRadarPingUntil", expiresAt)
                    }
                    try await clock.sleep(for: .seconds(2))
                    await send(.powerUpNotificationDismissed)
                }
            case .powerUpNotificationDismissed:
                state.powerUpNotification = nil
                return .none
            case .powerUpInventoryTapped:
                state.showPowerUpInventory = true
                return .none
            case .powerUpInventoryDismissed:
                state.showPowerUpInventory = false
                return .none
            case .foundButtonTapped:
                state.isEnteringFoundCode = true
                return .none
            case .submitCodeButtonTapped:
                guard !state.isCodeOnCooldown else {
                    return .none
                }
                let code = state.enteredCode.trimmingCharacters(in: .whitespacesAndNewlines)
                state.enteredCode = ""
                state.isEnteringFoundCode = false

                guard code == state.game.foundCode else {
                    state.wrongCodeAttempts += 1
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

                let winner = Winner(
                    hunterId: state.hunterId,
                    hunterName: state.hunterName,
                    timestamp: .now
                )
                let gameId = state.game.id
                return .run { send in
                    do {
                        try await apiClient.addWinner(gameId, winner)
                        locationClient.stopTracking()
                        await send(.winnerRegistered)
                    } catch {
                        locationClient.stopTracking()
                        logger.error("Failed to add winner: \(error.localizedDescription)")
                        await send(.winnerRegistered)
                    }
                }
            case .returnedToMenu:
                return .none
            case .winnerRegistered:
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
            case .infoButtonTapped:
                state.showGameInfo = true
                return .none
            case let .newLocationFetched(location):
                state.mapCircle = CircleOverlay(
                    center: location,
                    radius: CLLocationDistance(state.radius)
                )
                return .none
            case .onTask:
                let rawUid = userClient.currentUserId()
                let gameId = state.game.id
                let gameMod = state.game.gameMod
                logger.info("HunterMap.onTask — currentUserId: \(rawUid ?? "nil"), gameId: \(gameId), gameMod: \(String(describing: gameMod))")
                if let uid = rawUid, !uid.isEmpty {
                    state.hunterId = uid
                }
                let hunterId = state.hunterId
                guard !hunterId.isEmpty else {
                    logger.error("hunterId is empty — cannot register hunter or write location. rawUid was: \(rawUid ?? "nil")")
                    return .none
                }
                let chickenCanSeeHunters = state.game.chickenCanSeeHunters
                logger.info("HunterMap.onTask — hunterId set to: \(hunterId), shouldWriteLocation: \(chickenCanSeeHunters)")
                let hunterStartDate = state.game.hunterStartDate
                let (lastUpdate, lastRadius) = state.game.findLastUpdate()
                state.radius = lastRadius
                state.nextRadiusUpdate = lastUpdate

                // Start Live Activity
                let attributes = PoulePartyAttributes(
                    gameName: state.game.name,
                    gameCode: state.game.gameCode,
                    playerRole: .hunter,
                    gameModeName: state.game.gameMod.title,
                    gameStartDate: state.game.startDate,
                    gameEndDate: state.game.endDate,
                    totalHunters: max(0, state.game.numberOfPlayers - 1)
                )
                let initialLAState = state.liveActivityState
                state.lastLiveActivityState = initialLAState

                var effects: [Effect<Action>] = [
                    .run { _ in
                        await liveActivityClient.start(attributes, initialLAState)
                    },
                    .run { _ in
                        try await apiClient.registerHunter(gameId, hunterId)
                    },
                    .run { send in
                        for await game in apiClient.gameConfigStream(gameId) {
                            if let game {
                                await send(.gameConfigUpdated( game))
                            }
                        }
                    },
                    .run { send in
                        for await _ in self.clock.timer(interval: .seconds(1)) {
                            await send(.timerTicked)
                        }
                    },
                    .run { send in
                        for await powerUps in apiClient.powerUpsStream(gameId) {
                            await send(.powerUpsUpdated(powerUps))
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
                                await send(.newLocationFetched(location))
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
                            await send(.userLocationUpdated(currentLocation))
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
                            await send(.userLocationUpdated(coordinate))
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

                return .merge(effects)
            case let .gameConfigUpdated(game):
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

                // Detect newly activated power-ups (compare old vs new)
                let now = Date.now
                var powerUpNotificationText: String?
                var powerUpNotificationType: PowerUp.PowerUpType?

                if let until = game.activeInvisibilityUntil?.dateValue(), until > now,
                   state.game.activeInvisibilityUntil?.dateValue() != until {
                    powerUpNotificationText = "\(PowerUp.PowerUpType.invisibility.emoji) Invisibility activated!"
                    powerUpNotificationType = .invisibility
                }
                if let until = game.activeZoneFreezeUntil?.dateValue(), until > now,
                   state.game.activeZoneFreezeUntil?.dateValue() != until {
                    powerUpNotificationText = "\(PowerUp.PowerUpType.zoneFreeze.emoji) Zone Freeze activated!"
                    powerUpNotificationType = .zoneFreeze
                }
                if let until = game.activeRadarPingUntil?.dateValue(), until > now,
                   state.game.activeRadarPingUntil?.dateValue() != until {
                    powerUpNotificationText = "\(PowerUp.PowerUpType.radarPing.emoji) Radar Ping activated!"
                    powerUpNotificationType = .radarPing
                }
                if let until = game.activeDecoyUntil?.dateValue(), until > now,
                   state.game.activeDecoyUntil?.dateValue() != until {
                    powerUpNotificationText = "\(PowerUp.PowerUpType.decoy.emoji) Decoy activated!"
                    powerUpNotificationType = .decoy
                }
                if let until = game.activeJammerUntil?.dateValue(), until > now,
                   state.game.activeJammerUntil?.dateValue() != until {
                    powerUpNotificationText = "\(PowerUp.PowerUpType.jammer.emoji) Jammer activated!"
                    powerUpNotificationType = .jammer
                }

                state.game = game
                state.radius = lastRadius
                state.nextRadiusUpdate = lastUpdate

                if game.gameMod != .stayInTheZone, let currentCircle = state.mapCircle {
                    state.mapCircle = CircleOverlay(
                        center: currentCircle.center,
                        radius: CLLocationDistance(state.radius)
                    )
                } else {
                    let interpolatedCenter = interpolateZoneCenter(
                        initialCenter: game.initialCoordinates.toCLCoordinates,
                        finalCenter: game.finalLocation,
                        initialRadius: game.initialRadius,
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
                        // Generate fake location at random offset (200-500m from circle center)
                        let angle = Double.random(in: 0..<2 * .pi)
                        let distance = Double.random(in: 200...500) / 111_320.0 // degrees
                        state.decoyLocation = CLLocationCoordinate2D(
                            latitude: center.latitude + distance * cos(angle),
                            longitude: center.longitude + distance * sin(angle) / cos(center.latitude * .pi / 180)
                        )
                    }
                } else {
                    state.decoyLocation = nil
                }

                // Update Live Activity with new game state
                let currentLAState = state.liveActivityState
                var effects: [Effect<Action>] = []
                if currentLAState != state.lastLiveActivityState {
                    state.lastLiveActivityState = currentLAState
                    effects.append(.run { _ in
                        await liveActivityClient.update(currentLAState)
                    })
                }

                // Show global power-up notification
                if let text = powerUpNotificationText {
                    state.powerUpNotification = text
                    state.lastActivatedPowerUpType = powerUpNotificationType
                    effects.append(.run { send in
                        try await clock.sleep(for: .seconds(2))
                        await send(.powerUpNotificationDismissed)
                    })
                }

                // Detect new winners
                if let notification = detectNewWinners(
                    winners: game.winners,
                    previousCount: state.previousWinnersCount,
                    ownHunterId: state.hunterId
                ) {
                    state.winnerNotification = notification
                    state.previousWinnersCount = game.winners.count
                    effects.append(.run { send in
                        try await clock.sleep(for: .seconds(AppConstants.winnerNotificationSeconds))
                        await send(.winnerNotificationDismissed)
                    })
                    return .merge(effects)
                }
                state.previousWinnersCount = game.winners.count
                return effects.isEmpty ? .none : .merge(effects)
            case .timerTicked:
                state.nowDate = .now

                // Countdown phases (hunter perspective)
                let countdownResult = evaluateCountdown(
                    phases: [
                        CountdownPhase(
                            targetDate: state.game.startDate,
                            completionText: "🐔 is hiding!",
                            showNumericCountdown: true,
                            isEnabled: state.game.chickenHeadStartMinutes > 0
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
                        await send(.countdownDismissed)
                    }
                }

                guard state.destination == nil else { return .none }
                guard state.hasGameStarted else { return .none }

                // Game over by time
                if checkGameOverByTime(endDate: state.game.endDate) {
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
                    radiusDeclinePerUpdate: state.game.radiusDeclinePerUpdate,
                    radiusIntervalUpdate: state.game.radiusIntervalUpdate,
                    gameMod: state.game.gameMod,
                    initialCoordinates: state.game.initialCoordinates.toCLCoordinates,
                    currentCircle: state.mapCircle,
                    driftSeed: state.game.driftSeed,
                    isZoneFrozen: state.game.isZoneFrozen,
                    finalCoordinates: state.game.finalLocation,
                    initialRadius: state.game.initialRadius
                ) {
                    if result.isGameOver {
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
                if let userLoc = state.userLocation {
                    let nearbyPowerUps = state.availablePowerUps.filter { powerUp in
                        CLLocation(latitude: userLoc.latitude, longitude: userLoc.longitude)
                            .distance(from: CLLocation(latitude: powerUp.coordinate.latitude, longitude: powerUp.coordinate.longitude))
                            <= AppConstants.powerUpCollectionRadiusMeters
                    }
                    if !nearbyPowerUps.isEmpty {
                        return .merge(nearbyPowerUps.map { .send(.powerUpCollected($0)) })
                    }
                }

                // Zone check (visual warning only — no elimination)
                if shouldCheckZone(role: .hunter, gameMod: state.game.gameMod),
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
                let currentLAState = state.liveActivityState
                if currentLAState != state.lastLiveActivityState {
                    state.lastLiveActivityState = currentLAState
                    return .run { _ in
                        await liveActivityClient.update(currentLAState)
                    }
                }
                return .none
            case let .userLocationUpdated(location):
                state.userLocation = location
                return .none
            }
        }
        .ifLet(\.$destination, action: \.destination) {
          Destination()
        }
    }
}

struct HunterMapView: View {
    @Bindable var store: StoreOf<HunterMapFeature>
    @State private var viewport: Viewport = .camera(
        center: CLLocationCoordinate2D(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude),
        zoom: 14
    )
    @State private var mapBearing: Double = 0
    @State private var selectedPowerUp: PowerUp?

    private var hunterSubtitle: String {
        if store.game.chickenCanSeeHunters {
            return "Catch the 🐔 (she sees you! 👀)"
        }
        switch store.game.gameMod {
        case .followTheChicken:
            return "Catch the 🐔 !"
        case .stayInTheZone:
            return "Stay in the zone 📍"
        }
    }

    private var overlayColor: UIColor {
        store.isOutsideZone ? UIColor(Color.zoneDanger).withAlphaComponent(0.4) : UIColor.black.withAlphaComponent(0.3)
    }

    private var compassButton: some View {
        Button {
            withViewportAnimation(.default(maxDuration: 0.5)) {
                if let circle = store.mapCircle {
                    viewport = .camera(
                        center: circle.center,
                        zoom: zoomForRadius(circle.radius, latitude: circle.center.latitude),
                        bearing: 0
                    )
                }
            }
        } label: {
            Image(systemName: "location.north.fill")
                .rotationEffect(.degrees(-mapBearing))
                .frame(width: 40, height: 40)
                .background(Color.surface)
                .clipShape(Circle())
                .shadow(color: .black.opacity(0.2), radius: 4, y: 2)
        }
        .padding(.trailing, 8)
        .padding(.top, 8)
    }

    private var topBar: some View {
        HStack {
            Spacer()
            VStack(spacing: 2) {
                BangerText("You are the Hunter", size: 20)
                    .foregroundStyle(.white)
                    .shadow(color: .black.opacity(0.3), radius: 2, y: 1)
                Text(hunterSubtitle)
                    .font(.gameboy(size: 10))
                    .foregroundStyle(.white.opacity(0.8))
            }
            Spacer()
            Button {
                self.store.send(.infoButtonTapped)
            } label: {
                Image(systemName: "info.circle")
                    .font(.system(size: 20))
                    .foregroundStyle(.white.opacity(0.8))
            }
            .accessibilityLabel("Game info")
            .padding(.trailing, 4)
        }
        .padding()
        .background(
            LinearGradient(colors: [.hunterRed, .CRPink], startPoint: .leading, endPoint: .trailing)
        )
    }

    private var bottomBar: some View {
        HStack {
            VStack(alignment: .leading) {
                Text("Radius : \(self.store.radius)m")
                    .font(.gameboy(size: 14))
                    .foregroundStyle(.white)
                    .accessibilityLabel("Radius \(self.store.radius) meters")
                CountdownView(nowDate: self.$store.nowDate, nextUpdateDate: self.$store.nextRadiusUpdate, chickenStartDate: store.game.startDate, hunterStartDate: store.game.hunterStartDate, isChicken: false)
            }
            Spacer()
            if !store.collectedPowerUps.isEmpty {
                Button {
                    self.store.send(.powerUpInventoryTapped)
                } label: {
                    ZStack {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color.CROrange)
                        HStack(spacing: 2) {
                            Image(systemName: "bolt.fill")
                                .font(.system(size: 10))
                            Text("\(store.collectedPowerUps.count)")
                                .font(.system(size: 11, weight: .bold))
                        }
                        .foregroundStyle(.white)
                    }
                }
                .accessibilityLabel("Power-ups inventory")
                .frame(width: 44, height: 40)
                .neonGlow(.CROrange, intensity: .subtle)
            }
            if store.hasGameStarted {
                Button {
                    self.store.send(.foundButtonTapped)
                } label: {
                    ZStack {
                        Capsule()
                            .fill(Color.hunterRed)
                        Text("FOUND")
                            .font(Font.system(size: 11))
                            .fontWeight(.bold)
                            .foregroundStyle(.white)
                    }
                }
                .accessibilityLabel("I found the chicken")
                .frame(width: 50, height: 40)
                .neonGlow(.hunterRed, intensity: .subtle)
            }
        }
        .padding()
        .background(Color.darkBackground.opacity(0.85))
    }

    private var mapView: some View {
        Map(viewport: $viewport) {
            Puck2D(bearing: .heading)

            // Inverted zone overlay (only visible after game starts)
            if store.hasGameStarted, let circle = self.store.mapCircle {
                let circlePolygon = Polygon(center: circle.center, radius: circle.radius, vertices: 72)
                let outerCoords = outerBoundsCoordinates(center: circle.center)
                let invertedPolygon = Polygon(
                    outerRing: Ring(coordinates: outerCoords + [outerCoords[0]]),
                    innerRings: [circlePolygon.outerRing]
                )
                PolygonAnnotation(polygon: invertedPolygon)
                    .fillColor(StyleColor(overlayColor))
                    .fillOpacity(1.0)

                // Zone border circle — neon glow effect (layered polylines)
                PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
                    .lineColor(StyleColor(UIColor(Color.zoneGreen).withAlphaComponent(0.08)))
                    .lineWidth(16)
                PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
                    .lineColor(StyleColor(UIColor(Color.zoneGreen).withAlphaComponent(0.15)))
                    .lineWidth(8)
                PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
                    .lineColor(StyleColor(UIColor(Color.zoneGreen).withAlphaComponent(0.35)))
                    .lineWidth(4)
                PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
                    .lineColor(StyleColor(UIColor(Color.zoneGreen).withAlphaComponent(0.9)))
                    .lineWidth(2.5)
            }

            // Zone Preview power-up effect (dashed preview of next zone)
            if let preview = store.previewCircle {
                let previewPolygon = Polygon(center: preview.center, radius: preview.radius, vertices: 72)
                PolylineAnnotation(lineCoordinates: previewPolygon.outerRing.coordinates)
                    .lineColor(StyleColor(UIColor(Color.powerupFreeze).withAlphaComponent(0.6)))
                    .lineWidth(2)
            }

            // Power-up markers (hunter power-ups only)
            if store.hasGameStarted {
                ForEvery(store.availablePowerUps) { powerUp in
                    MapViewAnnotation(coordinate: powerUp.coordinate) {
                        Button {
                            selectedPowerUp = powerUp
                        } label: {
                            Image(systemName: powerUp.type.iconName)
                                .font(.system(size: 20))
                                .foregroundStyle(.white)
                                .padding(8)
                                .background(powerUp.type.color)
                                .clipShape(Circle())
                                .shadow(color: powerUp.type.color.opacity(0.5), radius: 6, y: 2)
                        }
                    }
                    .allowOverlap(true)
                    .allowOverlapWithPuck(true)
                }
            }

            // Decoy: fake chicken marker when decoy is active
            if let decoyLocation = store.decoyLocation {
                MapViewAnnotation(coordinate: decoyLocation) {
                    Text("🐔")
                        .font(.system(size: 28))
                        .shadow(color: .black.opacity(0.3), radius: 4, y: 2)
                }
                .allowOverlap(true)
                .allowOverlapWithPuck(true)
            }
        }
        .onCameraChanged { context in
            mapBearing = context.cameraState.bearing
        }
        .ignoresSafeArea()
        .onChange(of: store.mapCircle) { _, newCircle in
            guard let center = newCircle?.center, let radius = newCircle?.radius else { return }
            withViewportAnimation(.default(maxDuration: 1)) {
                viewport = .camera(center: center, zoom: zoomForRadius(radius, latitude: center.latitude))
            }
        }
        .overlay(alignment: .topTrailing) {
            VStack(spacing: 0) {
                compassButton
                if store.game.powerUpsEnabled {
                    ActivePowerUpBadge(game: store.game)
                }
            }
        }
        .safeAreaInset(edge: .top) { topBar }
        .safeAreaInset(edge: .bottom) { bottomBar }
    }

    var body: some View {
        mapView
            .task {
                self.store.send(.onTask)
            }
            .alert(
                $store.scope(
                    state: \.destination?.alert,
                    action: \.destination.alert
                )
            )
            .alert("Enter Found Code", isPresented: $store.isEnteringFoundCode) {
                TextField("4-digit code", text: $store.enteredCode)
                    .keyboardType(.numberPad)
                Button("Submit") {
                    self.store.send(.submitCodeButtonTapped)
                }
                Button("Cancel", role: .cancel) { }
            } message: {
                Text("Enter the 4-digit code shown by the chicken.")
            }
            .sheet(isPresented: Binding(
                get: { self.store.showGameInfo },
                set: { _ in self.store.send(.gameInfoDismissed) }
            )) {
                GameInfoSheet(
                    game: self.store.game,
                    onCancelGame: { self.store.send(.cancelGameButtonTapped) },
                    leaveGameLabel: "Leave game"
                )
            }
            .overlay(alignment: .top) {
                WinnerNotificationOverlay(notification: store.winnerNotification)
            }
            .overlay(alignment: .top) {
                if store.isOutsideZone {
                    ZoneWarningOverlay()
                }
            }
            .overlay {
                GameStartCountdownOverlay(
                    countdownNumber: store.countdownNumber,
                    countdownText: store.countdownText
                )
            }
            .overlay {
                if !store.hasGameStarted {
                    PreGameOverlay(
                        role: .hunter,
                        gameModTitle: store.game.gameMod.title,
                        gameCode: nil,
                        targetDate: store.game.hunterStartDate,
                        nowDate: store.nowDate,
                        connectedHunters: store.game.hunterIds.count
                    )
                }
            }
            .overlay(alignment: .top) {
                if let notification = store.powerUpNotification {
                    Text(notification)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background((store.lastActivatedPowerUpType?.color ?? Color.CROrange).opacity(0.9))
                        .neonGlow(store.lastActivatedPowerUpType?.color ?? .CROrange, intensity: .subtle)
                        .clipShape(Capsule())
                        .padding(.top, 100)
                        .transition(.move(edge: .top).combined(with: .opacity))
                        .animation(.easeInOut, value: store.powerUpNotification)
                }
            }
            .sheet(isPresented: Binding(
                get: { self.store.showPowerUpInventory },
                set: { _ in self.store.send(.powerUpInventoryDismissed) }
            )) {
                PowerUpInventorySheet(
                    powerUps: store.collectedPowerUps,
                    onActivate: { powerUp in
                        store.send(.powerUpActivated(powerUp))
                    }
                )
            }
            .sheet(item: $selectedPowerUp) { powerUp in
                PowerUpDetailSheet(powerUpType: powerUp.type)
                    .presentationDetents([.medium, .large])
            }
    }
}

#Preview {
    HunterMapView(store: Store(initialState: HunterMapFeature.State(game: .mock)) {
        HunterMapFeature()
    })
}
