//
//  ChickenMapConfig.swift
//  PouleParty
//
//  Created by Julien Rahier on 04/04/2024.
//

import ComposableArchitecture
import CoreLocation
import MapboxMaps
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
    private static let defaultCoordinate = CLLocationCoordinate2D(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude)

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
    @State private var viewport: Viewport = .camera(
        center: CLLocationCoordinate2D(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude),
        zoom: 14
    )

    var body: some View {
        GeometryReader { proxy in
            Map(viewport: $viewport) {
                Puck2D(bearing: .heading)

                if let circle = self.store.mapCircle {
                    // Circle border
                    let circlePolygon = Polygon(center: circle.center, radius: circle.radius, vertices: 72)
                    PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
                        .lineColor(StyleColor(UIColor.green.withAlphaComponent(0.7)))
                        .lineWidth(2)
                }

                if let marker = self.store.marker {
                    MapViewAnnotation(coordinate: marker.coordinate) {
                        Image(systemName: "mappin.circle.fill")
                            .font(.title)
                            .foregroundStyle(.red)
                    }
                }
            }
            .onCameraChanged { context in
                store.send(.onMapCameraChange(context.cameraState.center))
            }
            .ignoresSafeArea()
            .onChange(of: store.cameraRegion) { _, newRegion in
                withViewportAnimation(.default(maxDuration: 1)) {
                    viewport = .camera(center: newRegion.center, zoom: 14)
                }
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
            .overlay(alignment: .topTrailing) {
                Button {
                    withViewportAnimation(.default(maxDuration: 1)) {
                        viewport = .followPuck(zoom: 14, bearing: .constant(0))
                    }
                } label: {
                    Image(systemName: "location.fill")
                        .padding(10)
                        .background(.thinMaterial)
                        .clipShape(Circle())
                }
                .padding(.trailing, 8)
                .padding(.top, 8)
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
