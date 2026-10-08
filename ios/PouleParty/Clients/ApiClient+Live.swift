import ComposableArchitecture
import CoreLocation
import FirebaseAuth
import FirebaseDatabase
import FirebaseFirestore
import FirebaseFunctions
import FirebaseStorage
import os

private let logger = Logger(category: "ApiClient")

private enum Paths {
    static let games = "games"
    static let gameCodes = "gameCodes"
    static let chickenLocations = "chickenLocations"
    static let hunterLocations = "hunterLocations"
    static let powerUps = "powerUps"
    static let players = "players"
    static let users = "users"
    static let memberships = "memberships"
    static let challenges = "challenges"
    static let challengeCompletions = "challengeCompletions"
    static let challengeSubmissions = "challengeSubmissions"
    static let reports = "reports"
    static let functionsRegion = "europe-west1"
}

/// A radar ping grants the chicken read for a short window, so retry quickly.
private let presenceRetryCap: Duration = .seconds(5)

private func games() -> CollectionReference {
    Firestore.firestore().collection(Paths.games)
}

private func callFunction(_ name: String, _ payload: [String: Any]) async throws -> [String: Any]? {
    do {
        let result = try await Functions.functions(region: Paths.functionsRegion).httpsCallable(name).call(payload)
        return result.data as? [String: Any]
    } catch {
        throw ApiError(error)
    }
}

private func withRetry(_ operation: String, block: () async throws -> Void) async throws {
    let maxAttempts = 3
    var lastError: Error?
    for attempt in 0..<maxAttempts {
        do {
            try await block()
            return
        } catch {
            lastError = error
            logger.warning("\(operation) failed (attempt \(attempt + 1)/\(maxAttempts)): \(error.localizedDescription)")
            if attempt < maxAttempts - 1 {
                try? await Task.sleep(for: .milliseconds(500 << attempt))
            }
        }
    }
    throw ApiError(lastError ?? ApiError(code: .unknown))
}

private func fetchGame(_ gameId: String) async throws -> Game? {
    let snapshot = try await games().document(gameId).getDocument()
    guard snapshot.exists else { return nil }
    return try snapshot.data(as: Game.self)
}

private func membershipGameIds(_ userId: String, limit: Int? = nil) async throws -> Set<String> {
    var query: Query = Firestore.firestore().collection(Paths.users).document(userId).collection(Paths.memberships)
    if let limit { query = query.limit(to: limit) }
    let snapshot = try await query.getDocuments()
    return Set(snapshot.documents.compactMap { ($0.data()["gameId"] as? String) ?? ($0.documentID.isEmpty ? nil : $0.documentID) })
}

/// A missing or unreadable game is skipped; the rest of the list still loads.
private func fetchGames(_ ids: Set<String>) async -> [Game] {
    await withTaskGroup(of: Game?.self) { group in
        for id in ids {
            group.addTask {
                do { return try await fetchGame(id) } catch {
                    logger.warning("Game \(id) unreadable: \(error.localizedDescription)")
                    return nil
                }
            }
        }
        var result: [Game] = []
        for await game in group { if let game { result.append(game) } }
        return result
    }
}

private func documentStream<Value>(
    _ operation: String,
    _ reference: DocumentReference,
    decode: @escaping (DocumentSnapshot) -> Value?
) -> AsyncStream<Value> {
    resubscribingStream(operation) { yield, fail in
        let listener = reference.addSnapshotListener { snapshot, error in
            if let error { return fail(error) }
            guard let snapshot, let value = decode(snapshot) else { return }
            yield(value)
        }
        return { listener.remove() }
    }
}

private func queryStream<Element>(
    _ operation: String,
    _ query: Query,
    decode: @escaping (QueryDocumentSnapshot) -> Element?
) -> AsyncStream<[Element]> {
    resubscribingStream(operation) { yield, fail in
        let listener = query.addSnapshotListener { snapshot, error in
            if let error { return fail(error) }
            yield(snapshot?.documents.compactMap(decode) ?? [])
        }
        return { listener.remove() }
    }
}

private func decoded<T: Decodable>(_ type: T.Type, _ document: DocumentSnapshot, _ operation: String) -> T? {
    do { return try document.data(as: type) } catch {
        logger.error("\(operation): failed to decode \(document.documentID): \(String(describing: error))")
        return nil
    }
}

