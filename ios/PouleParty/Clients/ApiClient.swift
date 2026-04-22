//
//  ApiClient.swift
//  PouleParty
//
//  Created by Julien Rahier on 17/03/2024.
//

import ComposableArchitecture
import CoreLocation
import FirebaseAuth
import FirebaseFirestore
import os

struct ApiClient {
    var findActiveGame: (String) async throws -> (Game, GameRole)?
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
    var collectPowerUp: (String, String, String) async throws -> Void
    /// Activates a collected power-up atomically. Sets `activatedAt` / `expiresAt`
    /// on the power-up doc AND, if `activeEffectField` is provided, sets
    /// `powerUps.activeEffects.<field>` on the game doc — both in a single
    /// Firestore transaction so the state never drifts (no half-activated
    /// power-up with no visible effect).
    var activatePowerUp: (_ gameId: String, _ powerUpId: String, _ activeEffectField: String?, _ expiresAt: Timestamp) async throws -> Void
    var powerUpsStream: (String) -> AsyncStream<[PowerUp]>
    var updateHeartbeat: (String) throws -> Void
    var countFreeGamesToday: (String) async throws -> Int
    var fetchPartyPlansConfig: () async throws -> PartyPlansConfig
    var fetchMyGames: (String) async throws -> [MyGame]
    var findRegistration: (String, String) async throws -> Registration?
    var createRegistration: (String, Registration) async throws -> Void
    var fetchAllRegistrations: (String) async throws -> [Registration]
    var challengesStream: () -> AsyncStream<[Challenge]>
    var challengeCompletionsStream: (String) -> AsyncStream<[ChallengeCompletion]>
    var markChallengeCompleted: (String, String, String, String, Int) async throws -> Void
    var fetchUserNicknames: ([String]) async throws -> [String: String]
    /// Submit a report against another player (user-generated-content moderation).
    /// Writes a doc to `/reports/{autoId}` which is readable only by the admin SDK.
    var reportPlayer: (_ gameId: String, _ reportedUserId: String, _ reportedNickname: String) async throws -> Void
    /// Generate a new Firestore-style auto-ID (20-char alphanumeric) for a
    /// brand-new game doc. Used by the free-game client path so all game IDs
    /// in Firestore look consistent with the server-side auto-IDs produced by
    /// Cloud Functions for Forfait / promo creations, no more mix of UUIDs
    /// (client) and auto-IDs (server).
    var newGameId: () -> String
}

private let logger = Logger(category: "ApiClient")

/// Logs a Firestore `addSnapshotListener` error. `permission-denied` is a
/// transient hiccup during network wobbles / auth token refreshes — the
/// listener recovers automatically, so we log it at `.debug` to avoid noisy
/// warnings in production. Other errors stay at `.warning`.
private func logListenerError(_ operation: String, _ error: Error) {
    let code = (error as NSError).code
    if code == FirestoreErrorCode.permissionDenied.rawValue {
        logger.debug("\(operation) listener transient permission-denied (expected during auth refresh): \(error.localizedDescription)")
    } else {
        logger.warning("\(operation) listener error: \(error.localizedDescription)")
    }
}

private let gamesCollection = "games"
private let chickenLocationsSubcollection = "chickenLocations"
private let hunterLocationsSubcollection = "hunterLocations"
private let powerUpsSubcollection = "powerUps"
private let registrationsSubcollection = "registrations"
private let challengesCollection = "challenges"
private let challengeCompletionsSubcollection = "challengeCompletions"
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
                // Cap the shift so a future bump of `maxRetries` past 63 can't
                // overflow `UInt64`. With maxRetries = 3 the cap is a no-op,
                // but it keeps the call site safe by construction.
                let shift = min(attempt, 20)
                try? await Task.sleep(nanoseconds: initialDelayNs * UInt64(1 << shift))
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
        collectPowerUp: { _, _, _ in },
        activatePowerUp: { _, _, _, _ in },
        powerUpsStream: { _ in AsyncStream { _ in } },
        updateHeartbeat: { _ in },
        countFreeGamesToday: { _ in 0 },
        fetchPartyPlansConfig: { PartyPlansConfig() },
        fetchMyGames: { _ in [] },
        findRegistration: { _, _ in nil },
        createRegistration: { _, _ in },
        fetchAllRegistrations: { _ in [] },
        challengesStream: { AsyncStream { _ in } },
        challengeCompletionsStream: { _ in AsyncStream { _ in } },
        markChallengeCompleted: { _, _, _, _, _ in },
        fetchUserNicknames: { _ in [:] },
        reportPlayer: { _, _, _ in },
        newGameId: { "test-game-id" }
    )
}

