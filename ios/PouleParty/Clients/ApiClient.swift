//
//  ApiClient.swift
//  PouleParty
//
//  Created by Julien Rahier on 17/03/2024.
//

import ComposableArchitecture
import CoreLocation
import FirebaseFirestore
import os

struct ApiClient {
    var findActiveGame: (String) async throws -> (Game, PlayerRole)?
    var addWinner: (String, Winner) async throws -> Void
    var deleteConfig: (String) async throws -> Void
    var getConfig: (String) async throws -> Game?
    var findGameByCode: (String) async throws -> Game?
    var registerHunter: (String, String) async throws -> Void
    var updateGameStatus: (String, Game.GameStatus) async throws -> Void
    var chickenLocationStream: (String) -> AsyncStream<CLLocationCoordinate2D?>
    var gameConfigStream: (String) -> AsyncStream<Game?>
    var hunterLocationsStream: (String) -> AsyncStream<[HunterLocation]>
    var setChickenLocation: (String, CLLocationCoordinate2D) throws -> Void
    var setConfig: (Game) async throws -> Void
    var setHunterLocation: (String, String, CLLocationCoordinate2D) throws -> Void
    var spawnPowerUps: (String, [PowerUp]) async throws -> Void
    var collectPowerUp: (String, String, String) async throws -> Void
    var activatePowerUp: (String, String, Timestamp) async throws -> Void
    var updateGameActiveEffect: (String, String, Timestamp) async throws -> Void
    var powerUpsStream: (String) -> AsyncStream<[PowerUp]>
}

private let logger = Logger(subsystem: "dev.rahier.pouleparty", category: "ApiClient")
private let gamesCollection = "games"
private let chickenLocationsSubcollection = "chickenLocations"
private let hunterLocationsSubcollection = "hunterLocations"
private let powerUpsSubcollection = "powerUps"
private let maxRetries = 3
private let initialDelayNs: UInt64 = 500_000_000

private func withRetry(_ operation: String, block: () async throws -> Void) async throws {
    var lastError: Error?
    for attempt in 0..<maxRetries {
        do {
            try await block()
            return
        } catch {
            lastError = error
            logger.warning("\(operation) failed (attempt \(attempt + 1)/\(maxRetries)): \(error)")
            if attempt < maxRetries - 1 {
                try? await Task.sleep(nanoseconds: initialDelayNs * UInt64(1 << attempt))
            }
        }
    }
    throw lastError ?? NSError(domain: "ApiClient", code: -1, userInfo: [NSLocalizedDescriptionKey: "\(operation) failed after \(maxRetries) retries"])
}

extension ApiClient: TestDependencyKey {
    static let testValue = ApiClient(
        findActiveGame: { _ in nil },
        addWinner: { _, _ in },
        deleteConfig: { _ in },
        getConfig: { _ in nil },
        findGameByCode: { _ in nil },
        registerHunter: { _, _ in },
        updateGameStatus: { _, _ in },
        chickenLocationStream: { _ in AsyncStream { _ in } },
        gameConfigStream: { _ in AsyncStream { _ in } },
        hunterLocationsStream: { _ in AsyncStream { _ in } },
        setChickenLocation: { _, _ in },
        setConfig: { _ in },
        setHunterLocation: { _, _, _ in },
        spawnPowerUps: { _, _ in },
        collectPowerUp: { _, _, _ in },
        activatePowerUp: { _, _, _ in },
        updateGameActiveEffect: { _, _, _ in },
        powerUpsStream: { _ in AsyncStream { _ in } }
    )
}