private func submissionStream(_ operation: String, _ query: Query) -> AsyncStream<[ChallengeSubmission]> {
    queryStream(operation, query) { doc in
        guard var submission = decoded(ChallengeSubmission.self, doc, operation) else { return nil }
        if submission.firestoreId?.isEmpty ?? true { submission.firestoreId = doc.documentID }
        return submission
    }
}

private func valueStream<Value>(
    _ operation: String,
    path: String,
    whenDenied: Value,
    decode: @escaping (DataSnapshot) -> Value
) -> AsyncStream<Value> {
    resubscribingStream(operation, cap: presenceRetryCap, whenDenied: whenDenied) { yield, fail in
        let ref = Database.database().reference(withPath: path)
        let handle = ref.observe(.value) { snapshot in yield(decode(snapshot)) } withCancel: { error in fail(error) }
        return { ref.removeObserver(withHandle: handle) }
    }
}

extension ApiClient: DependencyKey {
    static let liveValue = ApiClient(
        findActiveGame: { userId in
            let games = await fetchGames(try await membershipGameIds(userId))
            let candidates = games.compactMap { game in roleOf(game, userId: userId).map { (game, $0) } }
            return selectActiveGame(candidates, now: .now)
        },
        submitFoundCode: { gameId, foundCode, hunterName in
            let payload: [String: Any]?
            do {
                payload = try await callFunction("submitFoundCode", ["gameId": gameId, "foundCode": foundCode, "hunterName": hunterName])
            } catch let error as ApiError where error.code == .tooManyAttempts {
                throw SubmitFoundCodeError.cooldown(until: error.lockedUntil)
            }
            if let rejection = parseSubmitFoundCode(payload) { throw rejection }
        },
        getFoundCode: { gameId in
            (try await callFunction("getFoundCode", ["gameId": gameId])?["foundCode"] as? String) ?? ""
        },
        fetchZoneSchedule: { gameId in
            let doc = try await games().document(gameId).collection("zone").document("schedule").getDocument()
            return decodeZoneCircles(doc.data()?["circles"])
        },
        findGameByCode: { code in
            let normalized = code.trimmingCharacters(in: .whitespaces).uppercased()
            let index = try await Firestore.firestore().collection(Paths.gameCodes).document(normalized).getDocument()
            if let gameId = index.data()?["gameId"] as? String {
                return try await fetchGame(gameId)
            }
            // Games created before the code index existed.
            let snapshot = try await games().whereField("gameCode", isEqualTo: normalized).limit(to: 1).getDocuments()
            return snapshot.documents.first.flatMap { decoded(Game.self, $0, "findGameByCode") }
        },
        joinGame: { gameId, teamName in
            _ = try await callFunction("joinGame", ["gameId": gameId, "teamName": teamName])
        },
        leaveGame: { gameId in
            _ = try await callFunction("leaveGame", ["gameId": gameId])
        },
        updateGameStatus: { gameId, status in
            try await withRetry("updateGameStatus(\(gameId))") {
                try await games().document(gameId).updateData(["status": status.rawValue])
            }
        },
        chickenLocationStream: { gameId in
            valueStream(
                "Chicken location (\(gameId))",
                path: "\(Paths.games)/\(gameId)/\(Paths.chickenLocations)/latest",
                whenDenied: nil
            ) { snapshot in snapshot.exists() ? ChickenLocation(rtdb: snapshot.value) : nil }
        },
        gameConfigStream: { gameId in
            documentStream("Game config (\(gameId))", games().document(gameId)) { snapshot -> Game?? in
                guard snapshot.exists else { return .some(nil) }
                return decoded(Game.self, snapshot, "Game config").map { .some($0) }
            }
        },
        hunterLocationsStream: { gameId in
            valueStream(
                "Hunter locations (\(gameId))",
                path: "\(Paths.games)/\(gameId)/\(Paths.hunterLocations)",
                whenDenied: []
            ) { snapshot in
                snapshot.children.compactMap { child in
                    (child as? DataSnapshot).flatMap { HunterLocation(hunterId: $0.key, rtdb: $0.value) }
                }
            }
        },
        setChickenLocation: { gameId, coordinate, invisible in
            Database.database()
                .reference(withPath: "\(Paths.games)/\(gameId)/\(Paths.chickenLocations)/latest")
                .setValue([
                    "lat": coordinate.latitude,
                    "lng": coordinate.longitude,
                    "ts": ServerValue.timestamp(),
                    "invisible": invisible,
                ]) { error, _ in
                    if let error { logger.warning("[presence] chicken location write failed: \(error.localizedDescription)") }
                }
        },
        setConfig: { newGame in
            try await withRetry("setConfig(\(newGame.id))") {
                var data = try Firestore.Encoder().encode(newGame)
                data["gameCode"] = newGame.gameCode
                try await games().document(newGame.id).setData(data)
            }
        },
        setHunterLocation: { gameId, hunterId, coordinate in
            guard !gameId.isEmpty, !hunterId.isEmpty else { return }
            Database.database()
                .reference(withPath: "\(Paths.games)/\(gameId)/\(Paths.hunterLocations)/\(hunterId)")
                .setValue([
                    "lat": coordinate.latitude,
                    "lng": coordinate.longitude,
                    "ts": ServerValue.timestamp(),
                ]) { error, _ in
                    if let error { logger.warning("[presence] hunter location write failed: \(error.localizedDescription)") }
                }
        },
        collectPowerUp: { gameId, powerUpId, coordinate in
            _ = try await callFunction("collectPowerUp", [
                "gameId": gameId,
                "powerUpId": powerUpId,
                "lat": coordinate.latitude,
                "lng": coordinate.longitude,
            ])
        },
        activatePowerUp: { gameId, powerUpId in
            _ = try await callFunction("activatePowerUp", ["gameId": gameId, "powerUpId": powerUpId])
        },
        powerUpsStream: { gameId in
            queryStream("Power-ups (\(gameId))", games().document(gameId).collection(Paths.powerUps)) { doc in
                var data = doc.data()
                data["id"] = doc.documentID
                do { return try Firestore.Decoder().decode(PowerUp.self, from: data) } catch {
                    logger.error("Failed to decode power-up \(doc.documentID): \(String(describing: error))")
                    return nil
                }
            }
        },
        updateHeartbeat: { gameId in
            try await withRetry("updateHeartbeat(\(gameId))") {
                let ref = Database.database().reference(withPath: "\(Paths.games)/\(gameId)/presence/chicken")
                try await ref.onDisconnectSetValue(["online": false, "ts": ServerValue.timestamp()])
                try await ref.setValue(["online": true, "ts": ServerValue.timestamp()])
            }
        },
        fetchMyGames: { userId in
            async let created = games().whereField("creatorId", isEqualTo: userId).limit(to: 30).getDocuments()
            async let joinedIds = membershipGameIds(userId, limit: 30)
            var result: [MyGame] = []
            var seen = Set<String>()
            for doc in try await created.documents {
                guard let game = decoded(Game.self, doc, "fetchMyGames"), seen.insert(game.id).inserted else { continue }
                result.append(MyGame(game: game, role: .chicken))
            }
            for game in await fetchGames(try await joinedIds.subtracting(seen)) where seen.insert(game.id).inserted {
                result.append(MyGame(game: game, role: roleOf(game, userId: userId) ?? .hunter))
            }
            return Array(result.sorted { $0.game.startDate > $1.game.startDate }.prefix(20))
        },
        findRegistration: { gameId, userId in
            guard !gameId.isEmpty, !userId.isEmpty else { return nil }
            let snapshot = try await games().document(gameId).collection(Paths.players).document(userId).getDocument()
            guard snapshot.exists else { return nil }
            return try snapshot.data(as: Registration.self)
        },
        fetchAllRegistrations: { gameId in
            guard !gameId.isEmpty else { return [] }
            let snapshot = try await games().document(gameId).collection(Paths.players).getDocuments()
            return snapshot.documents.compactMap { decoded(Registration.self, $0, "fetchAllRegistrations") }
        },
        registrationsStream: { gameId in
            queryStream("Players (\(gameId))", games().document(gameId).collection(Paths.players)) { doc in
                decoded(Registration.self, doc, "Players")
            }
        },
        challengesStream: { gameId in
            queryStream("Challenges (\(gameId))", games().document(gameId).collection(Paths.challenges)) { doc in
                guard var challenge = decoded(Challenge.self, doc, "Challenges") else { return nil }
                challenge.firestoreId = doc.documentID
                return challenge
            }
        },
        leaderboardStream: { gameId in
            documentStream("Leaderboard (\(gameId))", games().document(gameId).collection("aggregates").document("leaderboard")) { snapshot in
                decodeLeaderboard(snapshot.data()?["entries"])
            }
        },
        myCompletionStream: { gameId, hunterId in
            documentStream(
                "My completion (\(gameId))",
                games().document(gameId).collection(Paths.challengeCompletions).document(hunterId)
            ) { snapshot -> ChallengeCompletion?? in
                guard snapshot.exists else { return .some(nil) }
                return decoded(ChallengeCompletion.self, snapshot, "My completion").map { .some($0) }
            }
        },
        hunterSubmissionsStream: { gameId, hunterId in
            submissionStream(
                "Hunter submissions (\(gameId))",
                games().document(gameId).collection(Paths.challengeSubmissions).whereField("hunterId", isEqualTo: hunterId)
            )
        },
        pendingSubmissionsStream: { gameId in
            submissionStream(
                "Pending submissions (\(gameId))",
                games().document(gameId).collection(Paths.challengeSubmissions)
                    .whereField("status", isEqualTo: "pending")
                    .order(by: "submittedAt", descending: false)
            )
        },
        submitChallenge: { gameId, challengeId, hunterId, type, mediaData, mediaType in
            let submissionsRef = games().document(gameId).collection(Paths.challengeSubmissions)
            let existing = try await submissionsRef
                .whereField("hunterId", isEqualTo: hunterId)
                .whereField("challengeId", isEqualTo: challengeId)
                .limit(to: 10)
                .getDocuments()
                .documents
                .compactMap { decoded(ChallengeSubmission.self, $0, "submitChallenge") }
            if let rejection = blockingSubmission(existing, type: type) { throw rejection }
            let newDoc = submissionsRef.document()
            let isVideo = mediaType == .video
            let storageRef = Storage.storage().reference()
                .child("gameSubmissions/\(gameId)/\(newDoc.documentID).\(isVideo ? "mp4" : "jpg")")
            let metadata = StorageMetadata()
            metadata.contentType = isVideo ? "video/mp4" : "image/jpeg"
            _ = try await storageRef.putDataAsync(mediaData, metadata: metadata)
            let mediaUrl = try await storageRef.downloadURL().absoluteString
            let submission = ChallengeSubmission(
                firestoreId: newDoc.documentID,
                challengeId: challengeId,
                hunterId: hunterId,
                type: type,
                submittedAt: Timestamp(date: .now),
                mediaUrl: mediaUrl,
                mediaType: mediaType,
                status: .pending
            )
            try newDoc.setData(from: submission)
            return submission
        },
        validateChallengeSubmission: { gameId, submissionId, accept in
            _ = try await callFunction("validateChallengeSubmission", ["gameId": gameId, "submissionId": submissionId, "accept": accept])
        },
        applyOutOfZonePenalty: { gameId in
            _ = try await callFunction("applyOutOfZonePenalty", ["gameId": gameId])
        },
        reportPlayer: { gameId, reportedUserId, reportedNickname in
            guard let reporterId = Auth.auth().currentUser?.uid else { throw ApiError(code: .unauthenticated) }
            try await Firestore.firestore().collection(Paths.reports).addDocument(data: [
                "reporterId": reporterId,
                "reportedUserId": reportedUserId,
                "reportedNickname": String(reportedNickname.prefix(60)),
                "gameId": gameId,
                "createdAt": FieldValue.serverTimestamp(),
            ])
        },
        newGameId: {
            games().document().documentID
        },
        setGameMasterPassword: { gameId, password in
            _ = try await callFunction("setGameMasterPassword", ["gameId": gameId, "password": password])
        },
        joinAsGameMaster: { gameId, password in
            parseJoinAsGameMaster(try await callFunction("joinAsGameMaster", ["gameId": gameId, "password": password]))
        },
        designateChicken: { gameId, newChickenUid in
            _ = try await callFunction("designateChicken", ["gameId": gameId, "newChickenUid": newChickenUid])
        },
        validateRegistrationCode: { batchId, code in
            parseValidationCode(try await callFunction("validateRegistrationCode", ["batchId": batchId, "code": code]))
        },
        launchGame: { gameId in
            parseLaunchedAt(try await callFunction("launchGame", ["gameId": gameId]), fallback: .now)
        },
        debugAdvanceGame: { gameId, action in
            _ = try await callFunction("debugAdvanceGame", ["gameId": gameId, "action": action.rawValue])
        }
    )
}
