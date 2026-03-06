//
//  ChickenMap.swift
//  PouleParty
//
//  Created by Julien Rahier on 16/03/2024.
//

import ComposableArchitecture
import MapboxMaps
import os
import SwiftUI

private let logger = Logger(subsystem: "dev.rahier.pouleparty", category: "ChickenMap")

struct HunterAnnotation: Equatable, Identifiable {
    let id: String
    var coordinate: CLLocationCoordinate2D
    var displayName: String
}

@Reducer
struct ChickenMapFeature {

    @ObservableState
    struct State: Equatable {
        @Presents var destination: Destination.State?
        var game: Game
        var hunterAnnotations: [HunterAnnotation] = []
        var nextRadiusUpdate: Date?
        var nowDate: Date = .now
        var previousWinnersCount: Int = 0
        var radius: Int = 1500
        var mapCircle: CircleOverlay?
        var showGameInfo: Bool = false
        var winnerNotification: String? = nil
        var countdownNumber: Int? = nil
        var countdownText: String? = nil
        var userLocation: CLLocationCoordinate2D?
        var isOutsideZone: Bool = false
        var lastLiveActivityState: PoulePartyAttributes.ContentState?

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

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.continuousClock) var clock
    @Dependency(\.liveActivityClient) var liveActivityClient
    @Dependency(\.locationClient) var locationClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case .destination(.presented(.alert(.cancelGame))):
                locationClient.stopTracking()
                liveActivityClient.end(nil)
                return .send(.returnedToMenu)
            case .destination(.presented(.alert(.gameOver))):
                liveActivityClient.end(nil)
                return .send(.returnedToMenu)
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
            case let .gameUpdated(game):
                state.game = game

                // Update Live Activity with new game state
                let currentLAState = state.liveActivityState
                if currentLAState != state.lastLiveActivityState {
                    state.lastLiveActivityState = currentLAState
                    liveActivityClient.update(currentLAState)
                }

                if let notification = detectNewWinners(
                    winners: game.winners,
                    previousCount: state.previousWinnersCount
                ) {
                    state.winnerNotification = notification
                    state.previousWinnersCount = game.winners.count
                    return .run { send in
                        try await clock.sleep(for: .seconds(AppConstants.winnerNotificationSeconds))
                        await send(.winnerNotificationDismissed)
                    }
                }
                state.previousWinnersCount = game.winners.count
                return .none
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
                if state.game.gameMod != .stayInTheZone {
                    state.mapCircle = CircleOverlay(
                        center: location,
                        radius: CLLocationDistance(state.radius)
                    )
                }
                return .none
            case .onTask:
                let gameId = state.game.id
                let gameMod = state.game.gameMod
                let startDate = state.game.startDate

                var effects: [Effect<Action>] = [
                    .run { send in
                        for await _ in self.clock.timer(interval: .seconds(1)) {
                            await send(.timerTicked)
                        }
                    },
                    .run { send in
                        for await game in apiClient.gameConfigStream(gameId) {
                            if let game {
                                await send(.gameUpdated(game))
                            }
                        }
                    }
                ]

