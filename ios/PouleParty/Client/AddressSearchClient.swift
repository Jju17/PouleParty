//
//  AddressSearchClient.swift
//  PouleParty
//

import ComposableArchitecture
import CoreLocation
import MapKit

// MARK: - Model

struct AddressSearchResult: Equatable, Identifiable {
    var id: String { "\(title)|\(subtitle)" }
    let title: String
    let subtitle: String
}

// MARK: - Client

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

// MARK: - Live Implementation

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
