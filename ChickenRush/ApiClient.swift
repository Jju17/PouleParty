//
//  ApiClient.swift
//  ChickenRush
//
//  Created by Julien Rahier on 17/03/2024.
//

import ComposableArchitecture
import CoreLocation
import FirebaseFirestore

struct ApiClient {
    var setConfig: (Game) async throws -> Void
    var deleteConfig: () async throws -> Void
    var getConfig: () async -> Game?
    var getLastChickenLocation: () async throws -> CLLocationCoordinate2D?
}

extension ApiClient: DependencyKey {
    static var liveValue = ApiClient(
        setConfig: { newGame in
            try Firestore.firestore().collection("games").document("sRhFcVTSpPvWOQe947cz").setData(from: newGame)
        },
        deleteConfig: {
            try await Firestore.firestore().collection("games").document("sRhFcVTSpPvWOQe947cz").delete()
        },
        getConfig: {
            var game: Game?
            do {
                game = try await Firestore.firestore().collection("games").document("sRhFcVTSpPvWOQe947cz").getDocument(as: Game.self)
            } catch { }

            return game
        },
        getLastChickenLocation: {
            var location: CLLocationCoordinate2D?

            let snapshot = try await Firestore.firestore()
                .collection("chickenLocations")
                .order(by: "timestamp", descending: true)
                .limit(to: 1)
                .getDocuments()

            let chickenLocation = try snapshot.documents.first?.data(as: ChickenLocation.self)
            if let lat = chickenLocation?.location.latitude,
               let long = chickenLocation?.location.longitude {
                location = CLLocationCoordinate2D(latitude: lat, longitude: long)
            }

            return location
        }
    )
}

extension DependencyValues {
    var apiClient: ApiClient {
        get { self[ApiClient.self] }
        set { self[ApiClient.self] = newValue }
    }
}
