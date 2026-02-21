//
//  HunterMap.swift
//  PouleParty
//
//  Created by Julien Rahier on 14/03/2024.
//

import ComposableArchitecture
import MapKit
import SwiftUI

@Reducer
struct HunterMapFeature {

    @ObservableState
    struct State: Equatable {
        @Presents var destination: Destination.State?
        var game: Game
        var hunterId: String = UUID().uuidString
        var hunterName: String = "Hunter"
        var enteredCode: String = ""
        var isEnteringFoundCode: Bool = false
        var nextRadiusUpdate: Date?
        var nowDate: Date = .now
        var previousWinnersCount: Int = 0
        var radius: Int = 1500
        var mapCircle: CircleOverlay?
        var winnerNotification: String? = nil
        var countdownNumber: Int? = nil
        var countdownText: String? = nil

        var hasGameStarted: Bool { .now >= game.hunterStartDate }
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case cancelGameButtonTapped
        case destination(PresentationAction<Destination.Action>)
        case dismissCountdown
        case dismissWinnerNotification
        case foundButtonTapped
        case goToMenu
        case goToVictory
        case newLocationFetched(CLLocationCoordinate2D)
        case onTask
        case setGameTriggered(to: Game)
        case submitFoundCode
        case timerTicked
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
    @Dependency(\.continuousClock) var clock
    @Dependency(\.locationClient) var locationClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
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
            case .dismissWinnerNotification:
                state.winnerNotification = nil
                return .none
            case .foundButtonTapped:
                state.isEnteringFoundCode = true
                return .none
            case .submitFoundCode:
                let code = state.enteredCode.trimmingCharacters(in: .whitespacesAndNewlines)
                state.enteredCode = ""
                state.isEnteringFoundCode = false

                guard code == state.game.foundCode else {
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
                    timestamp: .init(date: .now)
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
            case let .newLocationFetched(location):
                state.mapCircle = CircleOverlay(
                    center: location,
                    radius: CLLocationDistance(state.radius)
                )
                return .none
            case .onTask:
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

                // mutualTracking: hunter sends its own position
                // Gated behind hunterStartDate
                if gameMod == .mutualTracking {
                    effects.append(
                        .run { _ in
                            let delay = hunterStartDate.timeIntervalSinceNow
                            if delay > 0 {
                                try await clock.sleep(for: .seconds(delay))
                            }
                            // Send current location immediately on connect
                            if let currentLocation = locationClient.lastLocation() {
                                try apiClient.setHunterLocation(gameId, hunterId, currentLocation)
                            }
                            var lastWrite = Date.now
                            for await coordinate in locationClient.startTracking() {
                                if Date.now.timeIntervalSince(lastWrite) >= 5 {
                                    try apiClient.setHunterLocation(gameId, hunterId, coordinate)
                                    lastWrite = .now
                                }
                            }
                        }
                    )
                }

                return .merge(effects)
            case let .setGameTriggered(game):
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
                let previousCount = state.previousWinnersCount
                if game.winners.count > previousCount {
                    let newWinners = Array(game.winners.suffix(from: previousCount))
                    if let latest = newWinners.last, latest.hunterId != state.hunterId {
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
            case .timerTicked:
                state.nowDate = .now

                // Phase 1: Chicken departure countdown (only if head start > 0)
                if state.game.chickenHeadStartMinutes > 0 {
                    let timeToChickenStart = state.game.startDate.timeIntervalSinceNow
                    if timeToChickenStart > 0 && timeToChickenStart <= 3 {
                        let number = Int(ceil(timeToChickenStart))
                        if state.countdownNumber != number {
                            state.countdownNumber = number
                            state.countdownText = nil
                        }
                    } else if timeToChickenStart <= 0 && timeToChickenStart > -1 && state.countdownText == nil {
                        state.countdownNumber = nil
                        state.countdownText = "🐔 is hiding!"
                        return .run { send in
                            try await clock.sleep(for: .seconds(1.5))
                            await send(.dismissCountdown)
                        }
                    }
                }

                // Phase 2: Hunt start countdown: 3, 2, 1, LET'S HUNT!
                let timeToHuntStart = state.game.hunterStartDate.timeIntervalSinceNow
                if timeToHuntStart > 0 && timeToHuntStart <= 3 {
                    let number = Int(ceil(timeToHuntStart))
                    if state.countdownNumber != number {
                        state.countdownNumber = number
                        state.countdownText = nil
                    }
                } else if timeToHuntStart <= 0 && timeToHuntStart > -1 && state.countdownText == nil {
                    state.countdownNumber = nil
                    state.countdownText = "LET'S HUNT! 🔍"
                    return .run { send in
                        try await clock.sleep(for: .seconds(1.5))
                        await send(.dismissCountdown)
                    }
                }

                guard state.destination == nil else { return .none }

                // Don't process game-over or radius updates before game starts
                guard state.hasGameStarted else { return .none }

                if .now >= state.game.endDate {
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

                guard let nextRadiusUpdate = state.nextRadiusUpdate,
                      .now >= nextRadiusUpdate
                else { return .none }

                let game = state.game
                let newRadius = Int(state.radius) - Int(game.radiusDeclinePerUpdate)

                guard newRadius > 0 else {
                    locationClient.stopTracking()
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
                    return .none
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

struct HunterMapView: View {
    @Bindable var store: StoreOf<HunterMapFeature>

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

    var body: some View {
        Map {
            if store.hasGameStarted, let circle = self.store.mapCircle {
                MapCircle(center: circle.center, radius: circle.radius)
                    .foregroundStyle(.green.opacity(0.5))
                    .mapOverlayLevel(level: .aboveRoads)
            }
            UserAnnotation()
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
                    Text("You are the Hunter")
                    Text(hunterSubtitle)
                        .font(.system(size: 12))
                }
                Spacer()
            }
            .padding()
            .background(.thinMaterial)
        }
        .safeAreaInset(edge: .bottom) {
            HStack {
                VStack(alignment: .leading) {
                    Text("Radius : \(self.store.radius)m")
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
}

#Preview {
    HunterMapView(store: Store(initialState: HunterMapFeature.State(game: .mock)) {
        HunterMapFeature()
    })
}