extension ApiClient: DependencyKey {
    static var liveValue = ApiClient(
        findActiveGame: { userId in
            let db = Firestore.firestore()
            let activeStatuses = [Game.GameStatus.waiting.rawValue, Game.GameStatus.inProgress.rawValue]

            // Query 1: Is the user a hunter in an active game?
            do {
                let hunterSnapshot = try await db.collection(gamesCollection)
                    .whereField("hunterIds", arrayContains: userId)
                    .whereField("status", in: activeStatuses)
                    .limit(to: 1)
                    .getDocuments()

                if let doc = hunterSnapshot.documents.first,
                   let game = try? doc.data(as: Game.self) {
                    return (game, .hunter)
                }
            } catch {
                logger.error("findActiveGame hunter query failed: \(error.localizedDescription)")
            }

            // Query 2: Is the user the chicken/creator?
            do {
                let creatorSnapshot = try await db.collection(gamesCollection)
                    .whereField("creatorId", isEqualTo: userId)
                    .whereField("status", in: activeStatuses)
                    .limit(to: 1)
                    .getDocuments()

                if let doc = creatorSnapshot.documents.first,
                   let game = try? doc.data(as: Game.self) {
                    return (game, .chicken)
                }
            } catch {
                logger.error("findActiveGame creator query failed: \(error.localizedDescription)")
            }

            return nil
        },
        addWinner: { gameId, winner in
            try await withRetry("addWinner(\(gameId))") {
                let ref = Firestore.firestore().collection(gamesCollection).document(gameId)
                try await ref.updateData([
                    "winners": FieldValue.arrayUnion([
                        [
                            "hunterId": winner.hunterId,
                            "hunterName": winner.hunterName,
                            "timestamp": Timestamp(date: winner.timestamp)
                        ] as [String: Any]
                    ])
                ])
            }
        },
        deleteConfig: { gameId in
            try await Firestore.firestore().collection(gamesCollection).document(gameId).delete()
        },
        getConfig: { gameId in
            do {
                return try await Firestore.firestore().collection(gamesCollection).document(gameId).getDocument(as: Game.self)
            } catch {
                logger.error("Failed to get game config \(gameId): \(error.localizedDescription)")
                return nil
            }
        },
        findGameByCode: { code in
            let snapshot = try await Firestore.firestore()
                .collection(gamesCollection)
                .whereField("gameCode", isEqualTo: code.uppercased())
                .limit(to: 1)
                .getDocuments()

            guard let doc = snapshot.documents.first else { return nil }

            do {
                return try doc.data(as: Game.self)
            } catch {
                logger.error("Failed to decode game from code query: \(error.localizedDescription)")
                return nil
            }
        },
        registerHunter: { gameId, hunterId in
            guard !gameId.isEmpty, !hunterId.isEmpty else {
                logger.warning("registerHunter skipped — gameId: '\(gameId)', hunterId: '\(hunterId)'")
                return
            }
            try await withRetry("registerHunter(\(gameId), \(hunterId))") {
                let ref = Firestore.firestore().collection(gamesCollection).document(gameId)
                try await ref.updateData([
                    "hunterIds": FieldValue.arrayUnion([hunterId])
                ])
            }
        },
        updateGameStatus: { gameId, status in
            try await withRetry("updateGameStatus(\(gameId), \(status))") {
                try await Firestore.firestore().collection(gamesCollection).document(gameId).updateData([
                    "status": status.rawValue
                ])
            }
        },
        chickenLocationStream: { gameId in
            AsyncStream { continuation in
                let listener = Firestore.firestore()
                    .collection(gamesCollection).document(gameId).collection(chickenLocationsSubcollection)
                    .order(by: "timestamp", descending: true)
                    .limit(to: 1)
                    .addSnapshotListener { snapshot, error in
                        if let error {
                            logger.warning("Chicken location listener error for game \(gameId): \(error.localizedDescription)")
                        }
                        guard let document = snapshot?.documents.first else {
                            continuation.yield(nil)
                            return
                        }
                        guard let chickenLocation: ChickenLocation = {
                            do { return try document.data(as: ChickenLocation.self) }
                            catch { logger.error("Failed to decode ChickenLocation: \(error.localizedDescription)"); return nil }
                        }() else {
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
                    .collection(gamesCollection)
                    .document(gameId)
                    .addSnapshotListener { snapshot, error in
                        if let error {
                            logger.warning("Game config listener error for game \(gameId): \(error.localizedDescription)")
                        }
                        guard let snapshot = snapshot else {
                            continuation.yield(nil)
                            return
                        }
                        guard let game: Game = {
                            do { return try snapshot.data(as: Game.self) }
                            catch { logger.error("Failed to decode Game config: \(error.localizedDescription)"); return nil }
                        }() else {
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
                    .collection(gamesCollection).document(gameId).collection(hunterLocationsSubcollection)
                    .addSnapshotListener { snapshot, error in
                        if let error {
                            logger.warning("Hunter locations listener error for game \(gameId): \(error.localizedDescription)")
                        }
                        guard let documents = snapshot?.documents else {
                            continuation.yield([])
                            return
                        }
                        let hunters = documents.compactMap { doc -> HunterLocation? in
                            do { return try doc.data(as: HunterLocation.self) }
                            catch { logger.error("Failed to decode HunterLocation \(doc.documentID): \(error.localizedDescription)"); return nil }
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
                .collection(gamesCollection).document(gameId).collection(chickenLocationsSubcollection).document("latest")
            try ref.setData(from: chickenLocation)
        },
        setConfig: { newGame in
            try await withRetry("setConfig(\(newGame.id))") {
                let ref = Firestore.firestore().collection(gamesCollection).document(newGame.id)
                var data = try Firestore.Encoder().encode(newGame)
                data["gameCode"] = newGame.gameCode
                try await ref.setData(data)
            }
        },
        setHunterLocation: { gameId, hunterId, coordinate in
            guard !gameId.isEmpty, !hunterId.isEmpty else {
                logger.warning("setHunterLocation skipped — gameId: '\(gameId)', hunterId: '\(hunterId)'")
                return
            }

            let hunterLocation = HunterLocation(
                hunterId: hunterId,
                location: GeoPoint(latitude: coordinate.latitude, longitude: coordinate.longitude),
                timestamp: Timestamp(date: .now)
            )

            let ref = Firestore.firestore()
                .collection(gamesCollection).document(gameId).collection(hunterLocationsSubcollection).document(hunterId)
            try ref.setData(from: hunterLocation, merge: true)
        },
        spawnPowerUps: { gameId, powerUps in
            try await withRetry("spawnPowerUps(\(gameId), \(powerUps.count) items)") {
                let batch = Firestore.firestore().batch()
                for powerUp in powerUps {
                    let ref = Firestore.firestore()
                        .collection(gamesCollection).document(gameId)
                        .collection(powerUpsSubcollection).document(powerUp.id)
                    try batch.setData(from: powerUp, forDocument: ref, merge: true)
                }
                try await batch.commit()
            }
        },
        collectPowerUp: { gameId, powerUpId, userId in
            try await withRetry("collectPowerUp(\(gameId), \(powerUpId))") {
                let db = Firestore.firestore()
                let docRef = db.collection(gamesCollection).document(gameId)
                    .collection(powerUpsSubcollection).document(powerUpId)
                try await db.runTransaction { transaction, errorPointer in
                    let snapshot: DocumentSnapshot
                    do {
                        snapshot = try transaction.getDocument(docRef)
                    } catch let error as NSError {
                        errorPointer?.pointee = error
                        return nil
                    }
                    if snapshot.data()?["collectedBy"] != nil {
                        let error = NSError(domain: "ApiClient", code: -2, userInfo: [NSLocalizedDescriptionKey: "Power-up already collected"])
                        errorPointer?.pointee = error
                        return nil
                    }
                    transaction.updateData([
                        "collectedBy": userId,
                        "collectedAt": Timestamp(date: .now)
                    ], forDocument: docRef)
                    return nil
                }
            }
        },
        activatePowerUp: { gameId, powerUpId, expiresAt in
            try await withRetry("activatePowerUp(\(gameId), \(powerUpId))") {
                try await Firestore.firestore()
                    .collection(gamesCollection).document(gameId)
                    .collection(powerUpsSubcollection).document(powerUpId)
                    .updateData([
                        "activatedAt": Timestamp(date: .now),
                        "expiresAt": expiresAt
                    ])
            }
        },
        updateGameActiveEffect: { gameId, field, until in
            try await withRetry("updateGameActiveEffect(\(gameId), \(field))") {
                try await Firestore.firestore()
                    .collection(gamesCollection).document(gameId)
                    .updateData([field: until])
            }
        },
        powerUpsStream: { gameId in
            AsyncStream { continuation in
                let listener = Firestore.firestore()
                    .collection(gamesCollection).document(gameId)
                    .collection(powerUpsSubcollection)
                    .addSnapshotListener { snapshot, error in
                        if let error {
                            logger.warning("Power-ups listener error for game \(gameId): \(error.localizedDescription)")
                        }
                        guard let documents = snapshot?.documents else {
                            continuation.yield([])
                            return
                        }
                        let powerUps = documents.compactMap { doc -> PowerUp? in
                            do { return try doc.data(as: PowerUp.self) }
                            catch { logger.error("Failed to decode PowerUp \(doc.documentID): \(error.localizedDescription)"); return nil }
                        }
                        continuation.yield(powerUps)
                    }

                continuation.onTermination = { _ in
                    listener.remove()
                }
            }
        }
    )
}

extension DependencyValues {
    var apiClient: ApiClient {
        get { self[ApiClient.self] }
        set { self[ApiClient.self] = newValue }
    }
}
