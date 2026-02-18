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
    var deleteConfig: (String) async throws -> Void
    var getConfig: (String) async throws -> Game?
    var findGameByCode: (String) async throws -> Game?
    var chickenLocationStream: (String) -> AsyncStream<CLLocationCoordinate2D?>
    var gameConfigStream: (String) -> AsyncStream<Game?>
    var hunterLocationsStream: (String) -> AsyncStream<[HunterLocation]>
    var setChickenLocation: (String, CLLocationCoordinate2D) throws -> Void
    var setConfig: (Game) throws -> Void
    var setHunterLocation: (String, String, CLLocationCoordinate2D) throws -> Void
}

extension ApiClient: DependencyKey {
    static var liveValue = ApiClient(
        deleteConfig: { gameId in
            try await Firestore.firestore().collection("games").document(gameId).delete()
        },
        getConfig: { gameId in
            try await Firestore.firestore().collection("games").document(gameId).getDocument(as: Game.self)
        },
        findGameByCode: { code in
            let snapshot = try await Firestore.firestore()
                .collection("games")
                .getDocuments()

            return snapshot.documents.compactMap { doc in
                try? doc.data(as: Game.self)
            }.first { game in
                game.gameCode == code.uppercased()
            }
        },
        chickenLocationStream: { gameId in
            AsyncStream { continuation in
                let listener = Firestore.firestore()
                    .collection("games").document(gameId).collection("chickenLocations")
                    .order(by: "timestamp", descending: true)
                    .limit(to: 1)
                    .addSnapshotListener { snapshot, error in
                        guard let document = snapshot?.documents.first,
                              let chickenLocation = try? document.data(as: ChickenLocation.self)
                        else {
                            continuation.yield(nil)
                            return
                        }
                        let coordinate = CLLocationCoordinate2D(
                            latitude: chickenLocation.location.latitude,
                            longitude: chickenLocation.location.longitude
                        )
                        continuation.yield(coordinate)
                    }

                continuation.onTermination = { _ in
                    listener.remove()
                }
            }
        },
        gameConfigStream: { gameId in
            AsyncStream { continuation in
                let listener = Firestore.firestore()
                    .collection("games")
                    .document(gameId)
                    .addSnapshotListener { snapshot, error in
                        guard let snapshot = snapshot,
                              let game = try? snapshot.data(as: Game.self)
                        else {
                            continuation.yield(nil)
                            return
                        }
                        continuation.yield(game)
                    }

                continuation.onTermination = { _ in
                    listener.remove()
                }
            }
        },
        hunterLocationsStream: { gameId in
            AsyncStream { continuation in
                let listener = Firestore.firestore()
                    .collection("games").document(gameId).collection("hunterLocations")
                    .addSnapshotListener { snapshot, error in
                        guard let documents = snapshot?.documents else {
                            continuation.yield([])
                            return
                        }
                        let hunters = documents.compactMap { doc in
                            try? doc.data(as: HunterLocation.self)
                        }
                        continuation.yield(hunters)
                    }

                continuation.onTermination = { _ in
                    listener.remove()
                }
            }
        },
        setChickenLocation: { gameId, coordinate in
            let chickenLocation = ChickenLocation(
                location: GeoPoint(latitude: coordinate.latitude, longitude: coordinate.longitude),
                timestamp: Timestamp(date: .now)
            )

            let ref = Firestore.firestore()
                .collection("games").document(gameId).collection("chickenLocations").document()
            try ref.setData(from: chickenLocation)
        },
        setConfig: { newGame in
            try Firestore.firestore().collection("games").document(newGame.id).setData(from: newGame)
        },
        setHunterLocation: { gameId, hunterId, coordinate in
            let hunterLocation = HunterLocation(
                hunterId: hunterId,
                location: GeoPoint(latitude: coordinate.latitude, longitude: coordinate.longitude),
                timestamp: Timestamp(date: .now)
            )

            let ref = Firestore.firestore()
                .collection("games").document(gameId).collection("hunterLocations").document(hunterId)
            try ref.setData(from: hunterLocation, merge: true)
        }
    )
}

extension DependencyValues {
    var apiClient: ApiClient {
        get { self[ApiClient.self] }
        set { self[ApiClient.self] = newValue }
    }
}
