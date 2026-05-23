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
    case finalZone
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
        var currentGame: Game { game }
    }
    private static let defaultCoordinate = CLLocationCoordinate2D(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude)

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case initialLocationReceived(CLLocationCoordinate2D)
        case initialRadiusChanged(Double)
        case mapLocationTapped(CLLocationCoordinate2D)
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
                state.cameraRegion = CameraRegion(center: coordinate)
                // PP-11 / PP-13: seed both `startPin` (user-placed
                // pin) and `zone.center` (initial disc center) from
                // the same coordinate. PP-13's recap will later
                // overwrite `zone.center` with the computed random
                // center, but `startPin` stays at the user's
                // placement.
                state.$game.withLock {
                    $0.startPinLocation = coordinate
                    $0.initialLocation = coordinate
                }
                self.updateMapComponents(state: &state)
                // Restore final marker if game has a final location
                if let finalCoord = state.game.finalLocation {
                    state.finalMarker = MarkerOverlay(title: "Final", coordinate: finalCoord)
                }
                return .none
            case .onTask:
                // If game already has a configured location (not default Brussels), keep it
                let gameLocation = state.game.startPinLocation
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
                // PP-11 / PP-12: each step owns one pinMode (forced by
                // StartZoneSetupStep / FinalZoneSetupStep). The recap step
                // (PP-13) recomputes the zone radius from the two pins, so
                // we no longer constrain the final pin to fit inside an
                // arbitrary slider-controlled radius — only the
                // ≥ 100 m minimum distance, enforced when the user taps
                // Next via `isFinalZoneConfigured`.
                switch state.pinMode {
                case .finalZone:
                    state.$game.withLock { $0.finalLocation = coordinate }
                    state.finalMarker = MarkerOverlay(title: "Final", coordinate: coordinate)
                case .start:
                    // PP-11 / PP-13: write both `startPin` (user's
                    // placed pin) and `zone.center` (current disc
                    // center) so the PP-11 preview circle re-centers
                    // on the new pin until PP-13 picks a non-centered
                    // computed center.
                    state.$game.withLock {
                        $0.startPinLocation = coordinate
                        $0.initialLocation = coordinate
                    }
                    state.cameraRegion = CameraRegion(center: coordinate)
                    self.updateMapComponents(state: &state)
                    // If the user moves the start pin close enough that
                    // the existing final pin falls below 100 m, clear it
                    // so they're forced to re-place it on PP-12.
                    if let finalCoord = state.game.finalLocation {
                        let newStart = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
                        let finalLoc = CLLocation(latitude: finalCoord.latitude, longitude: finalCoord.longitude)
                        if newStart.distance(from: finalLoc) < 100 {
                            state.$game.withLock { $0.finalLocation = nil }
                            state.finalMarker = nil
                        }
                    }
                }
                return .none
            case let .initialRadiusChanged(radius):
                state.$game.withLock { $0.zone.radius = radius }
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
        // PP-11/PP-12 always display the start marker at the
        // user-placed pin (`startPinLocation`), independently of where
        // the PP-13 recap may have re-centered the disc.
        state.marker = MarkerOverlay(title: "Start", coordinate: state.game.startPinLocation)
        state.mapCircle = CircleOverlay(
            center: state.game.startPinLocation,
            radius: CLLocationDistance(state.game.zone.radius)
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
    @State private var viewport: Viewport

    init(store: StoreOf<ChickenMapConfigFeature>) {
        self.store = store
        let center = store.currentGame.initialLocation
        let zoom = zoomForRadius(
            CLLocationDistance(store.currentGame.zone.radius),
            latitude: center.latitude
        )
        _viewport = State(initialValue: .camera(center: center, zoom: zoom))
    }
    @State private var searchText = ""
    @StateObject private var searchHelper = AddressSearchHelper()
    @FocusState private var isSearchFieldFocused: Bool

    private var radiusZoom: CGFloat {
        zoomForRadius(CLLocationDistance(store.currentGame.zone.radius), latitude: store.currentGame.initialLocation.latitude)
    }

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                mapContent

                // Search bar at top
                VStack {
                    searchOverlay
                        .padding(.top, 32)
                    Spacer()
                }

                // Location button
                locationButton

                // Bottom bar driven by the current step (PP-11 / PP-12).
                // The legacy combined `Start zone / Final zone` segmented
                // picker is gone — each wizard step owns one pinMode,
                // forced by `StartZoneSetupStep` / `FinalZoneSetupStep`.
                VStack {
                    Spacer()
                    VStack(spacing: 10) {
                        if store.pinMode == .start
                            && store.currentGame.gameMode == .followTheChicken {
                            // PP-11: 3-button size picker replaces the
                            // 500…2000 slider in followTheChicken so the
                            // chicken picks a familiar T-shirt size
                            // instead of fiddling with a 14-step slider.
                            zoneSizePicker
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(Color.background.opacity(0.85))
                    .background(.ultraThinMaterial)
                }
                .ignoresSafeArea(.keyboard)
            }
        }
    }

    private var mapContent: some View {
        Map(viewport: $viewport) {
            Puck2D(bearing: .heading)

            // PP-11: preview circle only when the radius is something the
            // user controls — i.e. `followTheChicken` mode where the
            // 3-button size picker drives the radius. In `stayInTheZone`
            // the radius is recomputed at the recap step (PP-13) so
            // showing a stale 1500m circle would be misleading.
            if let circle = self.store.mapCircle,
               store.currentGame.gameMode == .followTheChicken,
               store.pinMode == .start {
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
                // PP-12: on the final step the start pin is read-only —
                // fade it so the user clearly sees they're now placing
                // the final pin. Tap routing already gates this (taps in
                // `.finalZone` mode never touch `initialLocation`).
                let isStartReadOnly = store.pinMode == .finalZone
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
                    .opacity(isStartReadOnly ? 0.4 : 1)
                }
                .allowOverlap(true)
                .allowOverlapWithPuck(true)
                .ignoreCameraPadding(true)
                .priority(1)
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
                .allowOverlapWithPuck(true)
                .ignoreCameraPadding(true)
                .priority(1)

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
        .mapStyle(.streets)
        .ignoresSafeArea()
        .onChange(of: store.cameraRegion) { _, newRegion in
            withViewportAnimation(.default(maxDuration: 1)) {
                viewport = .camera(center: newRegion.center, zoom: radiusZoom)
            }
        }
        .task {
            store.send(.onTask)
        }
        .onChange(of: store.currentGame.zone.radius) { _, newRadius in
            store.send(.initialRadiusChanged(newRadius))
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

    /// PP-11 — 3-button size picker shown on the `startZoneSetup` step
    /// in `followTheChicken` mode. Replaces the 14-step radius slider so
    /// the chicken picks a familiar T-shirt size instead of fiddling
    /// with a sub-100m slider increment.
    private var zoneSizePicker: some View {
        let sizes: [(label: String, meters: Double)] = [
            (String(localized: "Small"), 500),
            (String(localized: "Medium"), 1000),
            (String(localized: "Large"), 2000),
        ]
        return HStack(spacing: 8) {
            ForEach(sizes, id: \.meters) { size in
                Button {
                    store.send(.initialRadiusChanged(size.meters))
                } label: {
                    VStack(spacing: 2) {
                        Text(size.label)
                            .font(.gameboy(size: 10))
                        Text("\(Int(size.meters)) m")
                            .font(.gameboy(size: 7))
                            .opacity(0.7)
                    }
                    .foregroundStyle(
                        store.currentGame.zone.radius == size.meters ? .white : Color.onBackground
                    )
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(
                        Capsule()
                            .fill(
                                store.currentGame.zone.radius == size.meters
                                    ? AnyShapeStyle(Color.gradientFire)
                                    : AnyShapeStyle(Color.surface)
                            )
                    )
                }
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
