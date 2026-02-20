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
        var nextRadiusUpdate: Date?
        var nowDate: Date = .now
        var radius: Int = 1500
        var mapCircle: CircleOverlay?
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case cancelGameButtonTapped
        case destination(PresentationAction<Destination.Action>)
        case goToMenu
        case newLocationFetched(CLLocationCoordinate2D)
        case onTask
        case setGameTriggered(to: Game)
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
                        TextState("Leave game")
                    } actions: {
                        ButtonState(role: .cancel) {
                            TextState("Never mind")
                        }
                        ButtonState(role: .destructive, action: .leaveGame) {
                            TextState("Leave game")
                        }
                    } message: {
                        TextState("Are you sure you want to leave the game?")
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
            case .goToMenu:
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
                let (lastUpdate, lastRadius) = state.game.findLastUpdate()
                state.radius = lastRadius
                state.nextRadiusUpdate = lastUpdate
                state.mapCircle = CircleOverlay(
                    center: state.game.initialCoordinates.toCLCoordinates,
                    radius: CLLocationDistance(state.radius)
                )

                var effects: [Effect<Action>] = [
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
                if gameMod != .stayInTheZone {
                    effects.append(
                        .run { send in
                            for await location in apiClient.chickenLocationStream(gameId) {
                                if let location {
                                    await send(.newLocationFetched(location))
                                }
                            }
                        }
                    )
                }

                // mutualTracking: hunter sends its own position
                if gameMod == .mutualTracking {
                    effects.append(
                        .run { _ in
                            var lastWrite = Date.distantPast
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
                return .none
            case .timerTicked:
                state.nowDate = .now

                guard state.destination == nil else { return .none }

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
            if let circle = self.store.mapCircle {
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
                    CountdownView(nowDate: self.$store.nowDate, nextUpdateDate: self.$store.nextRadiusUpdate)
                }
                Spacer()
                Button {
                    self.store.send(.cancelGameButtonTapped)
                } label: {
                    ZStack {
                        RoundedRectangle(cornerRadius: 5)
                            .fill(.red)
                        Image(systemName: "stop.circle.fill")
                            .resizable()
                            .scaledToFit()
                            .foregroundStyle(.white)
                            .frame(width: 25, height: 25)
                    }
                }
                .frame(width: 50, height: 40)
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
    }
}

#Preview {
    HunterMapView(store: Store(initialState: HunterMapFeature.State(game: .mock)) {
        HunterMapFeature()
    })
}
