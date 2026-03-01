//
//  HunterMap.swift
//  PouleParty
//
//  Created by Julien Rahier on 14/03/2024.
//

import ComposableArchitecture
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
        var outsideZoneSince: Date?
        var outsideZoneSeconds: Int = 0

        var hasGameStarted: Bool { nowDate >= game.hunterStartDate }
        var isCodeOnCooldown: Bool { codeCooldownUntil.map { nowDate < $0 } ?? false }
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case cancelGameButtonTapped
        case destination(PresentationAction<Destination.Action>)
        case dismissCountdown
        case dismissGameInfo
        case dismissWinnerNotification
        case foundButtonTapped
        case goToMenu
        case goToVictory
        case infoButtonTapped
        case newLocationFetched(CLLocationCoordinate2D)
        case onTask
        case setGameTriggered(to: Game)
        case submitFoundCode
        case timerTicked
        case userLocationUpdated(CLLocationCoordinate2D)
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
    @Dependency(\.authClient) var authClient
    @Dependency(\.continuousClock) var clock
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
                return .send(.goToMenu)
            case .destination(.presented(.alert(.gameOver))):
                return .send(.goToMenu)
            case .destination:
                return .none
            case .dismissCountdown:
                state.countdownNumber = nil
                state.countdownText = nil
                return .none
            case .dismissGameInfo:
                state.showGameInfo = false
                return .none
            case .dismissWinnerNotification:
                state.winnerNotification = nil
                return .none
            case .foundButtonTapped:
                state.isEnteringFoundCode = true
                return .none
            case .submitFoundCode:
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
                    try await apiClient.addWinner(gameId, winner)
                    locationClient.stopTracking()
                    await send(.goToVictory)
                }
            case .goToMenu:
                return .none
            case .goToVictory:
                return .none
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
                if let uid = authClient.currentUserId() {
                    state.hunterId = uid
                }
                let gameId = state.game.id
                let gameMod = state.game.gameMod
                let hunterId = state.hunterId
                let hunterStartDate = state.game.hunterStartDate
                let (lastUpdate, lastRadius) = state.game.findLastUpdate()
                state.radius = lastRadius
                state.nextRadiusUpdate = lastUpdate

                var effects: [Effect<Action>] = [
                    .run { _ in
                        try await apiClient.registerHunter(gameId, hunterId)
                    },
                    .run { send in
                        for await game in apiClient.gameConfigStream(gameId) {
                            if let game {
                                await send(.setGameTriggered(to: game))
                            }
                        }
                    },
                    .run { send in
                        for await _ in self.clock.timer(interval: .seconds(1)) {
                            await send(.timerTicked)
                        }
                    }
                ]

                // followTheChicken & mutualTracking: circle follows the chicken
                // stayInTheZone: circle stays fixed on initial coordinates (no chicken stream)
                // Gated behind hunterStartDate to avoid leaking position early
                if gameMod != .stayInTheZone {
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
                }

                // Hunter always tracks own location (for zone check).
                // In mutualTracking mode, also writes to Firestore.
                // Gated behind hunterStartDate.
                let shouldWriteLocation = gameMod == .mutualTracking
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
            case let .setGameTriggered(game):
                // React to game cancelled/ended by chicken or Cloud Function
                if game.status == .done, state.destination == nil {
                    locationClient.stopTracking()
                    state.game = game
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
                    return .none
                }

                let (lastUpdate, lastRadius) = game.findLastUpdate()

                state.game = game
                state.radius = lastRadius
                state.nextRadiusUpdate = lastUpdate

                if game.gameMod != .stayInTheZone, let currentCircle = state.mapCircle {
                    state.mapCircle = CircleOverlay(
                        center: currentCircle.center,
                        radius: CLLocationDistance(state.radius)
                    )
                } else {
                    state.mapCircle = CircleOverlay(
                        center: game.initialCoordinates.toCLCoordinates,
                        radius: CLLocationDistance(state.radius)
                    )
                }

                // Detect new winners
                if let notification = detectNewWinners(
                    winners: game.winners,
                    previousCount: state.previousWinnersCount,
                    ownHunterId: state.hunterId
                ) {
                    state.winnerNotification = notification
                    state.previousWinnersCount = game.winners.count
                    return .run { send in
                        try await clock.sleep(for: .seconds(AppConstants.winnerNotificationSeconds))
                        await send(.dismissWinnerNotification)
                    }
                }
                state.previousWinnersCount = game.winners.count
                return .none
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
                        await send(.dismissCountdown)
                    }
                }

                guard state.destination == nil else { return .none }
                guard state.hasGameStarted else { return .none }

                // Game over by time
                if checkGameOverByTime(endDate: state.game.endDate) {
                    locationClient.stopTracking()
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
                    return .none
                }

                // Radius update
                if let result = processRadiusUpdate(
                    nextRadiusUpdate: state.nextRadiusUpdate,
                    currentRadius: state.radius,
                    radiusDeclinePerUpdate: state.game.radiusDeclinePerUpdate,
                    radiusIntervalUpdate: state.game.radiusIntervalUpdate,
                    gameMod: state.game.gameMod,
                    initialCoordinates: state.game.initialCoordinates.toCLCoordinates,
                    currentCircle: state.mapCircle
                ) {
                    if result.isGameOver {
                        locationClient.stopTracking()
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
                        return .none
                    }
                    state.radius = result.newRadius
                    state.nextRadiusUpdate = result.newNextUpdate
                    state.mapCircle = result.newCircle
                }

                // Zone check
                if shouldCheckZone(role: .hunter, gameMod: state.game.gameMod),
                   let userLoc = state.userLocation,
                   let circle = state.mapCircle {
                    let zoneResult = checkZoneStatus(
                        userLocation: userLoc,
                        zoneCenter: circle.center,
                        zoneRadius: circle.radius
                    )
                    if zoneResult.isOutsideZone {
                        if state.outsideZoneSince == nil {
                            state.outsideZoneSince = .now
                        }
                        let elapsed = Int(Date.now.timeIntervalSince(state.outsideZoneSince!))
                        state.outsideZoneSeconds = max(0, AppConstants.outsideZoneGracePeriodSeconds - elapsed)
                        state.isOutsideZone = true
                    } else {
                        state.isOutsideZone = false
                        state.outsideZoneSince = nil
                        state.outsideZoneSeconds = 0
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

    private var hunterSubtitle: String {
        switch store.game.gameMod {
        case .followTheChicken:
            return "Catch the 🐔 !"
        case .stayInTheZone:
            return "Stay in the zone 📍"
        case .mutualTracking:
            return "Catch the 🐔 (she sees you! 👀)"
        }
    }

    private var overlayColor: UIColor {
        store.isOutsideZone ? UIColor.red.withAlphaComponent(0.4) : UIColor.black.withAlphaComponent(0.3)
    }

    var body: some View {
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

                // Zone border circle
                PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
                    .lineColor(StyleColor(UIColor.green.withAlphaComponent(0.7)))
                    .lineWidth(2)
            }
        }
        .ignoresSafeArea()
        .onChange(of: store.mapCircle) { _, newCircle in
            guard let center = newCircle?.center, let radius = newCircle?.radius else { return }
            withViewportAnimation(.default(maxDuration: 1)) {
                viewport = .camera(center: center, zoom: zoomForRadius(radius, latitude: center.latitude))
            }
        }
        .safeAreaInset(edge: .top) {
            HStack {
                Spacer()
                VStack {
                    Text("You are the Hunter")
                    Text(hunterSubtitle)
                        .font(.system(size: 12))
                }
                Spacer()
                Button {
                    self.store.send(.infoButtonTapped)
                } label: {
                    Image(systemName: "info.circle")
                        .font(.system(size: 20))
                        .foregroundStyle(.secondary)
                }
                .accessibilityLabel("Game info")
                .padding(.trailing, 4)
            }
            .padding()
            .background(.thinMaterial)
        }
        .safeAreaInset(edge: .bottom) {
            HStack {
                VStack(alignment: .leading) {
                    Text("Radius : \(self.store.radius)m")
                        .accessibilityLabel("Radius \(self.store.radius) meters")
                    CountdownView(nowDate: self.$store.nowDate, nextUpdateDate: self.$store.nextRadiusUpdate, chickenStartDate: store.game.startDate, hunterStartDate: store.game.hunterStartDate, isChicken: false)
                }
                Spacer()
                if store.hasGameStarted {
                    Button {
                        self.store.send(.foundButtonTapped)
                    } label: {
                        ZStack {
                            RoundedRectangle(cornerRadius: 5)
                                .fill(.red)
                            Text("FOUND")
                                .font(Font.system(size: 11))
                                .fontWeight(.bold)
                                .foregroundStyle(.white)
                        }
                    }
                    .accessibilityLabel("I found the chicken")
                    .frame(width: 50, height: 40)
                }
                Button {
                    self.store.send(.cancelGameButtonTapped)
                } label: {
                    Text("Quit")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(.ultraThinMaterial)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .accessibilityLabel("Quit game")
            }
            .padding()
            .background(.thinMaterial)
        }
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
                self.store.send(.submitFoundCode)
            }
            Button("Cancel", role: .cancel) { }
        } message: {
            Text("Enter the 4-digit code shown by the chicken.")
        }
        .sheet(isPresented: Binding(
            get: { self.store.showGameInfo },
            set: { _ in self.store.send(.dismissGameInfo) }
        )) {
            GameInfoSheet(game: self.store.game)
        }
        .overlay(alignment: .top) {
            WinnerNotificationOverlay(notification: store.winnerNotification)
        }
        .overlay(alignment: .top) {
            if store.isOutsideZone {
                ZoneWarningOverlay(secondsRemaining: store.outsideZoneSeconds)
            }
        }
        .overlay {
            GameStartCountdownOverlay(
                countdownNumber: store.countdownNumber,
                countdownText: store.countdownText
            )
        }
    }
}

/// Large rectangle centered on the given coordinate, used as the outer boundary for inverted zone overlay.
private func outerBoundsCoordinates(center: CLLocationCoordinate2D, padding: Double = 20.0) -> [CLLocationCoordinate2D] {
    let north = min(85.0, center.latitude + padding)
    let south = max(-85.0, center.latitude - padding)
    let west = center.longitude - padding
    let east = center.longitude + padding
    return [
        CLLocationCoordinate2D(latitude: north, longitude: west),
        CLLocationCoordinate2D(latitude: north, longitude: east),
        CLLocationCoordinate2D(latitude: south, longitude: east),
        CLLocationCoordinate2D(latitude: south, longitude: west)
    ]
}

#Preview {
    HunterMapView(store: Store(initialState: HunterMapFeature.State(game: .mock)) {
        HunterMapFeature()
    })
}
