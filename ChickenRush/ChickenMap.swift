//
//  ChickenMap.swift
//  ChickenRush
//
//  Created by Julien Rahier on 16/03/2024.
//

import ComposableArchitecture
import FirebaseFirestore
import MapKit
import SwiftUI

@Reducer
struct ChickenMapFeature {

    @ObservableState
    struct State {
        var game: Game
        var locationManager: LocationManager = LocationManager()
        var nextRadiusUpdate: Date?
        var nowDate: Date = .now
        var radius: Int = 1500
        var timer: Timer?
        var mapCircle: MapCircle?
    }

    enum Action: BindableAction {
        case barButtonTapped
        case binding(BindingAction<State>)
        case chickenLocationFetched(CLLocationCoordinate2D)
        case onAppear
        case onTask
        case setGameTriggered(to: Game)
        case stopGameButtonTapped
        case timerTicked
    }

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.continuousClock) var clock

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .barButtonTapped:
                return .none
            case .binding:
                return .none
            case let .chickenLocationFetched(location):
                let mapCircle = MapCircle(
                    center: location,
                    radius: CLLocationDistance(state.radius)
                )
                state.mapCircle = mapCircle
                return .none
            case .onAppear:
                return .none
            case .onTask:
                return .run { send in
                    for await _ in self.clock.timer(interval: .seconds(1)) {
                        await send(.timerTicked)
                    }
                }
            case let .setGameTriggered(game):
                state.game = game
                state.radius = game.initialRadius
                state.nextRadiusUpdate = game.gameStartDate
                let mapCircle = MapCircle(
                    center: game.initialCoordinates.toCLCoordinates,
                    radius: CLLocationDistance(game.initialRadius)
                )
                state.mapCircle = mapCircle
                return .none
            case .stopGameButtonTapped:
                return .none
            case .timerTicked:
                guard let nextRadiusUpdate = state.nextRadiusUpdate
                else { return .none }
                let game = state.game

                if .now >= nextRadiusUpdate {
                    state.nextRadiusUpdate?.addTimeInterval(TimeInterval(game.radiusIntervalUpdate * 60))
                    state.radius = state.radius - game.radiusDeclinePerUpdate
                    return .run { send in
                        do {
                            if let location = try await apiClient.getLastChickenLocation() {
                                await send(.chickenLocationFetched(location))
                            }
                        } catch {
                            print("Error getting documents: \(error)")
                        }
                    }
                } else {
                    state.nowDate = .now
                }
                return .none
            }
        }
    }
}

struct ChickenMapView: View {
    @Bindable var store: StoreOf<ChickenMapFeature>

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
                    Text("You are the Chicken")
                    Text("Don't be seen !")
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
                    self.store.send(.stopGameButtonTapped)
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
                .frame(width: 45, height: 40)
            }
            .padding()
            .background(.thinMaterial)
        }
        .onAppear {
            self.store.send(.onAppear)
        }
        .task {
            store.send(.onTask)
        }
    }
}

#Preview {
    ChickenMapView(store: Store(initialState: ChickenMapFeature.State(game: .mock)) {
        ChickenMapFeature()
    })
}