extension ApiClient: DependencyKey {
    static var liveValue = ApiClient(
        findActiveGame: { userId in
            let db = Firestore.firestore()
            let activeStatuses = [Game.GameStatus.waiting.rawValue, Game.GameStatus.inProgress.rawValue]
            var candidates: [(Game, GameRole)] = []

            // Query 1: Is the user a hunter in active games?
            do {
                let hunterSnapshot = try await db.collection(gamesCollection)
                    .whereField("hunterIds", arrayContains: userId)
                    .whereField("status", in: activeStatuses)
                    .getDocuments()

                for doc in hunterSnapshot.documents {
                    if let game = try? doc.data(as: Game.self) {
                        candidates.append((game, .hunter))
                    }
                }
            } catch {
                logger.error("findActiveGame hunter query failed: \(error.localizedDescription)")
            }

            // Query 2: Is the user the chicken/creator?
            do {
                let creatorSnapshot = try await db.collection(gamesCollection)
                    .whereField("creatorId", isEqualTo: userId)
                    .whereField("status", in: activeStatuses)
                    .getDocuments()

                for doc in creatorSnapshot.documents {
                    if let game = try? doc.data(as: Game.self) {
                        candidates.append((game, .chicken))
                    }
                }
            } catch {
                logger.error("findActiveGame creator query failed: \(error.localizedDescription)")
            }

            // Filter out games whose end time has already passed (status may
            // not have been updated to DONE yet due to network/Cloud Task lag)
            let stillActive = candidates.filter { $0.0.endDate > .now }

            // Return the most recently started game
            return stillActive.max(by: { $0.0.startDate < $1.0.startDate })
        },
        addWinner: { gameId, winner in
            try await withRetry("addWinner(\(gameId))") {
                let ref = Firestore.firestore().collection(gamesCollection).document(gameId)
                try await ref.updateData([
                    "winners": FieldValue.arrayUnion([
                        [
                            "hunterId": winner.hunterId,
                            "hunterName": winner.hunterName,
                            "timestamp": winner.timestamp
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
                            logListenerError("Chicken location (game \(gameId))", error)
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
                            logListenerError("Game config (game \(gameId))", error)
                        }
                        guard let snapshot = snapshot else {
                            continuation.yield(nil)
                            return
                        }
                        guard let game: Game = {
                            do { return try snapshot.data(as: Game.self) }
                            catch {
                                // localizedDescription collapses DecodingError into a useless
                                // "data couldn't be read because it is missing" string. Dump
                                // the full error so we can see the missing key / bad type /
                                // coding path when a server-side schema drift causes a decode
                                // failure (e.g. a new required field shipping before the
                                // corresponding client update).
                                logger.error("Failed to decode Game config for \(gameId) (exists=\(snapshot.exists)): \(String(describing: error))")
                                return nil
                            }
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
                            logListenerError("Hunter locations (game \(gameId))", error)
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
        collectPowerUp: { gameId, powerUpId, userId in
            try await withRetry("collectPowerUp(\(gameId), \(powerUpId))") {
                let db = Firestore.firestore()
                let docRef = db.collection(gamesCollection).document(gameId)
                    .collection(powerUpsSubcollection).document(powerUpId)
                _ = try await db.runTransaction { transaction, errorPointer in
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
        activatePowerUp: { gameId, powerUpId, activeEffectField, expiresAt in
            try await withRetry("activatePowerUp(\(gameId), \(powerUpId))") {
                let db = Firestore.firestore()
                let puRef = db.collection(gamesCollection).document(gameId)
                    .collection(powerUpsSubcollection).document(powerUpId)
                let gameRef = db.collection(gamesCollection).document(gameId)
                _ = try await db.runTransaction { transaction, _ in
                    let now = Timestamp(date: .now)
                    transaction.updateData([
                        "activatedAt": now,
                        "expiresAt": expiresAt
                    ], forDocument: puRef)
                    if let field = activeEffectField {
                        transaction.updateData([field: expiresAt], forDocument: gameRef)
                    }
                    return nil
                }
            }
        },
        powerUpsStream: { gameId in
            AsyncStream { continuation in
                let listener = Firestore.firestore()
                    .collection(gamesCollection).document(gameId)
                    .collection(powerUpsSubcollection)
                    .addSnapshotListener { snapshot, error in
                        if let error {
                            logListenerError("Power-ups (game \(gameId))", error)
                        }
                        guard let documents = snapshot?.documents else {
                            continuation.yield([])
                            return
                        }
                        let powerUps = documents.compactMap { doc -> PowerUp? in
                            var data = doc.data()
                            data["id"] = doc.documentID
                            do { return try Firestore.Decoder().decode(PowerUp.self, from: data) }
                            catch { logger.error("Failed to decode PowerUp \(doc.documentID): \(error.localizedDescription)"); return nil }
                        }
                        continuation.yield(powerUps)
                    }

                continuation.onTermination = { _ in
                    listener.remove()
                }
            }
        },
        updateHeartbeat: { gameId in
            Firestore.firestore().collection(gamesCollection).document(gameId).updateData([
                "lastHeartbeat": Timestamp(date: .now)
            ])
        },
        countFreeGamesToday: { userId in
            let db = Firestore.firestore()
            let calendar = Calendar.current
            let startOfDay = calendar.startOfDay(for: Date())
            let startTimestamp = Timestamp(date: startOfDay)

            let snapshot = try await db.collection(gamesCollection)
                .whereField("creatorId", isEqualTo: userId)
                .whereField("pricing.model", isEqualTo: Game.PricingModel.free.rawValue)
                .whereField("timing.start", isGreaterThanOrEqualTo: startTimestamp)
                .getDocuments()

            return snapshot.documents.count
        },
        fetchPartyPlansConfig: {
            try await Firestore.firestore()
                .collection("config")
                .document("partyPlans")
                .getDocument(as: PartyPlansConfig.self)
        },
        fetchMyGames: { userId in
            // Run two queries in parallel: games I created + games I joined as a hunter.
            // We don't use .order here to avoid needing a composite index — we sort client-side.
            async let createdSnapshot = Firestore.firestore()
                .collection(gamesCollection)
                .whereField("creatorId", isEqualTo: userId)
                .limit(to: 30)
                .getDocuments()

            async let joinedSnapshot = Firestore.firestore()
                .collection(gamesCollection)
                .whereField("hunterIds", arrayContains: userId)
                .limit(to: 30)
                .getDocuments()

            let (created, joined) = try await (createdSnapshot, joinedSnapshot)

            var result: [MyGame] = []
            var seenIds = Set<String>()

            for doc in created.documents {
                guard let game = try? doc.data(as: Game.self) else { continue }
                if seenIds.insert(game.id).inserted {
                    result.append(MyGame(game: game, role: .chicken))
                }
            }

            for doc in joined.documents {
                guard let game = try? doc.data(as: Game.self) else { continue }
                // Creator takes precedence if the same user is both creator and hunter.
                if seenIds.insert(game.id).inserted {
                    result.append(MyGame(game: game, role: .hunter))
                }
            }

            // Sort by start date (most recent first) and limit to 20.
            result.sort { $0.game.startDate > $1.game.startDate }
            return Array(result.prefix(20))
        },
        findRegistration: { gameId, userId in
            guard !gameId.isEmpty, !userId.isEmpty else { return nil }
            let snapshot = try await Firestore.firestore()
                .collection(gamesCollection).document(gameId)
                .collection(registrationsSubcollection).document(userId)
                .getDocument()
            guard snapshot.exists else { return nil }
            do {
                return try snapshot.data(as: Registration.self)
            } catch {
                logger.error("Failed to decode registration \(gameId)/\(userId): \(error.localizedDescription)")
                return nil
            }
        },
        createRegistration: { gameId, registration in
            guard !gameId.isEmpty, !registration.userId.isEmpty else {
                logger.warning("createRegistration skipped — gameId: '\(gameId)', userId: '\(registration.userId)'")
                return
            }
            try await withRetry("createRegistration(\(gameId), \(registration.userId))") {
                let ref = Firestore.firestore()
                    .collection(gamesCollection).document(gameId)
                    .collection(registrationsSubcollection).document(registration.userId)
                try ref.setData(from: registration)
            }
        },
        fetchAllRegistrations: { gameId in
            guard !gameId.isEmpty else { return [] }
            let snapshot = try await Firestore.firestore()
                .collection(gamesCollection).document(gameId)
                .collection(registrationsSubcollection)
                .getDocuments()
            return snapshot.documents.compactMap { doc in
                try? doc.data(as: Registration.self)
            }
        },
        challengesStream: {
            AsyncStream { continuation in
                let listener = Firestore.firestore()
                    .collection(challengesCollection)
                    .addSnapshotListener { snapshot, error in
                        if let error {
                            logListenerError("Challenges", error)
                        }
                        guard let documents = snapshot?.documents else {
                            continuation.yield([])
                            return
                        }
                        let challenges = documents.compactMap { doc -> Challenge? in
                            do { return try doc.data(as: Challenge.self) }
                            catch {
                                logger.error("Failed to decode Challenge \(doc.documentID): \(error.localizedDescription)")
                                return nil
                            }
                        }
                        continuation.yield(challenges)
                    }

                continuation.onTermination = { _ in
                    listener.remove()
                }
            }
        },
        challengeCompletionsStream: { gameId in
            AsyncStream { continuation in
                let listener = Firestore.firestore()
                    .collection(gamesCollection).document(gameId)
                    .collection(challengeCompletionsSubcollection)
                    .addSnapshotListener { snapshot, error in
                        if let error {
                            logListenerError("Challenge completions (game \(gameId))", error)
                        }
                        guard let documents = snapshot?.documents else {
                            continuation.yield([])
                            return
                        }
                        let completions = documents.compactMap { doc -> ChallengeCompletion? in
                            do { return try doc.data(as: ChallengeCompletion.self) }
                            catch {
                                logger.error("Failed to decode ChallengeCompletion \(doc.documentID): \(error.localizedDescription)")
                                return nil
                            }
                        }
                        continuation.yield(completions)
                    }

                continuation.onTermination = { _ in
                    listener.remove()
                }
            }
        },
        markChallengeCompleted: { gameId, hunterId, teamName, challengeId, points in
            guard !gameId.isEmpty, !hunterId.isEmpty, !challengeId.isEmpty else {
                logger.warning("markChallengeCompleted skipped — gameId: '\(gameId)', hunterId: '\(hunterId)', challengeId: '\(challengeId)'")
                return
            }
            try await withRetry("markChallengeCompleted(\(gameId), \(hunterId), \(challengeId))") {
                let db = Firestore.firestore()
                let docRef = db.collection(gamesCollection).document(gameId)
                    .collection(challengeCompletionsSubcollection).document(hunterId)
                _ = try await db.runTransaction { transaction, errorPointer in
                    let snapshot: DocumentSnapshot
                    do {
                        snapshot = try transaction.getDocument(docRef)
                    } catch let error as NSError {
                        errorPointer?.pointee = error
                        return nil
                    }
                    let existing = (try? snapshot.data(as: ChallengeCompletion.self)) ?? ChallengeCompletion()
                    if existing.completedChallengeIds.contains(challengeId) {
                        // Idempotent — nothing to do.
                        return nil
                    }
                    let payload: [String: Any] = [
                        "completedChallengeIds": existing.completedChallengeIds + [challengeId],
                        "totalPoints": existing.totalPoints + points,
                        "teamName": teamName
                    ]
                    transaction.setData(payload, forDocument: docRef)
                    return nil
                }
            }
        },
        fetchUserNicknames: { userIds in
            let uniqueIds = Array(Set(userIds)).filter { !$0.isEmpty }
            guard !uniqueIds.isEmpty else { return [:] }
            // Firestore `in` queries are limited to 30 values — batch accordingly.
            let batchSize = 30
            let db = Firestore.firestore()
            var result: [String: String] = [:]
            for start in stride(from: 0, to: uniqueIds.count, by: batchSize) {
                let chunk = Array(uniqueIds[start..<min(start + batchSize, uniqueIds.count)])
                do {
                    let snapshot = try await db.collection("users")
                        .whereField(FieldPath.documentID(), in: chunk)
                        .getDocuments()
                    for doc in snapshot.documents {
                        if let nickname = doc.data()["nickname"] as? String, !nickname.isEmpty {
                            result[doc.documentID] = nickname
                        }
                    }
                } catch {
                    logger.warning("fetchUserNicknames chunk failed: \(error.localizedDescription)")
                }
            }
            return result
        },
        reportPlayer: { gameId, reportedUserId, reportedNickname in
            guard let reporterId = Auth.auth().currentUser?.uid else {
                throw NSError(
                    domain: "ApiClient",
                    code: -1,
                    userInfo: [NSLocalizedDescriptionKey: "Not authenticated"]
                )
            }
            let payload: [String: Any] = [
                "reporterId": reporterId,
                "reportedUserId": reportedUserId,
                "reportedNickname": reportedNickname,
                "gameId": gameId,
                "createdAt": FieldValue.serverTimestamp()
            ]
            try await Firestore.firestore()
                .collection("reports")
                .addDocument(data: payload)
        },
        newGameId: {
            // Local-only, no network call, the Firestore SDK generates the
            // auto-ID client-side. Same 20-char alphanumeric format as the
            // Cloud Function's `db().collection("games").doc()`.
            Firestore.firestore().collection(gamesCollection).document().documentID
        }
    )
}

extension DependencyValues {
    var apiClient: ApiClient {
        get { self[ApiClient.self] }
        set { self[ApiClient.self] = newValue }
    }
}
