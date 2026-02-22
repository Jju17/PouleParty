//
//  ChickenMap.swift
//  PouleParty
//
//  Created by Julien Rahier on 16/03/2024.
//

import ComposableArchitecture
import MapKit
import SwiftUI

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

        var hasGameStarted: Bool { .now >= game.startDate }
        var hasHuntStarted: Bool { .now >= game.hunterStartDate }
    }

    enum Action: BindableAction {
        case barButtonTapped
        case binding(BindingAction<State>)
        case destination(PresentationAction<Destination.Action>)
        case dismissEndGameCode
        case dismissWinnerNotification
        case cancelGameButtonTapped
        case beenFoundButtonTapped
        case gameUpdated(Game)
        case goToMenu
        case hunterLocationsUpdated([HunterLocation])
        case infoButtonTapped
        case dismissGameInfo
        case newLocationFetched(CLLocationCoordinate2D)
        case onTask
        case dismissCountdown
        case setGameTriggered
        case timerTicked
    }

    @Reducer
    struct Destination {
        @ObservableState
        enum State: Equatable {
            case alert(AlertState<Action.Alert>)
            case endGameCode(EndGameCodeFeature.State)
        }

        enum Action {
            case alert(Alert)
            case endGameCode(EndGameCodeFeature.Action)

            enum Alert: Equatable {
                case cancelGame
                case gameOver
                case noGameFound
            }
        }

        var body: some ReducerOf<Self> {
            Scope(state: \.endGameCode, action: \.endGameCode) {
                EndGameCodeFeature()
            }
        }
    }

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.continuousClock) var clock
    @Dependency(\.locationClient) var locationClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .barButtonTapped:
                return .none
            case .binding:
                return .none
            case .destination(.presented(.alert(.cancelGame))):
                locationClient.stopTracking()
                return .run { send in
                    await send(.goToMenu)
                }
            case .destination(.presented(.alert(.gameOver))):
                return .run { send in
                    await send(.goToMenu)
                }
            case .destination:
                return .none
            case .dismissCountdown:
                state.countdownNumber = nil
                state.countdownText = nil
                return .none
            case .dismissEndGameCode:
                state.destination = nil
                return .none
            case .dismissWinnerNotification:
                state.winnerNotification = nil
                return .none
            case let .gameUpdated(game):
                state.game = game
                let previousCount = state.previousWinnersCount
                if game.winners.count > previousCount {
                    let newWinners = Array(game.winners.suffix(from: previousCount))
                    if let latest = newWinners.last {
                        state.winnerNotification = "\(latest.hunterName) a trouvé la poule !"
                    }
                    state.previousWinnersCount = game.winners.count
                    if state.winnerNotification != nil {
                        return .run { send in
                            try await clock.sleep(for: .seconds(4))
                            await send(.dismissWinnerNotification)
                        }
                    }
                }
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
                        TextState("Are you sure you want to cancel and finish the game now ?")
                    }
                )
                return .none
            case .beenFoundButtonTapped:
                state.destination = .endGameCode(EndGameCodeFeature.State(foundCode: state.game.foundCode))
                return .none
            case .goToMenu:
                return .none
            case .infoButtonTapped:
                state.showGameInfo = true
                return .none
            case .dismissGameInfo:
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
                state.mapCircle = CircleOverlay(
                    center: location,
                    radius: CLLocationDistance(state.radius)
                )
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
                // stayInTheZone: no position sharing, zone is fixed
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
                                try apiClient.setChickenLocation(gameId, currentLocation)
                            }
                            var lastWrite = Date.now
                            for await coordinate in locationClient.startTracking() {
                                await send(.newLocationFetched(coordinate))
                                if Date.now.timeIntervalSince(lastWrite) >= 5 {
                                    try apiClient.setChickenLocation(gameId, coordinate)
                                    lastWrite = .now
                                }
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
            case .setGameTriggered:
                let (lastUpdate, lastRadius) = state.game.findLastUpdate()

                state.radius = lastRadius
                state.nextRadiusUpdate = lastUpdate
                state.mapCircle = CircleOverlay(
                    center: state.game.initialCoordinates.toCLCoordinates,
                    radius: CLLocationDistance(state.radius)
                )
                return .none
            case .timerTicked:
                state.nowDate = .now

                // Pre-game countdown: 3, 2, 1, RUN!
                let timeToStart = state.game.startDate.timeIntervalSinceNow
                if timeToStart > 0 && timeToStart <= 3 {
                    let number = Int(ceil(timeToStart))
                    if state.countdownNumber != number {
                        state.countdownNumber = number
                        state.countdownText = nil
                    }
                } else if timeToStart <= 0 && timeToStart > -1 && state.countdownText == nil {
                    state.countdownNumber = nil
                    state.countdownText = "RUN! 🐔"
                    return .run { send in
                        try await clock.sleep(for: .seconds(1.5))
                        await send(.dismissCountdown)
                    }
                }

                // Notification when hunters start (only if head start > 0)
                if state.game.chickenHeadStartMinutes > 0 {
                    let timeToHuntStart = state.game.hunterStartDate.timeIntervalSinceNow
                    if timeToHuntStart <= 0 && timeToHuntStart > -1 && state.countdownText == nil && state.countdownNumber == nil {
                        state.countdownText = "🔍 Hunters incoming!"
                        return .run { send in
                            try await clock.sleep(for: .seconds(1.5))
                            await send(.dismissCountdown)
                        }
                    }
                }

                guard state.destination == nil else { return .none }

                // Don't process game-over or radius updates before hunt starts
                guard state.hasHuntStarted else { return .none }

                if .now >= state.game.endDate {
                    locationClient.stopTracking()
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
                        try? await apiClient.updateGameStatus(gameId, .done)
                    }
                }

                guard let nextRadiusUpdate = state.nextRadiusUpdate,
                      .now >= nextRadiusUpdate
                else { return .none }

                let game = state.game
                let newRadius = Int(state.radius) - Int(game.radiusDeclinePerUpdate)

                guard newRadius > 0 else {
                    locationClient.stopTracking()
                    let gameId = state.game.id
                    state.destination = .alert(
                        AlertState {
                            TextState("Game Over")
                        } actions: {
                            ButtonState(action: .gameOver) {
                                TextState("OK")
                            }
                        } message: {
                            TextState("The zone has collapsed!")
                        }
                    )
                    return .run { _ in
                        try? await apiClient.updateGameStatus(gameId, .done)
                    }
                }

                state.nextRadiusUpdate?.addTimeInterval(TimeInterval(game.radiusIntervalUpdate * 60))
                state.radius = newRadius

                if game.gameMod == .stayInTheZone {
                    state.mapCircle = CircleOverlay(
                        center: game.initialCoordinates.toCLCoordinates,
                        radius: CLLocationDistance(state.radius)
                    )
                } else if let currentCircle = state.mapCircle {
                    state.mapCircle = CircleOverlay(
                        center: currentCircle.center,
                        radius: CLLocationDistance(state.radius)
                    )
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

    var body: some View {
        Map {
            if let circle = self.store.mapCircle {
                MapCircle(center: circle.center, radius: circle.radius)
                    .foregroundStyle(.green.opacity(0.5))
                    .mapOverlayLevel(level: .aboveRoads)
            }
            UserAnnotation()
            if store.hasHuntStarted {
                ForEach(self.store.hunterAnnotations) { hunter in
                    hunterAnnotationView(for: hunter)
                }
            }
        }
        .mapControls {
            MapUserLocationButton()
            MapCompass()
            MapScaleView()
        }
        .safeAreaInset(edge: .top) {
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
                .padding(.trailing, 4)
            }
            .padding()
            .background(.thinMaterial)
        }
        .safeAreaInset(edge: .bottom) {
            HStack {
                VStack(alignment: .leading) {
                    Text("Radius : \(self.store.radius)m")
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
                .frame(width: 50, height: 40)
            }
            .padding()
            .background(.thinMaterial)
        }
        .task {
            self.store.send(.setGameTriggered)
            self.store.send(.onTask)
        }
        .alert(
            $store.scope(
                state: \.destination?.alert,
                action: \.destination.alert
            )
        )
        .sheet(
            item: $store.scope(
                state: \.destination?.endGameCode,
                action: \.destination.endGameCode
            )
        ) { store in
            NavigationStack {
                EndGameCodeView(store: store)
                    .toolbar {
                        ToolbarItem {
                            Button {
                                self.store.send(.dismissEndGameCode)
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
            set: { _ in self.store.send(.dismissGameInfo) }
        )) {
            GameInfoSheet(game: self.store.game, onCancelGame: {
                self.store.send(.cancelGameButtonTapped)
            })
        }
        .overlay(alignment: .top) {
            if let notification = store.winnerNotification {
                Text(notification)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.green.opacity(0.9))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .padding(.top, 100)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .animation(.easeInOut, value: store.winnerNotification)
            }
        }
        .overlay {
            GameStartCountdownOverlay(
                countdownNumber: store.countdownNumber,
                countdownText: store.countdownText
            )
        }
    }

    private func hunterAnnotationView(for hunter: HunterAnnotation) -> some MapContent {
        Annotation(hunter.displayName, coordinate: hunter.coordinate) {
            Image(systemName: "figure.walk")
                .foregroundStyle(.white)
                .padding(6)
                .background(.orange)
                .clipShape(Circle())
        }
    }
}

struct GameInfoSheet: View {
    let game: Game
    var onCancelGame: (() -> Void)? = nil
    @State private var codeCopied = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Game Code") {
                    HStack {
                        Spacer()
                        Text(game.gameCode)
                            .font(.gameboy(size: 24))
                        Spacer()
                        Button {
                            UIPasteboard.general.string = game.gameCode
                            withAnimation {
                                codeCopied = true
                            }
                            DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
                                withAnimation {
                                    codeCopied = false
                                }
                            }
                        } label: {
                            Image(systemName: codeCopied ? "checkmark" : "doc.on.doc")
                                .foregroundStyle(codeCopied ? .green : .gray)
                                .contentTransition(.symbolEffect(.replace))
                        }
                        .buttonStyle(.plain)
                    }
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
