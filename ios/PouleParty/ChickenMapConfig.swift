//
//  ChickenMapConfig.swift
//  PouleParty
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
    struct State: Equatable {
        var cameraRegion: CameraRegion = .brussels
        @Shared var game: Game
        var marker: MarkerOverlay?
        var mapCircle: CircleOverlay?
    }
    private static let defaultCoordinate = CLLocationCoordinate2D(latitude: 50.8503, longitude: 4.3517)

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
                state.cameraRegion = CameraRegion(
                    center: coordinate,
                    latitudeDelta: 0.01,
                    longitudeDelta: 0.01
                )
                state.$game.withLock { $0.initialLocation = coordinate }
                self.updateMapComponents(state: &state)
                return .none
            case .onTask:
                let gameLocation = state.game.initialLocation
                return .run { send in
                    var firstLocation = gameLocation
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
        state.marker = MarkerOverlay(title: "", coordinate: state.game.initialLocation)
        state.mapCircle = CircleOverlay(
            center: state.game.initialLocation,
            radius: CLLocationDistance(state.game.initialRadius)
        )
    }
}


struct ChickenMapConfigView: View {
    @Bindable var store: StoreOf<ChickenMapConfigFeature>

    var body: some View {
        GeometryReader { proxy in
            Map(position: Binding(
                get: { store.cameraRegion.toMapCameraPosition },
                set: { _ in }
            )) {
                if let marker = self.store.marker {
                    Marker(marker.title, coordinate: marker.coordinate)
                }
                if let circle = self.store.mapCircle {
                    MapCircle(center: circle.center, radius: circle.radius)
                        .foregroundStyle(.green.opacity(0.5))
                        .mapOverlayLevel(level: .aboveRoads)
                }
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
                cameraRegion: .brussels,
                game: Shared(value: Game.mock)
            ),
            reducer: { ChickenMapConfigFeature() }
        )
    )
}
