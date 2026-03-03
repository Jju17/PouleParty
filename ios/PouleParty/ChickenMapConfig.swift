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

// MARK: - Address Search Client

struct AddressSearchResult: Equatable, Identifiable {
    var id: String { "\(title)|\(subtitle)" }
    let title: String
    let subtitle: String
}

struct AddressSearchClient: Sendable {
    var updateQuery: @Sendable (String) async -> Void
    var results: @Sendable () -> AsyncStream<[AddressSearchResult]>
    var resolveLocation: @Sendable (AddressSearchResult) async -> CLLocationCoordinate2D?
}

extension AddressSearchClient: DependencyKey {
    static let liveValue: AddressSearchClient = {
        let manager = LiveSearchManager()
        return AddressSearchClient(
            updateQuery: { query in
                await MainActor.run { manager.updateQuery(query) }
            },
            results: { manager.results() },
            resolveLocation: { result in
                await manager.resolveLocation(for: result)
            }
        )
    }()

    static let testValue = AddressSearchClient(
        updateQuery: { _ in },
        results: { AsyncStream { $0.finish() } },
        resolveLocation: { _ in nil }
    )
}

extension DependencyValues {
    var addressSearchClient: AddressSearchClient {
        get { self[AddressSearchClient.self] }
        set { self[AddressSearchClient.self] = newValue }
    }
}

private final class LiveSearchManager: NSObject, MKLocalSearchCompleterDelegate, @unchecked Sendable {
    private let completer = MKLocalSearchCompleter()
    private var continuation: AsyncStream<[AddressSearchResult]>.Continuation?
    private var currentCompletions: [MKLocalSearchCompletion] = []

    override init() {
        super.init()
        completer.delegate = self
        completer.resultTypes = [.address, .pointOfInterest]
    }

    func updateQuery(_ query: String) {
        if query.isEmpty {
            currentCompletions = []
            continuation?.yield([])
        } else {
            completer.queryFragment = query
        }
    }

    func results() -> AsyncStream<[AddressSearchResult]> {
        AsyncStream { [weak self] continuation in
            self?.continuation = continuation
            continuation.onTermination = { [weak self] _ in
                self?.continuation = nil
            }
        }
    }

    func resolveLocation(for result: AddressSearchResult) async -> CLLocationCoordinate2D? {
        let completion = await MainActor.run {
            currentCompletions.first(where: {
                $0.title == result.title && $0.subtitle == result.subtitle
            })
        }
        guard let completion else { return nil }
        let request = MKLocalSearch.Request(completion: completion)
        do {
            let response = try await MKLocalSearch(request: request).start()
            return response.mapItems.first?.placemark.coordinate
        } catch {
            return nil
        }
    }

    func completerDidUpdateResults(_ completer: MKLocalSearchCompleter) {
        currentCompletions = completer.results
        continuation?.yield(completer.results.map {
            AddressSearchResult(title: $0.title, subtitle: $0.subtitle)
        })
    }

    func completer(_ completer: MKLocalSearchCompleter, didFailWithError error: Error) {
        currentCompletions = []
        continuation?.yield([])
    }
}

// MARK: - Feature

@Reducer
struct ChickenMapConfigFeature {

    @ObservableState
    struct State: Equatable {
        var cameraRegion: CameraRegion = .brussels
        @Shared var game: Game
        var marker: MarkerOverlay?
        var mapCircle: CircleOverlay?
        var searchText: String = ""
        var searchResults: [AddressSearchResult] = []
    }
    private static let defaultCoordinate = CLLocationCoordinate2D(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude)

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case initialLocationReceived(CLLocationCoordinate2D)
        case onLocationSelected(CLLocationCoordinate2D)
        case onMapCameraChange(CLLocationCoordinate2D)
        case onTask
        case onUpdatedRadius
        case searchResultsUpdated([AddressSearchResult])
        case searchResultTapped(AddressSearchResult)
        case clearSearch
    }

    @Dependency(\.locationClient) var locationClient
    @Dependency(\.addressSearchClient) var addressSearchClient

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding(\.searchText):
                return .run { [query = state.searchText] _ in
                    await addressSearchClient.updateQuery(query)
                }
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
                return .merge(
                    .run { send in
                        var firstLocation = gameLocation
                        for await coordinate in locationClient.startTracking() {
                            firstLocation = coordinate
                            break
                        }
                        locationClient.stopTracking()
                        await send(.initialLocationReceived(firstLocation))
                    },
                    .run { send in
                        for await results in addressSearchClient.results() {
                            await send(.searchResultsUpdated(results))
                        }
                    }
                )
            case let .onLocationSelected(coordinate):
                state.$game.withLock { $0.initialLocation = coordinate }
                state.cameraRegion = CameraRegion(
                    center: coordinate,
                    latitudeDelta: 0.01,
                    longitudeDelta: 0.01
                )
                state.searchText = ""
                state.searchResults = []
                self.updateMapComponents(state: &state)
                return .run { _ in
                    await addressSearchClient.updateQuery("")
                }
            case let .onMapCameraChange(centerCoordinates):
                state.$game.withLock { $0.initialLocation = centerCoordinates }
                self.updateMapComponents(state: &state)
                return .none
            case .onUpdatedRadius:
                self.updateMapComponents(state: &state)
                return .none
            case let .searchResultsUpdated(results):
                state.searchResults = results
                return .none
            case let .searchResultTapped(result):
                state.searchText = result.title
                state.searchResults = []
                return .run { send in
                    if let coordinate = await addressSearchClient.resolveLocation(result) {
                        await send(.onLocationSelected(coordinate))
                    }
                }
            case .clearSearch:
                state.searchText = ""
                state.searchResults = []
                return .run { _ in
                    await addressSearchClient.updateQuery("")
                }
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
                store.send(.onLocationSelected(coordinate))
                withViewportAnimation(.default(maxDuration: 0.5)) {
                    viewport = .camera(center: coordinate, zoom: 14)
                }
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
        .safeAreaInset(edge: .bottom) {
            VStack(alignment: .leading) {
                Text("Radius: \(Int(self.store.game.initialRadius))")
                Slider(value: $store.game.initialRadius, in: 500...2000, step: 100)
                .onChange(of: self.store.game.initialRadius) { _, _ in
                    store.send(.onUpdatedRadius)
                }
            }
            .padding()
            .background(.regularMaterial)
        }
    }

    private var searchOverlay: some View {
        VStack(spacing: 4) {
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.gray)
                TextField("Search address", text: $store.searchText)
                    .foregroundColor(.primary)
                    .autocorrectionDisabled()
                    .submitLabel(.search)
                    .focused($isSearchFieldFocused)
                    .onSubmit {
                        isSearchFieldFocused = false
                    }
                if !store.searchText.isEmpty {
                    Button {
                        store.send(.clearSearch)
                        isSearchFieldFocused = false
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.gray)
                    }
                }
            }
            .padding(12)
            .background(Color(.systemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .shadow(color: .black.opacity(0.15), radius: 4, y: 2)
            .padding(.horizontal, 8)
            .padding(.top, 8)

            if !store.searchResults.isEmpty {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(store.searchResults.prefix(5)) { result in
                            Button {
                                store.send(.searchResultTapped(result))
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
                .clipShape(RoundedRectangle(cornerRadius: 12))
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
