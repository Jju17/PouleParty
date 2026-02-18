//
//  ChickenMapConfig.swift
//  ChickenRush
//
//  Created by Julien Rahier on 04/04/2024.
//

import ComposableArchitecture
import CoreLocation
import MapKit
import SwiftUI

@Reducer
struct ChickenMapConfigFeature {

    @ObservableState
    struct State {
        var cameraPosition: MapCameraPosition = MapCameraPosition.region(MKCoordinateRegion(
            center: .init(latitude: 50.8503, longitude: 4.3517),
            span: MKCoordinateSpan(latitudeDelta: 0.01, longitudeDelta: 0.01)
        ))
        @Shared var game: Game
        var marker: Marker<Text>?
        var mapCircle: MapCircle?
    }
    private static let brusselsCoordinate = CLLocationCoordinate2D(latitude: 50.8503, longitude: 4.3517)

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case initialLocationReceived(CLLocationCoordinate2D)
        case onMapCameraChange(CLLocationCoordinate2D)
        case onTask
        case onUpdatedRadius
    }

    @Dependency(\.locationClient) var locationClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case let .initialLocationReceived(coordinate):
                state.cameraPosition = .region(MKCoordinateRegion(
                    center: coordinate,
                    span: MKCoordinateSpan(latitudeDelta: 0.01, longitudeDelta: 0.01)
                ))
                state.$game.withLock { $0.initialLocation = coordinate }
                self.updateMapComponents(state: &state)
                return .none
            case .onTask:
                return .run { send in
                    var firstLocation: CLLocationCoordinate2D = Self.brusselsCoordinate
                    for await coordinate in locationClient.startTracking() {
                        firstLocation = coordinate
                        break
                    }
                    locationClient.stopTracking()
                    await send(.initialLocationReceived(firstLocation))
                }
            case let .onMapCameraChange(centerCoordinates):
                state.$game.withLock { $0.initialLocation = centerCoordinates }
                self.updateMapComponents(state: &state)
                return .none
            case .onUpdatedRadius:
                self.updateMapComponents(state: &state)
                return .none
            }
        }
    }

    private func updateMapComponents(state: inout ChickenMapConfigFeature.State) {
        state.marker = Marker("", coordinate: state.game.initialLocation)
        state.mapCircle = MapCircle(
            center: state.game.initialLocation,
            radius: CLLocationDistance(state.game.initialRadius)
        )
    }
}


struct ChickenMapConfigView: View {
    @Bindable var store: StoreOf<ChickenMapConfigFeature>

    var body: some View {
        GeometryReader { proxy in
            Map(position: $store.cameraPosition) {
                self.store.marker
                self.store.mapCircle
                    .foregroundStyle(.green.opacity(0.5))
                    .mapOverlayLevel(level: .aboveRoads)
            }
            .onMapCameraChange(frequency: .continuous) { mapCameraUpdateContext in
                store.send(.onMapCameraChange(mapCameraUpdateContext.camera.centerCoordinate))
            }
            .mapControls {
                MapUserLocationButton()
                MapCompass()
                MapScaleView()
            }
            .task {
                store.send(.onTask)
            }
            .safeAreaInset(edge: .bottom) {
                VStack(alignment: .leading) {
                    Text("Radius: \(Int(self.store.game.initialRadius))")
                    Slider(value: $store.game.initialRadius, in: 500...2000, step: 100)
                    .onChange(of: self.store.game.initialRadius) { _, _ in
                        store.send(.onUpdatedRadius)
                    }
                }
                .padding()
                .background(.thinMaterial)
            }
        }
    }
}

#Preview {
    ChickenMapConfigView(
        store: Store(
            initialState: ChickenMapConfigFeature.State(
                cameraPosition: MapCameraPosition.region(MKCoordinateRegion(
                    center: .init(latitude: 50.8503, longitude: 4.3517),
                    span: MKCoordinateSpan(latitudeDelta: 0.01, longitudeDelta: 0.01)
                )),
                game: Shared(value: Game.mock)
            ),
            reducer: { ChickenMapConfigFeature() }
        )
    )
}
