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
        @Presents var destination: Destination.State?
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
        case destination(PresentationAction<Destination.Action>)
        case dismissDrinksLottery
        case goToChickenConfig
        case newLocationFetched(CLLocationCoordinate2D)
        case noGameFound
        case onAppear
        case onTask
        case setGameTriggered(to: Game)
        case timerTicked
    }

    @Reducer
    struct Destination {
        enum State {
            case alert(AlertState<Action.Alert>)
            case drinksLottery(DrinksLotteryFeature.State)
        }

        enum Action {
            case alert(Alert)
            case drinksLottery(DrinksLotteryFeature.Action)

            enum Alert {
                case beTheChicken
            }
        }

        var body: some ReducerOf<Self> {
            Scope(state: \.drinksLottery, action: \.drinksLottery) {
                DrinksLotteryFeature()
            }
        }
    }

    @Dependency(\.continuousClock) var clock
    @Dependency(\.apiClient) var apiClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .barButtonTapped:
                state.destination = .drinksLottery(DrinksLotteryFeature.State())
                return .none
            case .binding:
                return .none
            case .destination(.presented(.alert(.beTheChicken))):
                return .run { send in
                    await send(.goToChickenConfig)
                }
            case .destination:
                return .none
            case .goToChickenConfig:
                return .none
            case .dismissDrinksLottery:
                state.destination = nil
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
                        TextState("No game found, please wait on this page or create one and be the chicken. 🐔")
                    } actions: {
                        ButtonState(role: .cancel) {
                            TextState("I'll wait")
                        }
                        ButtonState(action: .beTheChicken) {
                            TextState("Be the 🐔")
                        }
                    }
                )
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
                        await send(.noGameFound)
                    }
                }
            case .onTask:
                return .run { send in
                    for await _ in self.clock.timer(interval: .seconds(1)) {
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

                guard state.radius - game.radiusDeclinePerUpdate > 0
                else {  return .none }

                state.nextRadiusUpdate?.addTimeInterval(TimeInterval(game.radiusIntervalUpdate * 60))
                state.radius = state.radius - game.radiusDeclinePerUpdate
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
                state: \.destination?.drinksLottery,
                action: \.destination.drinksLottery
            )
        ) { store in
            NavigationStack {
                DrinksLotteryView(store: store)
                    .toolbar {
                        ToolbarItem {
                            Button {
                                self.store.send(.dismissDrinksLottery)
                            } label: {
                                Image(systemName: "xmark")
                                    .foregroundStyle(.white)
                            }

                        }
                    }
            }
        }
    }
}

#Preview {
    HunterMapView(store: Store(initialState: HunterMapFeature.State()) {
        HunterMapFeature()
    })
}
