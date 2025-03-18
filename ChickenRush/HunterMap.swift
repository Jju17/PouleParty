//
//  HunterMap.swift
//  ChickenRush
//
//  Created by Julien Rahier on 14/03/2024.
//

import ComposableArchitecture
import MapKit
import SwiftUI

@Reducer
struct HunterMapFeature {

    @ObservableState
    struct State {
        @Presents var destination: Destination.State?
        var game: Game?
        var nextRadiusUpdate: Date?
        var nowDate: Date = .now
        var radius: Int = 1500
        var timer: Timer?
        var mapCircle: MapCircle?
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case destination(PresentationAction<Destination.Action>)
        case newLocationFetched(CLLocationCoordinate2D)
        case noGameFound
        case onTask
        case setGameTriggered(to: Game)
        case timerTicked
    }

    @Reducer
    struct Destination {
        enum State {
            case alert(AlertState<Action.Alert>)
        }

        enum Action {
            case alert(Alert)

            enum Alert {
                case beTheChicken
            }
        }
    }

    @Dependency(\.continuousClock) var clock
    @Dependency(\.apiClient) var apiClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case .destination:
                return .none
            case let .newLocationFetched(location):
                let mapCircle = MapCircle(
                    center: location,
                    radius: CLLocationDistance(state.radius)
                )
                state.mapCircle = mapCircle
                return .none
            case .noGameFound:
                state.destination = .alert(
                    AlertState {
                        TextState("No game found")
                    } actions: {
                        ButtonState(role: .cancel) {
                            TextState("I'll wait")
                        }
                    } message: {
                        TextState("Please wait on this page until the 🐔 create a new game.")
                    }
                )
                return .none
            case .onTask:
                return .run { send in
                    if let game = await apiClient.getConfig() {
                        await send(.setGameTriggered(to: game))
                    } else {
                        await send(.noGameFound)
                    }

                    for await _ in self.clock.timer(interval: .seconds(5)) {
                        print("onTask")
                        await send(.timerTicked)
                    }
                }
            case let .setGameTriggered(game):
                let (lastUpdate, lastRadius) = game.findLastUpdate()

                state.game = game
                state.radius = lastRadius
                state.nextRadiusUpdate = lastUpdate
                let mapCircle = MapCircle(
                    center: game.initialCoordinates.toCLCoordinates,
                    radius: CLLocationDistance(game.initialRadius)
                )
                state.mapCircle = mapCircle
                return .none
            case .timerTicked:
                guard let nextRadiusUpdate = state.nextRadiusUpdate,
                      let game = state.game,
                      .now >= nextRadiusUpdate
                else {
                    state.nowDate = .now
                    return .none
                }

                guard Int(state.radius) - Int(game.radiusDeclinePerUpdate) > 0
                else {  return .none }

                state.nextRadiusUpdate?.addTimeInterval(TimeInterval(game.radiusIntervalUpdate * 60))
                state.radius = Int(state.radius) - Int(game.radiusDeclinePerUpdate)
                return .run { send in
                    if let location = try await apiClient.getLastChickenLocation() {
                        await send(.newLocationFetched(location))
                    }
                }
            }
        }
        .ifLet(\.$destination, action: \.destination) {
          Destination()
        }
    }
}

struct HunterMapView: View {
    @Bindable var store: StoreOf<HunterMapFeature>

    var body: some View {
        Map {
            self.store.mapCircle
                .foregroundStyle(.green.opacity(0.5))
                .mapOverlayLevel(level: .aboveRoads)
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
                    Text("Catch the 🐔 !")
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
    HunterMapView(store: Store(initialState: HunterMapFeature.State()) {
        HunterMapFeature()
    })
}
