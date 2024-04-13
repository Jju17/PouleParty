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
            center: .init(latitude: 50.4233, longitude: 4.5343),
            span: MKCoordinateSpan(latitudeDelta: 0.2, longitudeDelta: 0.2)
        ))
        @Shared var game: Game
        var marker: Marker<Text>?
        var mapCircle: MapCircle?

        init(cameraPosition: MapCameraPosition, game: Game, marker: Marker<Text>? = nil, mapCircle: MapCircle? = nil) {
            self.cameraPosition = cameraPosition
            self.game = game
            self.marker = marker
            self.mapCircle = mapCircle
        }
    }
    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case onMapCameraChange(CLLocationCoordinate2D)
        case onUpdatedRadius
    }

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case let .onMapCameraChange(centerCoordinates):
                state.game.initialLocation = centerCoordinates
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
            initialState: ChickenMapConfigFeature.State(cameraPosition: MapCameraPosition.region(MKCoordinateRegion(
                center: .init(latitude: 50.4233, longitude: 4.5343),
                span: MKCoordinateSpan(latitudeDelta: 0.2, longitudeDelta: 0.2)
            )), game: Shared(Game.mock)),
            reducer: { ChickenMapConfigFeature() }
        )
    )
}
