//
//  ChickenMapConfig.swift
//  PouleParty
//
//  Created by Julien Rahier on 04/04/2024.
//

import ComposableArchitecture
import CoreLocation
import MapboxMaps
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
    private static let defaultCoordinate = CLLocationCoordinate2D(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude)

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case initialLocationReceived(CLLocationCoordinate2D)
        case mapLocationTapped(CLLocationCoordinate2D)
        case mapCameraChanged(CLLocationCoordinate2D)
        case onTask
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
            case let .mapLocationTapped(coordinate):
                state.$game.withLock { $0.initialLocation = coordinate }
                state.cameraRegion = CameraRegion(
                    center: coordinate,
                    latitudeDelta: 0.01,
                    longitudeDelta: 0.01
                )
                self.updateMapComponents(state: &state)
                return .none
            case let .mapCameraChanged(centerCoordinates):
                state.$game.withLock { $0.initialLocation = centerCoordinates }
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

// MARK: - Address Search Helper

class AddressSearchHelper: NSObject, ObservableObject, MKLocalSearchCompleterDelegate {
    @Published var results: [MKLocalSearchCompletion] = []
    private let completer = MKLocalSearchCompleter()

    override init() {
        super.init()
        completer.delegate = self
        completer.resultTypes = [.address, .pointOfInterest]
    }

    func search(_ query: String) {
        if query.isEmpty {
            results = []
            return
        }
        completer.queryFragment = query
    }

    func completerDidUpdateResults(_ completer: MKLocalSearchCompleter) {
        results = completer.results
    }

    func completer(_ completer: MKLocalSearchCompleter, didFailWithError error: Error) {
        results = []
    }

    func resolveLocation(for completion: MKLocalSearchCompletion) async -> CLLocationCoordinate2D? {
        let request = MKLocalSearch.Request(completion: completion)
        do {
            let response = try await MKLocalSearch(request: request).start()
            return response.mapItems.first?.placemark.coordinate
        } catch {
            return nil
        }
    }
}


struct ChickenMapConfigView: View {
    @Bindable var store: StoreOf<ChickenMapConfigFeature>
    @State private var viewport: Viewport = .camera(
        center: CLLocationCoordinate2D(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude),
        zoom: 14
    )
    @State private var searchText = ""
    @StateObject private var searchHelper = AddressSearchHelper()
    @FocusState private var isSearchFieldFocused: Bool

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .top) {
                mapContent

                // Search bar + results
                searchOverlay

                // Location button
                locationButton
            }
        }
    }

    private var mapContent: some View {
        Map(viewport: $viewport) {
            Puck2D(bearing: .heading)

            if let circle = self.store.mapCircle {
                let circlePolygon = Polygon(center: circle.center, radius: circle.radius, vertices: 72)
                PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
                    .lineColor(StyleColor(UIColor(Color.CROrange).withAlphaComponent(0.8)))
                    .lineWidth(2)
            }

            if let marker = self.store.marker {
                MapViewAnnotation(coordinate: marker.coordinate) {
                    Image(systemName: "mappin.circle.fill")
                        .font(.title)
                        .foregroundStyle(.red)
                }
            }

            TapInteraction { context in
                let coordinate = context.coordinate
                store.send(.mapLocationTapped(coordinate))
                withViewportAnimation(.default(maxDuration: 0.5)) {
                    viewport = .camera(center: coordinate, zoom: 14)
                }
                searchText = ""
                searchHelper.results = []
                isSearchFieldFocused = false
                return true
            }
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
        .onChange(of: store.game.initialRadius) { _, _ in
            store.send(.mapLocationTapped(store.game.initialLocation))
        }
    }

    private var searchOverlay: some View {
        VStack(spacing: 4) {
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.gray)
                TextField("Search address", text: $searchText)
                    .foregroundColor(.primary)
                    .autocorrectionDisabled()
                    .submitLabel(.search)
                    .focused($isSearchFieldFocused)
                    .onChange(of: searchText) { _, newValue in
                        searchHelper.search(newValue)
                    }
                    .onSubmit {
                        isSearchFieldFocused = false
                    }
                if !searchText.isEmpty {
                    Button {
                        searchText = ""
                        searchHelper.results = []
                        isSearchFieldFocused = false
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.gray)
                    }
                }
            }
            .padding(12)
            .background(Color(.systemBackground))
            .cornerRadius(12)
            .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
            .padding(.horizontal, 8)
            .padding(.top, 8)

            if !searchHelper.results.isEmpty {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(searchHelper.results.prefix(5), id: \.self) { result in
                            Button {
                                Task {
                                    if let coordinate = await searchHelper.resolveLocation(for: result) {
                                        store.send(.mapLocationTapped(coordinate))
                                        withViewportAnimation(.default(maxDuration: 1)) {
                                            viewport = .camera(center: coordinate, zoom: 14)
                                        }
                                    }
                                }
                                searchText = result.title
                                searchHelper.results = []
                                isSearchFieldFocused = false
                            } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(result.title)
                                        .foregroundColor(.primary)
                                        .font(.system(size: 14, weight: .medium))
                                    if !result.subtitle.isEmpty {
                                        Text(result.subtitle)
                                            .foregroundColor(.secondary)
                                            .font(.system(size: 12))
                                    }
                                }
                                .padding(.horizontal, 16)
                                .padding(.vertical, 10)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            Divider()
                        }
                    }
                }
                .frame(maxHeight: 200)
                .background(Color(.systemBackground))
                .cornerRadius(12)
                .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
                .padding(.horizontal, 8)
            }
        }
    }

    private var locationButton: some View {
        VStack {
            Spacer()
                .frame(height: 8)
            HStack {
                Spacer()
                Button {
                    withViewportAnimation(.default(maxDuration: 1)) {
                        viewport = .followPuck(zoom: 14, bearing: .constant(0))
                    }
                } label: {
                    Image(systemName: "location.fill")
                        .padding(10)
                        .background(.regularMaterial)
                        .clipShape(Circle())
                }
                .padding(.trailing, 8)
            }
        }
        .padding(.top, 56)
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
