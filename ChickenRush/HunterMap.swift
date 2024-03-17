//
//  HunterMap.swift
//  ChickenRush
//
//  Created by Julien Rahier on 14/03/2024.
//

import ComposableArchitecture
import FirebaseFirestore
import MapKit
import SwiftUI

@Reducer
struct HunterMapFeature {

    @ObservableState
    struct State {
        var locationManager = CLLocationManager()
        var game: Game?
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
        case timerTicked
    }

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
                state.locationManager.requestAlwaysAuthorization()
                return .run { send in
                    do {
                        let game = try await Firestore.firestore()
                            .collection("games")
                            .document("sRhFcVTSpPvWOQe947cz")
                            .getDocument(as: Game.self)

                        await send(.setGameTriggered(to: game))
                    } catch {
                        print("Error getting documents: \(error)")
                    }
                }
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
            case .timerTicked:
                guard let nextRadiusUpdate = state.nextRadiusUpdate,
                      let game = state.game
                else { return .none }

                if .now >= nextRadiusUpdate {
                    state.nextRadiusUpdate?.addTimeInterval(TimeInterval(game.radiusIntervalUpdate * 60))
                    state.radius = state.radius - game.radiusDeclinePerUpdate
                    return .run { send in
                        do {
                            let snapshot = try await Firestore.firestore()
                                .collection("chickenLocations")
                                .order(by: "timestamp", descending: true)
                                .limit(to: 1)
                                .getDocuments()

                            let chickenLocation = try snapshot.documents.first?.data(as: ChickenLocation.self)
                            if let lat = chickenLocation?.location.latitude,
                               let long = chickenLocation?.location.longitude {
                                let location = CLLocationCoordinate2D(latitude: lat, longitude: long)
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
                    Text("Catch the Chicken !")
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
                    self.store.send(.barButtonTapped)
                } label: {
                    ZStack {
                        RoundedRectangle(cornerRadius: 5)
                            .fill(.blue)
                        Image("Bar")
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
    HunterMapView(store: Store(initialState: HunterMapFeature.State()) {
        HunterMapFeature()
    })
}
