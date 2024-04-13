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
    var deleteConfig: () async throws -> Void
    var getConfig: () async -> Game?
    var getLastChickenLocation: () async throws -> CLLocationCoordinate2D?
    var setChickenLocation: (CLLocationCoordinate2D) throws -> Void
    var setConfig: (Game) throws -> Void
}

extension ApiClient: DependencyKey {
    static var liveValue = ApiClient(
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
        },
        setChickenLocation: { coordinate in
            let chickenLocation = ChickenLocation(
                location: GeoPoint(latitude: coordinate.latitude, longitude: coordinate.longitude),
                timestamp: Timestamp(date: .now)
            )

            let chickenLocationRef = Firestore.firestore().collection("chickenLocations").document()
            try chickenLocationRef.setData(from: chickenLocation)
        },
        setConfig: { newGame in
            try Firestore.firestore().collection("games").document("sRhFcVTSpPvWOQe947cz").setData(from: newGame)
        }
    )
}

extension DependencyValues {
    var apiClient: ApiClient {
        get { self[ApiClient.self] }
        set { self[ApiClient.self] = newValue }
    }
}
