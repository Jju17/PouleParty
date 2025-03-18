//
//  ChickenMap.swift
//  ChickenRush
//
//  Created by Julien Rahier on 16/03/2024.
//

import ComposableArchitecture
import MapKit
import SwiftUI

@Reducer
struct ChickenMapFeature {

    @ObservableState
    struct State {
        @Presents var destination: Destination.State?
        var game: Game
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
        case dismissEndGameCode
        case cancelGameButtonTapped
        case beenFoundButtonTapped
        case goToMenu
        case newLocationFetched(CLLocationCoordinate2D)
        case onTask
        case setGameTriggered
        case timerTicked
    }

    @Reducer
    struct Destination {
        enum State {
            case alert(AlertState<Action.Alert>)
            case endGameCode(EndGameCodeFeature.State)
        }

        enum Action {
            case alert(Alert)
            case endGameCode(EndGameCodeFeature.Action)

            enum Alert {
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

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .barButtonTapped:
                return .none
            case .binding:
                return .none
            case .destination(.presented(.alert(.cancelGame))):
                LocationManager.shared.stopUpdatingLocation()
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
            case let .newLocationFetched(location):
                let mapCircle = MapCircle(
                    center: location,
                    radius: CLLocationDistance(state.radius)
                )
                state.mapCircle = mapCircle
                return .none
            case .onTask:
                LocationManager.shared.askForLocationServicesAuthorization()
                LocationManager.shared.updateLocation(every: 10)
                return .run { send in
                    for await _ in self.clock.timer(interval: .seconds(1)) {
                        await send(.timerTicked)
                    }
                }
            case .setGameTriggered:
                let (lastUpdate, lastRadius) = state.game.findLastUpdate()

                state.radius = lastRadius
                state.nextRadiusUpdate = lastUpdate
                let mapCircle = MapCircle(
                    center: state.game.initialCoordinates.toCLCoordinates,
                    radius: CLLocationDistance(state.game.initialRadius)
                )
                state.mapCircle = mapCircle
                return .none
            case .timerTicked:
                guard let nextRadiusUpdate = state.nextRadiusUpdate,
                      .now >= nextRadiusUpdate
                else {
                    state.nowDate = .now
                    return .none
                }
                let game = state.game
                state.nextRadiusUpdate?.addTimeInterval(TimeInterval(game.radiusIntervalUpdate * 60))
                state.radius = Int(state.radius) - Int(game.radiusDeclinePerUpdate)

                let location = LocationManager.shared

//                (.newLocationFetched(location))
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
                    Text("You are the 🐔")
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
        .onAppear {
            self.store.send(.setGameTriggered)
            self.store.send(.onTask)
        }
        .task {

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
}

#Preview {
    ChickenMapView(store: Store(initialState: ChickenMapFeature.State(game: .mock)) {
        ChickenMapFeature()
    })
}


class LocationDelegate: NSObject, CLLocationManagerDelegate {
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if let location = locations.first {
            print("Found location: \(location)")
            //do something with the location
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("Failed to find user's location: \(error.localizedDescription)")
    }
}