                // followTheChicken & mutualTracking: chicken sends position to hunters
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
                                if Date.now.timeIntervalSince(lastWrite) >= AppConstants.locationThrottleSeconds {
                                    do {
                                        try apiClient.setChickenLocation(gameId, coordinate)
                                    } catch {
                                        logger.error("Failed to send chicken location: \(error)")
                                    }
                                    lastWrite = .now
                                }
                            }
                        }
                    )
                } else {
                    // stayInTheZone: chicken needs GPS for zone check (no Firestore writes)
                    effects.append(
                        .run { send in
                            let delay = startDate.timeIntervalSinceNow
                            if delay > 0 {
                                try await clock.sleep(for: .seconds(delay))
                            }
                            if let currentLocation = locationClient.lastLocation() {
                                await send(.newLocationFetched(currentLocation))
                            }
                            for await coordinate in locationClient.startTracking() {
                                await send(.newLocationFetched(coordinate))
                            }
                        }
                    )
                }

                // mutualTracking: chicken can see all hunters
                // Gated behind hunterStartDate (hunters aren't active until then)
                if gameMod == .mutualTracking {
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
                state.mapCircle = CircleOverlay(
                    center: state.game.initialCoordinates.toCLCoordinates,
                    radius: CLLocationDistance(state.radius)
                )

                // Start Live Activity
                let attributes = PoulePartyAttributes(
                    gameName: state.game.name,
                    gameCode: state.game.gameCode,
                    playerRole: .chicken,
                    gameModeName: state.game.gameMod.title,
                    gameStartDate: state.game.startDate,
                    gameEndDate: state.game.endDate,
                    totalHunters: max(0, state.game.numberOfPlayers - 1)
                )
                let initialLAState = state.liveActivityState
                state.lastLiveActivityState = initialLAState
                liveActivityClient.start(attributes, initialLAState)

                guard state.game.status == .waiting else { return .none }
                let gameId = state.game.id
                return .run { _ in
                    try? await apiClient.updateGameStatus(gameId, .inProgress)
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
                            isEnabled: state.game.chickenHeadStartMinutes > 0
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
                    locationClient.stopTracking()
                    liveActivityClient.end(PoulePartyAttributes.ContentState(
                        radiusMeters: state.radius,
                        nextShrinkDate: nil,
                        activeHunters: max(0, state.game.hunterIds.count - state.game.winners.count),
                        winnersCount: state.game.winners.count,
                        isOutsideZone: false,
                        gamePhase: .gameOver
                    ))
                    let gameId = state.game.id
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
                        do {
                            try await apiClient.updateGameStatus(gameId, .done)
                        } catch {
                            logger.error("Failed to update game status: \(error)")
                        }
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
                    driftSeed: state.game.driftSeed
                ) {
                    if result.isGameOver {
                        locationClient.stopTracking()
                        liveActivityClient.end(PoulePartyAttributes.ContentState(
                            radiusMeters: 0,
                            nextShrinkDate: nil,
                            activeHunters: max(0, state.game.hunterIds.count - state.game.winners.count),
                            winnersCount: state.game.winners.count,
                            isOutsideZone: false,
                            gamePhase: .gameOver
                        ))
                        let gameId = state.game.id
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
                            do {
                                try await apiClient.updateGameStatus(gameId, .done)
                            } catch {
                                logger.error("Failed to update game status to done: \(error.localizedDescription)")
                            }
                        }
                    }
                    state.radius = result.newRadius
                    state.nextRadiusUpdate = result.newNextUpdate
                    state.mapCircle = result.newCircle
                }

                // Zone check (visual warning only — no elimination)
                if shouldCheckZone(role: .chicken, gameMod: state.game.gameMod),
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
                    liveActivityClient.update(currentLAState)
                }
                return .none
            }
        }
        .ifLet(\.$destination, action: \.destination) {
          Destination()
        }
    }
}

struct ChickenMapView: View {
    @Bindable var store: StoreOf<ChickenMapFeature>
    @State private var viewport: Viewport = .camera(
        center: CLLocationCoordinate2D(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude),
        zoom: 14
    )
    @State private var mapBearing: Double = 0

    private var chickenSubtitle: String {
        switch store.game.gameMod {
        case .followTheChicken:
            return "Don't be seen !"
        case .stayInTheZone:
            return "Stay in the zone 📍"
        case .mutualTracking:
            return "You can see them 👀"
        }
    }

