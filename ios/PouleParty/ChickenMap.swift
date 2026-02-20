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
        var radius: Int = 1500
        var mapCircle: CircleOverlay?
    }

    enum Action: BindableAction {
        case barButtonTapped
        case binding(BindingAction<State>)
        case destination(PresentationAction<Destination.Action>)
        case dismissEndGameCode
        case cancelGameButtonTapped
        case beenFoundButtonTapped
        case goToMenu
        case hunterLocationsUpdated([HunterLocation])
        case newLocationFetched(CLLocationCoordinate2D)
        case onTask
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
            case .destination:
                return .none
            case .dismissEndGameCode:
                state.destination = nil
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
                state.destination = .endGameCode(EndGameCodeFeature.State())
                return .none
            case .goToMenu:
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

                var effects: [Effect<Action>] = [
                    .run { send in
                        for await _ in self.clock.timer(interval: .seconds(1)) {
                            await send(.timerTicked)
                        }
                    }
                ]

                // followTheChicken & mutualTracking: chicken sends position to hunters
                // stayInTheZone: no position sharing, zone is fixed
                if gameMod != .stayInTheZone {
                    effects.append(
                        .run { send in
                            var lastWrite = Date.distantPast
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
                if gameMod == .mutualTracking {
                    effects.append(
                        .run { send in
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
                    radius: CLLocationDistance(state.game.initialRadius)
                )
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
            ForEach(self.store.hunterAnnotations) { hunter in
                hunterAnnotationView(for: hunter)
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

#Preview {
    ChickenMapView(store: Store(initialState: ChickenMapFeature.State(game: .mock)) {
        ChickenMapFeature()
    })
}
