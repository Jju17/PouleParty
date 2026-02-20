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
        case destination(PresentationAction<Destination.Action>)
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
                case ok
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
            case .destination:
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
                guard let nextRadiusUpdate = state.nextRadiusUpdate,
                      .now >= nextRadiusUpdate
                else {
                    state.nowDate = .now
                    return .none
                }

                let game = state.game
                guard Int(state.radius) - Int(game.radiusDeclinePerUpdate) > 0
                else { return .none }

                state.nextRadiusUpdate?.addTimeInterval(TimeInterval(game.radiusIntervalUpdate * 60))
                state.radius = Int(state.radius) - Int(game.radiusDeclinePerUpdate)

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
