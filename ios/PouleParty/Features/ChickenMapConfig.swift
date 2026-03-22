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

enum MapConfigPinMode: Equatable {
    case start
    case final_
}

@Reducer
struct ChickenMapConfigFeature {

    @ObservableState
    struct State: Equatable {
        var cameraRegion: CameraRegion = .brussels
        @Shared var game: Game
        var marker: MarkerOverlay?
        var finalMarker: MarkerOverlay?
        var mapCircle: CircleOverlay?
        var pinMode: MapConfigPinMode = .start
    }
    private static let defaultCoordinate = CLLocationCoordinate2D(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude)

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case initialLocationReceived(CLLocationCoordinate2D)
        case initialRadiusChanged(Double)
        case mapLocationTapped(CLLocationCoordinate2D)
        case mapCameraChanged(CLLocationCoordinate2D)
        case onTask
        case pinModeChanged(MapConfigPinMode)
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
                // Restore final marker if game has a final location
                if let finalCoord = state.game.finalLocation {
                    state.finalMarker = MarkerOverlay(title: "Final", coordinate: finalCoord)
                }
                return .none
            case .onTask:
                // If game already has a configured location (not default Brussels), keep it
                let gameLocation = state.game.initialLocation
                let isDefault = abs(gameLocation.latitude - AppConstants.defaultLatitude) < 0.001
                    && abs(gameLocation.longitude - AppConstants.defaultLongitude) < 0.001
                if !isDefault {
                    // Restore existing configuration without resetting
                    return .send(.initialLocationReceived(gameLocation))
                }
                // Otherwise, get current location
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
                if state.pinMode == .final_ {
                    // Validate: final point must be within initial circle
                    let initialLoc = CLLocation(latitude: state.game.initialLocation.latitude, longitude: state.game.initialLocation.longitude)
                    let tappedLoc = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
                    let distance = initialLoc.distance(from: tappedLoc)
                    if distance > state.game.initialRadius {
                        return .none // Outside initial zone, ignore
                    }
                    state.$game.withLock { $0.finalLocation = coordinate }
                    state.finalMarker = MarkerOverlay(title: "Final", coordinate: coordinate)
                } else {
                    state.$game.withLock { $0.initialLocation = coordinate }
                    state.cameraRegion = CameraRegion(
                        center: coordinate,
                        latitudeDelta: 0.01,
                        longitudeDelta: 0.01
                    )
                    self.updateMapComponents(state: &state)

                    // Validate existing final zone — clear if now outside new start zone
                    if let finalCoord = state.game.finalLocation {
                        let newStart = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
                        let finalLoc = CLLocation(latitude: finalCoord.latitude, longitude: finalCoord.longitude)
                        if newStart.distance(from: finalLoc) > state.game.initialRadius {
                            state.$game.withLock { $0.finalLocation = nil }
                            state.finalMarker = nil
                        }
                    }

                    // Auto-switch to final zone mode after placing start
                    state.pinMode = .final_
                }
                return .none
            case let .mapCameraChanged(centerCoordinates):
                state.$game.withLock { $0.initialLocation = centerCoordinates }
                self.updateMapComponents(state: &state)
                return .none
            case let .initialRadiusChanged(radius):
                state.$game.withLock { $0.initialRadius = radius }
                self.updateMapComponents(state: &state)
                // Validate final zone against new radius
                if let finalCoord = state.game.finalLocation {
                    let start = CLLocation(latitude: state.game.initialLocation.latitude, longitude: state.game.initialLocation.longitude)
                    let finalLoc = CLLocation(latitude: finalCoord.latitude, longitude: finalCoord.longitude)
                    if start.distance(from: finalLoc) > radius {
                        state.$game.withLock { $0.finalLocation = nil }
                        state.finalMarker = nil
                    }
                }
                return .none
            case let .pinModeChanged(mode):
                state.pinMode = mode
                return .none
            }
        }
    }

    private func updateMapComponents(state: inout ChickenMapConfigFeature.State) {
        state.marker = MarkerOverlay(title: "Start", coordinate: state.game.initialLocation)
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

    private var radiusZoom: CGFloat {
        zoomForRadius(CLLocationDistance(store.game.initialRadius), latitude: store.game.initialLocation.latitude)
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                mapContent

                // Search bar at top
                VStack {
                    searchOverlay
                    Spacer()
                }

                // Location button
                locationButton

                // Bottom bar: radius slider + pin mode picker + hint
                VStack {
                    Spacer()
                    VStack(spacing: 10) {
                        if store.pinMode == .final_ {
                            Text("Tap inside the start zone to place the final zone center")
                                .font(.gameboy(size: 8))
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                        }

                        // Radius slider
                        VStack(spacing: 4) {
                            HStack {
                                Text("Radius")
                                    .font(.gameboy(size: 9))
                                    .foregroundStyle(.primary)
                                Spacer()
                                Text("\(Int(store.game.initialRadius)) m")
                                    .font(.gameboy(size: 9))
                                    .foregroundStyle(Color.CROrange)
                            }
                            Slider(
                                value: Binding(
                                    get: { store.game.initialRadius },
                                    set: { store.send(.initialRadiusChanged($0)) }
                                ),
                                in: 500...2000,
                                step: 100
                            )
                            .tint(.CROrange)
                        }

                        Picker("Pin Mode", selection: Binding(
                            get: { store.pinMode },
                            set: { store.send(.pinModeChanged($0)) }
                        )) {
                            Text("Start zone").tag(MapConfigPinMode.start)
                            Text("Final zone").tag(MapConfigPinMode.final_)
                        }
                        .pickerStyle(.segmented)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(.thinMaterial)
                }
                .ignoresSafeArea(.keyboard)
            }
        }
    }

    private var mapContent: some View {
        Map(viewport: $viewport) {
            Puck2D(bearing: .heading)

            if let circle = self.store.mapCircle {
                let circlePolygon = Polygon(center: circle.center, radius: circle.radius, vertices: 72)
                // Neon glow on start zone circle
                PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
                    .lineColor(StyleColor(UIColor(Color.CROrange).withAlphaComponent(0.08)))
                    .lineWidth(16)
                PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
                    .lineColor(StyleColor(UIColor(Color.CROrange).withAlphaComponent(0.15)))
                    .lineWidth(8)
                PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
                    .lineColor(StyleColor(UIColor(Color.CROrange).withAlphaComponent(0.35)))
                    .lineWidth(4)
                PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
                    .lineColor(StyleColor(UIColor(Color.CROrange).withAlphaComponent(0.9)))
                    .lineWidth(2.5)
            }

            if let marker = self.store.marker {
                MapViewAnnotation(coordinate: marker.coordinate) {
                    VStack(spacing: 0) {
                        Text("START")
                            .font(.gameboy(size: 7))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(
                                Capsule()
                                    .fill(Color.CROrange)
                                    .shadow(color: Color.CROrange.opacity(0.5), radius: 6, y: 2)
                            )
                        Image(systemName: "triangle.fill")
                            .font(.system(size: 8))
                            .foregroundStyle(Color.CROrange)
                            .rotationEffect(.degrees(180))
                    }
                }
                .allowOverlap(true)
            }

            // Final zone marker
            if let finalMarker = self.store.finalMarker {
                MapViewAnnotation(coordinate: finalMarker.coordinate) {
                    VStack(spacing: 0) {
                        Text("FINAL")
                            .font(.gameboy(size: 7))
                            .foregroundStyle(.black)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(
                                Capsule()
                                    .fill(Color.zoneGreen)
                                    .shadow(color: Color.zoneGreen.opacity(0.5), radius: 6, y: 2)
                            )
                        Image(systemName: "triangle.fill")
                            .font(.system(size: 8))
                            .foregroundStyle(Color.zoneGreen)
                            .rotationEffect(.degrees(180))
                    }
                }
                .allowOverlap(true)

                // Neon glow circle at final position
                let finalCircle = Polygon(center: finalMarker.coordinate, radius: 50, vertices: 36)
                PolylineAnnotation(lineCoordinates: finalCircle.outerRing.coordinates)
                    .lineColor(StyleColor(UIColor(Color.zoneGreen).withAlphaComponent(0.15)))
                    .lineWidth(8)
                PolylineAnnotation(lineCoordinates: finalCircle.outerRing.coordinates)
                    .lineColor(StyleColor(UIColor(Color.zoneGreen).withAlphaComponent(0.5)))
                    .lineWidth(3)
                PolylineAnnotation(lineCoordinates: finalCircle.outerRing.coordinates)
                    .lineColor(StyleColor(UIColor(Color.zoneGreen).withAlphaComponent(0.9)))
                    .lineWidth(1.5)
            }

            TapInteraction { context in
                let coordinate = context.coordinate
                store.send(.mapLocationTapped(coordinate))
                if store.pinMode == .start {
                    withViewportAnimation(.default(maxDuration: 0.5)) {
                        viewport = .camera(center: coordinate, zoom: radiusZoom)
                    }
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
                viewport = .camera(center: newRegion.center, zoom: radiusZoom)
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

            if !searchHelper.results.isEmpty {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(searchHelper.results.prefix(5), id: \.self) { result in
                            Button {
                                Task {
                                    if let coordinate = await searchHelper.resolveLocation(for: result) {
                                        store.send(.mapLocationTapped(coordinate))
                                        withViewportAnimation(.default(maxDuration: 1)) {
                                            viewport = .camera(center: coordinate, zoom: radiusZoom)
                                        }
                                    }
                                }
                                searchText = ""
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
                        viewport = .followPuck(zoom: radiusZoom, bearing: .constant(0))
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