    private var overlayColor: UIColor {
        store.isOutsideZone ? UIColor.red.withAlphaComponent(0.4) : UIColor.black.withAlphaComponent(0.3)
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
                .background(.thinMaterial)
                .clipShape(Circle())
        }
        .padding(.trailing, 8)
        .padding(.top, 8)
    }

    private var topBar: some View {
        HStack {
            Spacer()
            VStack {
                Text("You are the 🐔")
                Text(chickenSubtitle)
                    .font(.system(size: 14))
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

    private var bottomBar: some View {
        HStack {
            VStack(alignment: .leading) {
                Text("Radius : \(self.store.radius)m")
                    .accessibilityLabel("Radius \(self.store.radius) meters")
                CountdownView(nowDate: self.$store.nowDate, nextUpdateDate: self.$store.nextRadiusUpdate, chickenStartDate: store.game.startDate, hunterStartDate: store.game.hunterStartDate, isChicken: true)
            }
            Spacer()
            Button {
                self.store.send(.beenFoundButtonTapped)
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
            .accessibilityLabel("I have been found")
            .frame(width: 50, height: 40)
        }
        .padding()
        .background(.thinMaterial)
    }

    var body: some View {
        Map(viewport: $viewport) {
            Puck2D(bearing: .heading)

            if let circle = self.store.mapCircle {
                // Inverted zone overlay: grey outside, transparent inside
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

            // Hunter annotations (mutualTracking mode) -- only after hunt starts
            if store.hasHuntStarted {
                ForEvery(self.store.hunterAnnotations) { hunter in
                    MapViewAnnotation(coordinate: hunter.coordinate) {
                        VStack(spacing: 2) {
                            Text(hunter.displayName)
                                .font(.caption2)
                                .fontWeight(.semibold)
                            Image(systemName: "figure.walk")
                                .foregroundStyle(.white)
                                .padding(6)
                                .background(.orange)
                                .clipShape(Circle())
                        }
                    }
                }
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
            compassButton
        }
        .safeAreaInset(edge: .top) { topBar }
        .safeAreaInset(edge: .bottom) { bottomBar }
        .task {
            self.store.send(.gameInitialized)
            self.store.send(.onTask)
        }
        .alert(
            $store.scope(
                state: \.destination?.alert,
                action: \.destination.alert
            )
        )
        .sheet(
            isPresented: Binding(
                get: { store.destination.flatMap { if case .endGameCode = $0 { true } else { nil } } ?? false },
                set: { if !$0 { store.send(.endGameCodeDismissed) } }
            )
        ) {
            NavigationStack {
                EndGameCodeView(foundCode: {
                    if case let .endGameCode(code) = store.destination { return code }
                    return ""
                }())
                    .toolbar {
                        ToolbarItem {
                            Button {
                                self.store.send(.endGameCodeDismissed)
                            } label: {
                                Image(systemName: "xmark")
                                    .foregroundStyle(.white)
                            }
                        }
                    }
            }
        }
        .sheet(isPresented: Binding(
            get: { self.store.showGameInfo },
            set: { _ in self.store.send(.gameInfoDismissed) }
        )) {
            GameInfoSheet(game: self.store.game, onCancelGame: {
                self.store.send(.cancelGameButtonTapped)
            })
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
                    role: .chicken,
                    gameModTitle: store.game.gameMod.title,
                    gameCode: store.game.gameCode,
                    targetDate: store.game.startDate,
                    nowDate: store.nowDate,
                    onCancelGame: { store.send(.cancelGameButtonTapped) }
                )
            }
        }
    }
}

struct GameInfoSheet: View {
    let game: Game
    var onCancelGame: (() -> Void)? = nil
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Game Code") {
                    GameCodeRow(gameCode: game.gameCode)
                }

                Section("Game Mode") {
                    Text(game.gameMod.title)
                }

                Section("Schedule") {
                    HStack {
                        Text("Start")
                        Spacer()
                        Text(game.startDate, style: .time)
                            .foregroundStyle(.secondary)
                    }
                    HStack {
                        Text("End")
                        Spacer()
                        Text(game.endDate, style: .time)
                            .foregroundStyle(.secondary)
                    }
                }

                if let onCancelGame {
                    Section {
                        Button(role: .destructive) {
                            dismiss()
                            onCancelGame()
                        } label: {
                            HStack {
                                Spacer()
                                Text("Cancel game")
                                Spacer()
                            }
                        }
                    }
                }
            }
            .navigationTitle("Game Info")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }
}

#Preview {
    ChickenMapView(store: Store(initialState: ChickenMapFeature.State(game: .mock)) {
        ChickenMapFeature()
    })
}
