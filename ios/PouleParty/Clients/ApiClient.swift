//
//  ApiClient.swift
//  PouleParty
//
//  Created by Julien Rahier on 17/03/2024.
//

import ComposableArchitecture
import CoreLocation
import FirebaseAuth
import FirebaseDatabase
import FirebaseFirestore
import FirebaseStorage
import FirebaseFunctions
import os

/// Deterministic, total ordering for leaderboard rows. Parity with
/// Android: sort by `totalPoints` descending, then `teamName`
/// case-insensitive ascending, then `hunterId` ascending. Total so equal
/// teams never render in platform-dependent order.
func leaderboardOrdersBefore(_ lhs: ChallengeCompletion, _ rhs: ChallengeCompletion) -> Bool {
    if lhs.totalPoints != rhs.totalPoints {
        return lhs.totalPoints > rhs.totalPoints
    }
    let lName = lhs.teamName.lowercased()
    let rName = rhs.teamName.lowercased()
    if lName != rName {
        return lName < rName
    }
    return (lhs.hunterId ?? "") < (rhs.hunterId ?? "")
}

/// Phase of an active game, used by the Home banner to pick the right
/// copy + CTA. Games in both phases can legitimately coexist for a single
/// user (e.g. a hunter currently playing game A and registered to game B
/// for tomorrow), so `findActiveGame` returns the single most-relevant one.
enum GamePhase: Equatable {
    /// Game has already started (`status == .inProgress`) and the user is
    /// mid-play. Banner copy: "Partie en cours" + "Reprendre".
    case inProgress
    /// Game is scheduled but hasn't started yet (`status == .waiting`).
    /// Banner copy: "Prochaine partie" + "Préparer" (chicken) or
    /// "Rejoindre" (hunter).
    case upcoming
}

struct ApiClient {
    var findActiveGame: (String) async throws -> (Game, GameRole, GamePhase)?
    /// CRIT-3 (audit 2026-05-17): replaced the previous client-side
    /// `addWinner(gameId, Winner)` arrayUnion. The Cloud Function
    /// verifies caller-is-hunter + foundCode-matches inside a Firestore
    /// transaction before appending. Throws a `SubmitFoundCodeError`
    /// when the CF refuses (wrong code, not a hunter, already won,
    /// game not in progress).
    var submitFoundCode: (_ gameId: String, _ foundCode: String, _ hunterName: String) async throws -> Void
    /// CRIT-2 (audit 2026-05-17): fetches the game's 4-digit
    /// foundCode. The CF returns it only if the caller is `chickenId`
    /// — foundCode lives in `/games/{id}/private/security` (admin-SDK
    /// only) since V2.3 so hunters can't read it off the public doc
    /// and self-declare victory. Returns "" if no foundCode is set
    /// (e.g. pre-V2.3 game whose public field was never relocated).
    var getFoundCode: (_ gameId: String) async throws -> String
    var deleteConfig: (String) async throws -> Void
    var getConfig: (String) async throws -> Game?
    /// PP-zone-stored: fetch the immutable ordered circle schedule from
    /// `/games/{id}/zone/schedule` (written server-side by `onGameCreated`).
    /// Read once when the map mounts — the doc never changes. Empty on miss.
    var fetchZoneSchedule: (_ gameId: String) async throws -> [ZoneCircle]
    var findGameByCode: (String) async throws -> Game?
    /// PP-107: joins the caller as a hunter. The `joinGame` callable writes
    /// the role + the `/games/{id}/players/{uid}` team-name doc + the
    /// membership index server-side (admin SDK). Replaces the old client-side
    /// `hunterIds` arrayUnion + `/registrations` write — clients never write
    /// membership directly anymore.
    var joinGame: (_ gameId: String, _ teamName: String) async throws -> Void
    /// PP-107: removes the caller (hunter / GameMaster) from the game.
    var leaveGame: (_ gameId: String) async throws -> Void
    var updateGameStatus: (String, Game.GameStatus) async throws -> Void
    var chickenLocationStream: (String) -> AsyncStream<ChickenLocation?>
    var gameConfigStream: (String) -> AsyncStream<Game?>
    var hunterLocationsStream: (String) -> AsyncStream<[HunterLocation]>
    var setChickenLocation: (_ gameId: String, _ coordinate: CLLocationCoordinate2D, _ invisible: Bool) throws -> Void
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
    var updateHeartbeat: (String) async throws -> Void
    var fetchMyGames: (String) async throws -> [MyGame]
    var findRegistration: (String, String) async throws -> Registration?
    var fetchAllRegistrations: (String) async throws -> [Registration]
    /// Live stream of every `/games/{gameId}/players/*` doc (PP-107, renamed
    /// from `/registrations`). Used by the GameMaster map so the hunter count +
    /// drawer team names refresh the instant a new hunter joins, instead of
    /// staying frozen on the load-time snapshot.
    var registrationsStream: (String) -> AsyncStream<[Registration]>
    var challengesStream: (_ gameId: String) -> AsyncStream<[Challenge]>
    /// Live challenge leaderboard, read from the single `aggregates/leaderboard`
    /// doc (PP-103) instead of streaming the whole challengeCompletions
    /// collection. Entries carry only `hunterId` / `teamName` / `totalPoints`.
    var leaderboardStream: (String) -> AsyncStream<[ChallengeCompletion]>
    /// The current hunter's OWN completion doc (validatedChallengeIds etc.),
    /// a single-doc listener for their live "validated" checkmarks (PP-103).
    var myCompletionStream: (_ gameId: String, _ hunterId: String) -> AsyncStream<ChallengeCompletion?>
    var hunterSubmissionsStream: (_ gameId: String, _ hunterId: String) -> AsyncStream<[ChallengeSubmission]>
    var pendingSubmissionsStream: (_ gameId: String) -> AsyncStream<[ChallengeSubmission]>
    var submitChallenge: (_ gameId: String, _ challengeId: String, _ hunterId: String, _ type: Challenge.ChallengeType, _ mediaData: Data, _ mediaType: ChallengeSubmission.MediaType) async throws -> ChallengeSubmission
    var validateChallengeSubmission: (_ gameId: String, _ submissionId: String, _ accept: Bool) async throws -> Void
    var decrementTotalPoints: (_ gameId: String, _ hunterId: String) async throws -> Void
    /// Submit a report against another player (user-generated-content moderation).
    /// Writes a doc to `/reports/{autoId}` which is readable only by the admin SDK.
    var reportPlayer: (_ gameId: String, _ reportedUserId: String, _ reportedNickname: String) async throws -> Void
    /// Generate a new Firestore-style auto-ID (20-char alphanumeric) for a
    /// brand-new game doc.
    var newGameId: () -> String
    // MARK: - GameMaster (PP-70)
    /// Sets / replaces the 4-digit GameMaster password on a game.
    /// Only the creator can call this; the CF flips
    /// `Game.hasGameMasterPassword` to `true` and stores the password in
    /// `/games/{gameId}/private/security` (admin-SDK only).
    var setGameMasterPassword: (_ gameId: String, _ password: String) async throws -> Void
    /// Clears the GameMaster password. Already-joined GMs in
    /// `gameMasterIds` stay.
    var clearGameMasterPassword: (_ gameId: String) async throws -> Void
    /// Attempts to join as a GameMaster. Rate-limited (5 tries → 5 min
    /// lock). Returns `attemptsRemaining` for wrong passwords and a
    /// `lockedUntilMs` when the lock kicked in.
    var joinAsGameMaster: (_ gameId: String, _ password: String) async throws -> JoinAsGameMasterResult
    /// PP-86 / PP-107: GameMaster (or creator as fallback) designates a hunter
    /// as the new chicken. Routed through the `designateChicken` callable,
    /// which atomically moves the old chicken to `hunter` and the new uid to
    /// `chicken` in the server-owned `roles` map (clients never write roles).
    var designateChicken: (_ gameId: String, _ newChickenUid: String) async throws -> Void
    /// PP-52: validates + single-use-claims a paid-event registration code.
    /// Called from JoinFlow only when the resolved game has a `registrationBatchId`.
    var validateRegistrationCode: (_ batchId: String, _ code: String) async throws -> ValidationCodeResult
    // MARK: - Zone configuration (PP-69)
    /// Fetches the server-computed zone configuration for the wizard
    /// recap step (PP-13 / PP-14 Phase 2). The Cloud Function is the
    /// single source of truth for the initial radius, drift seed and
    /// shrink schedule — once this wrapper is wired into the wizard,
    /// the client-side `computeZoneRadius` / `interpolateZoneCenter` /
    /// `deterministicDriftCenter` helpers can be deleted (handled by
    /// the PP-13 Phase 2 / PP-14 Phase 2 tickets, NOT this one).
    var computeZoneConfiguration: (_ input: ComputeZoneConfigurationInput) async throws -> ComputeZoneConfigurationOutput
    // MARK: - Manual launch (PP-71)
    /// Promotes a `readyToLaunch` game to `inProgress`, stamps
    /// `timing.actualStart` on the server, recomputes `timing.end`,
    /// and enqueues the runtime Cloud Tasks deferred at creation.
    /// Returns the actual start timestamp so the caller can advance
    /// its local countdown without waiting for the Firestore stream.
    var launchGame: (_ gameId: String) async throws -> Date
    /// QA-only (debug games): force-advance a phase via the
    /// `debugAdvanceGame` callable. `action` is `"endNow"` or
    /// `"spawnPowerUp"`. The callable refuses non-debug games server-side.
    /// Defaulted to a no-op so existing literal `ApiClient(...)`
    /// constructions (demo mode, previews) don't have to list it.
    var debugAdvanceGame: (_ gameId: String, _ action: String) async throws -> Void = { _, _ in }
}

/// CRIT-3 (audit 2026-05-17): rejection reason from the `submitFoundCode`
/// Cloud Function. Used by HunterMap to render the right alert copy.
enum SubmitFoundCodeError: Error, Equatable {
    case invalidCode
    case notAHunter
    case alreadyWinner
    case gameNotInProgress
    case malformedResponse
}

struct JoinAsGameMasterResult: Equatable {
    let success: Bool
    let attemptsRemaining: Int
    let lockedUntilMs: Int?
}

/// PP-52: outcome of `validateRegistrationCode`. Mirrors the server-side
/// `{ status }` discriminated result and the Android `ValidationCodeResult`.
enum ValidationCodeResult: Equatable {
    case valid
    case invalid
    case alreadyUsed
}

// MARK: - PP-69 Zone configuration callable types

struct ComputeZoneConfigurationInput: Equatable {
    struct LatLng: Equatable {
        let lat: Double
        let lng: Double
    }
    let startPoint: LatLng
    let finalPoint: LatLng?
    let gameMode: Game.GameMode
    /// 500 / 1000 / 2000 m. Required in `followTheChicken`, ignored in
    /// `stayInTheZone` (the radius is computed from the two pins).
    let radiusHint: Double?
    let gameDurationMinutes: Double
    /// `true` from the Shuffle button (PP-14 Phase 2) — forces a new
    /// random seed on the server. `false` (default) yields a
    /// deterministic seed derived from the inputs.
    let forceNewSeed: Bool
    /// When set, pins the resulting `driftSeed` to this value (useful
    /// for re-fetching a previously created game's configuration).
    let existingSeed: Int?

    init(
        startPoint: LatLng,
        finalPoint: LatLng?,
        gameMode: Game.GameMode,
        radiusHint: Double?,
        gameDurationMinutes: Double,
        forceNewSeed: Bool = false,
        existingSeed: Int? = nil
    ) {
        self.startPoint = startPoint
        self.finalPoint = finalPoint
        self.gameMode = gameMode
        self.radiusHint = radiusHint
        self.gameDurationMinutes = gameDurationMinutes
        self.forceNewSeed = forceNewSeed
        self.existingSeed = existingSeed
    }
}

struct ComputeZoneConfigurationOutput: Equatable {
    struct LatLng: Equatable {
        let lat: Double
        let lng: Double
    }
    struct Circle: Equatable {
        let radiusMeters: Double
        let center: LatLng
    }
    let initialRadius: Double
    let validatedFinal: LatLng?
    let driftSeed: Int
    let finalZoneRadius: Double
    let interiorMargin: Double
    let shrinkIntervalMinutes: Double
    let shrinkMetersPerUpdate: Double
    let circles: [Circle]
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
// PP-107: the per-game team-name subcollection was renamed `players`
// server-side. Writes happen via the `joinGame` callable now; clients only read.
private let playersSubcollection = "players"
// PP-107: `/users/{uid}/memberships/{gameId}` (`{ gameId, role }`) is the
// per-user membership index written server-side by the role callables. Read by
// `findActiveGame` / `fetchMyGames` instead of array-contains game queries.
private let usersCollection = "users"
private let membershipsSubcollection = "memberships"
private let challengesCollection = "challenges"
private let challengeCompletionsSubcollection = "challengeCompletions"
private let challengeSubmissionsSubcollection = "challengeSubmissions"
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
        findActiveGame: { _ in nil as (Game, GameRole, GamePhase)? },
        submitFoundCode: { _, _, _ in },
        getFoundCode: { _ in "" },
        deleteConfig: { _ in },
        getConfig: { _ in nil },
        fetchZoneSchedule: { _ in [] },
        findGameByCode: { _ in nil },
        joinGame: { _, _ in },
        leaveGame: { _ in },
        updateGameStatus: { _, _ in },
        chickenLocationStream: { _ in AsyncStream { _ in } },
        gameConfigStream: { _ in AsyncStream { _ in } },
        hunterLocationsStream: { _ in AsyncStream { _ in } },
        setChickenLocation: { _, _, _ in },
        setConfig: { _ in },
        setHunterLocation: { _, _, _ in },
        collectPowerUp: { _, _, _ in },
        activatePowerUp: { _, _, _, _ in },
        powerUpsStream: { _ in AsyncStream { _ in } },
        updateHeartbeat: { _ in },
        fetchMyGames: { _ in [] },
        findRegistration: { _, _ in nil },
        fetchAllRegistrations: { _ in [] },
        registrationsStream: { _ in AsyncStream { _ in } },
        challengesStream: { _ in AsyncStream { _ in } },
        leaderboardStream: { _ in AsyncStream { _ in } },
        myCompletionStream: { _, _ in AsyncStream { _ in } },
        hunterSubmissionsStream: { _, _ in AsyncStream { _ in } },
        pendingSubmissionsStream: { _ in AsyncStream { _ in } },
        submitChallenge: { _, _, _, _, _, _ in ChallengeSubmission() },
        validateChallengeSubmission: { _, _, _ in },
        decrementTotalPoints: { _, _ in },
        reportPlayer: { _, _, _ in },
        newGameId: { "test-game-id" },
        setGameMasterPassword: { _, _ in },
        clearGameMasterPassword: { _ in },
        joinAsGameMaster: { _, _ in JoinAsGameMasterResult(success: true, attemptsRemaining: 5, lockedUntilMs: nil) },
        designateChicken: { _, _ in },
        validateRegistrationCode: { _, _ in .valid },
        computeZoneConfiguration: { _ in
            ComputeZoneConfigurationOutput(
                initialRadius: 1000,
                validatedFinal: nil,
                driftSeed: 1,
                finalZoneRadius: 50,
                interiorMargin: 200,
                shrinkIntervalMinutes: 5,
                shrinkMetersPerUpdate: 75,
                circles: []
            )
        },
        launchGame: { _ in Date() },
        debugAdvanceGame: { _, _ in }
    )
}

extension ApiClient: DependencyKey {
    static var liveValue = ApiClient(
        findActiveGame: { userId in
            let db = Firestore.firestore()
            var candidates: [(Game, GameRole)] = []

            // PP-107: membership now lives in `/users/{uid}/memberships`
            // (one doc per game the user belongs to, `{ gameId, role }`),
            // written server-side by the role callables. The old three
            // parallel array-contains queries on `hunterIds` / `chickenId` /
            // `gameMasterIds` are gone — those fields no longer exist on the
            // game doc.
            do {
                let membershipSnapshot = try await db.collection(usersCollection)
                    .document(userId)
                    .collection(membershipsSubcollection)
                    .getDocuments()

                let gameIds: [String] = membershipSnapshot.documents.compactMap { doc in
                    (doc.data()["gameId"] as? String) ?? (doc.documentID.isEmpty ? nil : doc.documentID)
                }

                try await withThrowingTaskGroup(of: Game?.self) { group in
                    for gameId in Set(gameIds) {
                        group.addTask {
                            let snap = try? await db.collection(gamesCollection).document(gameId).getDocument()
                            return try? snap?.data(as: Game.self)
                        }
                    }
                    for try await game in group {
                        guard let game else { continue }
                        // Resolve the role from the authoritative `roles` map.
                        // A membership with no matching role (stale doc, game
                        // left) is dropped.
                        let role: GameRole
                        if game.isChicken(userId) {
                            role = .chicken
                        } else if game.isGameMaster(userId) {
                            role = .gameMaster
                        } else if game.isHunter(userId) {
                            role = .hunter
                        } else {
                            continue
                        }
                        candidates.append((game, role))
                    }
                }
            } catch {
                logger.error("findActiveGame memberships query failed: \(error.localizedDescription)")
            }

            // Filter out games whose end time has already passed (status may
            // not have been updated to DONE yet due to network/Cloud Task lag)
            let stillActive = candidates.filter { $0.0.endDate > .now }

            // Priority 1: a game already in progress — that's the most urgent
            // one to surface. If the user has several, pick the most recently
            // started (they probably launched it last).
            let inProgress = stillActive.filter { $0.0.status == .inProgress }
            if let (game, role) = inProgress.max(by: { $0.0.startDate < $1.0.startDate }) {
                return (game, role, .inProgress)
            }

            // Priority 2: a waiting game with a future start date — surface
            // the one that will start the soonest so the user sees the most
            // actionable item. Games with `startDate < now` but still
            // `.waiting` (transition Cloud Task late) are filtered out because
            // they'll flip to inProgress within seconds and the banner would
            // already tell the user "Prochaine partie" confusingly.
            let upcoming = stillActive.filter {
                $0.0.status == .waiting && $0.0.startDate > .now
            }
            if let (game, role) = upcoming.min(by: { $0.0.startDate < $1.0.startDate }) {
                return (game, role, .upcoming)
            }
            return nil
        },
        submitFoundCode: { gameId, foundCode, hunterName in
            // CRIT-3 (audit 2026-05-17): routed via callable CF. The
            // CF re-verifies caller-is-hunter + foundCode-matches
            // inside a Firestore transaction before appending the
            // winner. firestore.rules denies all client writes to
            // `winners`, so this is now the only path.
            let functions = Functions.functions(region: "europe-west1")
            let result = try await functions
                .httpsCallable("submitFoundCode")
                .call([
                    "gameId": gameId,
                    "foundCode": foundCode,
                    "hunterName": hunterName,
                ])
            guard let dict = result.data as? [String: Any],
                  let success = dict["success"] as? Bool
            else {
                throw SubmitFoundCodeError.malformedResponse
            }
            if success { return }
            let reason = (dict["reason"] as? String) ?? ""
            switch reason {
            case "invalidCode": throw SubmitFoundCodeError.invalidCode
            case "notAHunter": throw SubmitFoundCodeError.notAHunter
            case "alreadyWinner": throw SubmitFoundCodeError.alreadyWinner
            case "gameNotInProgress": throw SubmitFoundCodeError.gameNotInProgress
            default: throw SubmitFoundCodeError.malformedResponse
            }
        },
        getFoundCode: { gameId in
            // CRIT-2 (audit 2026-05-17): foundCode lives in
            // /private/security since V2.3. The chicken fetches it via
            // this CF; the CF refuses for non-chicken callers.
            let functions = Functions.functions(region: "europe-west1")
            let result = try await functions
                .httpsCallable("getFoundCode")
                .call(["gameId": gameId])
            guard let dict = result.data as? [String: Any],
                  let code = dict["foundCode"] as? String
            else {
                throw NSError(
                    domain: "ApiClient",
                    code: -1,
                    userInfo: [NSLocalizedDescriptionKey: "getFoundCode: malformed response"]
                )
            }
            return code
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
        fetchZoneSchedule: { gameId in
            do {
                let doc = try await Firestore.firestore()
                    .collection(gamesCollection).document(gameId)
                    .collection("zone").document("schedule")
                    .getDocument()
                guard let raw = doc.data()?["circles"] as? [[String: Any]] else { return [] }
                return raw.compactMap { m -> ZoneCircle? in
                    guard let radius = (m["radiusMeters"] as? NSNumber)?.doubleValue,
                          let lat = (m["lat"] as? NSNumber)?.doubleValue,
                          let lng = (m["lng"] as? NSNumber)?.doubleValue
                    else { return nil }
                    let order = (m["order"] as? NSNumber)?.intValue ?? 0
                    return ZoneCircle(order: order, radiusMeters: radius, lat: lat, lng: lng)
                }.sorted { $0.order < $1.order }
            } catch {
                logger.error("Failed to fetch zone schedule \(gameId): \(error.localizedDescription)")
                return []
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
        joinGame: { gameId, teamName in
            guard !gameId.isEmpty else {
                logger.warning("joinGame skipped — empty gameId")
                return
            }
            // PP-107: the server writes the role + the `/players/{uid}`
            // team-name doc + the membership index in one atomic step.
            let functions = Functions.functions(region: "europe-west1")
            _ = try await functions
                .httpsCallable("joinGame")
                .call(["gameId": gameId, "teamName": teamName])
        },
        leaveGame: { gameId in
            guard !gameId.isEmpty else {
                logger.warning("leaveGame skipped — empty gameId")
                return
            }
            let functions = Functions.functions(region: "europe-west1")
            _ = try await functions
                .httpsCallable("leaveGame")
                .call(["gameId": gameId])
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
                let ref = Database.database()
                    .reference(withPath: "\(gamesCollection)/\(gameId)/\(chickenLocationsSubcollection)/latest")
                let handle = ref.observe(.value) { snapshot in
                    guard snapshot.exists(), let chickenLocation = ChickenLocation(rtdb: snapshot.value) else {
                        continuation.yield(nil)
                        return
                    }
                    continuation.yield(chickenLocation)
                } withCancel: { error in
                    logListenerError("Chicken location (game \(gameId))", error)
                    continuation.yield(nil)
                }

                continuation.onTermination = { _ in
                    ref.removeObserver(withHandle: handle)
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
                let ref = Database.database()
                    .reference(withPath: "\(gamesCollection)/\(gameId)/\(hunterLocationsSubcollection)")
                let handle = ref.observe(.value) { snapshot in
                    var hunters: [HunterLocation] = []
                    for case let child as DataSnapshot in snapshot.children {
                        if let hunter = HunterLocation(hunterId: child.key, rtdb: child.value) {
                            hunters.append(hunter)
                        }
                    }
                    continuation.yield(hunters)
                } withCancel: { error in
                    logListenerError("Hunter locations (game \(gameId))", error)
                    continuation.yield([])
                }

                continuation.onTermination = { _ in
                    ref.removeObserver(withHandle: handle)
                }
            }
        },
        setChickenLocation: { gameId, coordinate, invisible in
            // PP-102: fire-and-forget RTDB write. `ts` is a server timestamp;
            // the `invisible` flag (PP-87) rides along and gates hunter reads.
            Database.database()
                .reference(withPath: "\(gamesCollection)/\(gameId)/\(chickenLocationsSubcollection)/latest")
                .setValue([
                    "lat": coordinate.latitude,
                    "lng": coordinate.longitude,
                    "ts": ServerValue.timestamp(),
                    "invisible": invisible,
                ])
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
            // PP-102: fire-and-forget RTDB write. The hunterId is the RTDB key.
            Database.database()
                .reference(withPath: "\(gamesCollection)/\(gameId)/\(hunterLocationsSubcollection)/\(hunterId)")
                .setValue([
                    "lat": coordinate.latitude,
                    "lng": coordinate.longitude,
                    "ts": ServerValue.timestamp(),
                ])
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
        activatePowerUp: { gameId, powerUpId, _, _ in
            // Trailing `activeEffectField` / `expiresAt` are ignored —
            // duration is server-authoritative via the callable.
            try await withRetry("activatePowerUp(\(gameId), \(powerUpId))") {
                let functions = Functions.functions(region: "europe-west1")
                _ = try await functions
                    .httpsCallable("activatePowerUp")
                    .call([
                        "gameId": gameId,
                        "powerUpId": powerUpId,
                    ])
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
                            catch {
                                logger.error("Failed to decode PowerUp \(doc.documentID): \(String(describing: error))")
                                return nil
                            }
                        }
                        continuation.yield(powerUps)
                    }

                continuation.onTermination = { _ in
                    listener.remove()
                }
            }
        },
        updateHeartbeat: { gameId in
            // PP-102: presence moved to RTDB. Mark the chicken online and arm
            // an onDisconnect so a dropped connection flips it offline
            // server-side immediately, instead of waiting on a stale timeout.
            // Re-armed each tick for resilience; retry keeps a transient
            // failure from making the chicken look offline.
            try await withRetry("updateHeartbeat(\(gameId))") {
                let ref = Database.database()
                    .reference(withPath: "\(gamesCollection)/\(gameId)/presence/chicken")
                try await ref.onDisconnectSetValue([
                    "online": false,
                    "ts": ServerValue.timestamp(),
                ])
                try await ref.setValue([
                    "online": true,
                    "ts": ServerValue.timestamp(),
                ])
            }
        },
        fetchMyGames: { userId in
            let db = Firestore.firestore()
            // Games I created (still a top-level `creatorId` field) + games I
            // belong to via the PP-107 `/users/{uid}/memberships` index. The
            // `hunterIds` array query is gone — that field no longer exists.
            async let createdSnapshot = db
                .collection(gamesCollection)
                .whereField("creatorId", isEqualTo: userId)
                .limit(to: 30)
                .getDocuments()

            async let membershipSnapshot = db
                .collection(usersCollection)
                .document(userId)
                .collection(membershipsSubcollection)
                .limit(to: 30)
                .getDocuments()

            let (created, memberships) = try await (createdSnapshot, membershipSnapshot)

            var result: [MyGame] = []
            var seenIds = Set<String>()

            for doc in created.documents {
                guard let game = try? doc.data(as: Game.self) else { continue }
                if seenIds.insert(game.id).inserted {
                    result.append(MyGame(game: game, role: .chicken))
                }
            }

            // Fetch each membership's game doc and tag it with the live role.
            let membershipGameIds: [String] = memberships.documents.compactMap { doc in
                (doc.data()["gameId"] as? String) ?? (doc.documentID.isEmpty ? nil : doc.documentID)
            }
            try await withThrowingTaskGroup(of: Game?.self) { group in
                for gameId in Set(membershipGameIds) where !seenIds.contains(gameId) {
                    group.addTask {
                        let snap = try? await db.collection(gamesCollection).document(gameId).getDocument()
                        return try? snap?.data(as: Game.self)
                    }
                }
                for try await game in group {
                    guard let game else { continue }
                    let role: GameRole
                    if game.isChicken(userId) {
                        role = .chicken
                    } else if game.isGameMaster(userId) {
                        role = .gameMaster
                    } else {
                        role = .hunter
                    }
                    // Creator takes precedence if the same user appears twice.
                    if seenIds.insert(game.id).inserted {
                        result.append(MyGame(game: game, role: role))
                    }
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
                .collection(playersSubcollection).document(userId)
                .getDocument()
            guard snapshot.exists else { return nil }
            do {
                return try snapshot.data(as: Registration.self)
            } catch {
                logger.error("Failed to decode registration \(gameId)/\(userId): \(error.localizedDescription)")
                return nil
            }
        },
        fetchAllRegistrations: { gameId in
            guard !gameId.isEmpty else { return [] }
            let snapshot = try await Firestore.firestore()
                .collection(gamesCollection).document(gameId)
                .collection(playersSubcollection)
                .getDocuments()
            return snapshot.documents.compactMap { doc in
                try? doc.data(as: Registration.self)
            }
        },
        registrationsStream: { gameId in
            AsyncStream { continuation in
                let listener = Firestore.firestore()
                    .collection(gamesCollection).document(gameId)
                    .collection(playersSubcollection)
                    .addSnapshotListener { snapshot, error in
                        if let error {
                            logListenerError("Registrations (game \(gameId))", error)
                        }
                        guard let documents = snapshot?.documents else {
                            continuation.yield([])
                            return
                        }
                        let regs = documents.compactMap { doc -> Registration? in
                            do { return try doc.data(as: Registration.self) }
                            catch {
                                logger.error("Failed to decode Registration \(doc.documentID): \(String(describing: error))")
                                return nil
                            }
                        }
                        continuation.yield(regs)
                    }

                continuation.onTermination = { _ in
                    listener.remove()
                }
            }
        },
        challengesStream: { gameId in
            AsyncStream { continuation in
                let listener = Firestore.firestore()
                    .collection(gamesCollection).document(gameId)
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
                            do {
                                var challenge = try doc.data(as: Challenge.self)
                                challenge.firestoreId = doc.documentID
                                return challenge
                            } catch {
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
        leaderboardStream: { gameId in
            AsyncStream { continuation in
                let listener = Firestore.firestore()
                    .collection(gamesCollection).document(gameId)
                    .collection("aggregates").document("leaderboard")
                    .addSnapshotListener { snapshot, error in
                        if let error {
                            logListenerError("Leaderboard (game \(gameId))", error)
                        }
                        let entries = snapshot?.data()?["entries"] as? [String: [String: Any]] ?? [:]
                        let completions = entries
                            .map { hunterId, entry -> ChallengeCompletion in
                                var completion = ChallengeCompletion()
                                completion.hunterId = hunterId
                                completion.totalPoints = (entry["totalPoints"] as? Int) ?? 0
                                completion.teamName = (entry["teamName"] as? String) ?? ""
                                return completion
                            }
                            .sorted(by: leaderboardOrdersBefore)
                        continuation.yield(completions)
                    }

                continuation.onTermination = { _ in
                    listener.remove()
                }
            }
        },
        myCompletionStream: { gameId, hunterId in
            AsyncStream { continuation in
                let listener = Firestore.firestore()
                    .collection(gamesCollection).document(gameId)
                    .collection(challengeCompletionsSubcollection).document(hunterId)
                    .addSnapshotListener { snapshot, error in
                        if let error {
                            logListenerError("My completion (game \(gameId))", error)
                        }
                        guard let snapshot, snapshot.exists else {
                            continuation.yield(nil)
                            return
                        }
                        let completion: ChallengeCompletion? = {
                            do { return try snapshot.data(as: ChallengeCompletion.self) }
                            catch {
                                logger.error("Failed to decode my ChallengeCompletion: \(String(describing: error))")
                                return nil
                            }
                        }()
                        continuation.yield(completion)
                    }

                continuation.onTermination = { _ in
                    listener.remove()
                }
            }
        },
        hunterSubmissionsStream: { gameId, hunterId in
            AsyncStream { continuation in
                let listener = Firestore.firestore()
                    .collection(gamesCollection).document(gameId)
                    .collection(challengeSubmissionsSubcollection)
                    .whereField("hunterId", isEqualTo: hunterId)
                    .addSnapshotListener { snapshot, error in
                        if let error {
                            logListenerError("Challenge submissions (game \(gameId), hunter \(hunterId))", error)
                        }
                        guard let documents = snapshot?.documents else {
                            continuation.yield([])
                            return
                        }
                        let submissions = documents.compactMap { doc -> ChallengeSubmission? in
                            do {
                                var sub = try doc.data(as: ChallengeSubmission.self)
                                // `@DocumentID` injection is bypassed by our
                                // custom `init(from:)`. Set the id explicitly
                                // from the snapshot path so the validator
                                // queue (and any consumer that calls the
                                // `validateChallengeSubmission` callable with
                                // `submission.firestoreId`) doesn't no-op.
                                if sub.firestoreId == nil || sub.firestoreId?.isEmpty == true {
                                    sub.firestoreId = doc.documentID
                                }
                                return sub
                            } catch {
                                logger.error("Failed to decode ChallengeSubmission \(doc.documentID): \(error.localizedDescription)")
                                return nil
                            }
                        }
                        continuation.yield(submissions)
                    }

                continuation.onTermination = { _ in
                    listener.remove()
                }
            }
        },
        pendingSubmissionsStream: { gameId in
            AsyncStream { continuation in
                let listener = Firestore.firestore()
                    .collection(gamesCollection).document(gameId)
                    .collection(challengeSubmissionsSubcollection)
                    .whereField("status", isEqualTo: "pending")
                    .order(by: "submittedAt", descending: false)
                    .addSnapshotListener { snapshot, error in
                        if let error {
                            logListenerError("Pending submissions (game \(gameId))", error)
                        }
                        guard let documents = snapshot?.documents else {
                            continuation.yield([])
                            return
                        }
                        let subs = documents.compactMap { doc -> ChallengeSubmission? in
                            do {
                                var sub = try doc.data(as: ChallengeSubmission.self)
                                if sub.firestoreId == nil || sub.firestoreId?.isEmpty == true {
                                    sub.firestoreId = doc.documentID
                                }
                                return sub
                            } catch {
                                logger.error("Failed to decode ChallengeSubmission \(doc.documentID): \(error.localizedDescription)")
                                return nil
                            }
                        }
                        continuation.yield(subs)
                    }
                continuation.onTermination = { _ in listener.remove() }
            }
        },
        submitChallenge: { gameId, challengeId, hunterId, type, mediaData, mediaType in
            let db = Firestore.firestore()
            let submissionsRef = db.collection(gamesCollection).document(gameId)
                .collection(challengeSubmissionsSubcollection)
            let existing = try await submissionsRef
                .whereField("hunterId", isEqualTo: hunterId)
                .whereField("challengeId", isEqualTo: challengeId)
                .limit(to: 10)
                .getDocuments()
            for doc in existing.documents {
                let sub = try? doc.data(as: ChallengeSubmission.self)
                guard let sub else { continue }
                if sub.status == .pending {
                    throw NSError(domain: "ApiClient.submitChallenge", code: 1, userInfo: [NSLocalizedDescriptionKey: "A submission for this challenge is already pending."])
                }
                if type == .oneShot && sub.status == .validated {
                    throw NSError(domain: "ApiClient.submitChallenge", code: 2, userInfo: [NSLocalizedDescriptionKey: "Challenge already validated."])
                }
            }
            let newDoc = submissionsRef.document()
            let submissionId = newDoc.documentID
            let fileExtension = mediaType == .video ? "mp4" : "jpg"
            let contentType = mediaType == .video ? "video/mp4" : "image/jpeg"
            let storageRef = Storage.storage().reference()
                .child("gameSubmissions/\(gameId)/\(submissionId).\(fileExtension)")
            let metadata = StorageMetadata()
            metadata.contentType = contentType
            _ = try await storageRef.putDataAsync(mediaData, metadata: metadata)
            let mediaUrl = try await storageRef.downloadURL().absoluteString
            let submission = ChallengeSubmission(
                firestoreId: submissionId,
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
            try await withRetry("validateChallengeSubmission(\(gameId), \(submissionId), \(accept))") {
                let functions = Functions.functions(region: "europe-west1")
                _ = try await functions
                    .httpsCallable("validateChallengeSubmission")
                    .call([
                        "gameId": gameId,
                        "submissionId": submissionId,
                        "accept": accept,
                    ])
            }
        },
        decrementTotalPoints: { gameId, hunterId in
            guard !gameId.isEmpty, !hunterId.isEmpty else {
                logger.warning("decrementTotalPoints skipped — gameId: '\(gameId)', hunterId: '\(hunterId)'")
                return
            }
            try await withRetry("applyOutOfZonePenalty(\(gameId), \(hunterId))") {
                let functions = Functions.functions(region: "europe-west1")
                _ = try await functions
                    .httpsCallable("applyOutOfZonePenalty")
                    .call(["gameId": gameId])
            }
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
        },
        setGameMasterPassword: { gameId, password in
            let functions = Functions.functions(region: "europe-west1")
            _ = try await functions
                .httpsCallable("setGameMasterPassword")
                .call(["gameId": gameId, "password": password])
        },
        clearGameMasterPassword: { gameId in
            let functions = Functions.functions(region: "europe-west1")
            _ = try await functions
                .httpsCallable("clearGameMasterPassword")
                .call(["gameId": gameId])
        },
        joinAsGameMaster: { gameId, password in
            let functions = Functions.functions(region: "europe-west1")
            let result = try await functions
                .httpsCallable("joinAsGameMaster")
                .call(["gameId": gameId, "password": password])
            let dict = result.data as? [String: Any] ?? [:]
            return JoinAsGameMasterResult(
                success: (dict["success"] as? Bool) ?? false,
                attemptsRemaining: (dict["attemptsRemaining"] as? Int) ?? 0,
                lockedUntilMs: dict["lockedUntil"] as? Int
            )
        },
        designateChicken: { gameId, newChickenUid in
            // PP-107: roles are server-owned. The callable atomically moves the
            // old chicken to `hunter` and `newChickenUid` to `chicken` in the
            // `roles` map (and enforces `status == waiting` + caller-is-GM).
            let functions = Functions.functions(region: "europe-west1")
            _ = try await functions
                .httpsCallable("designateChicken")
                .call(["gameId": gameId, "newChickenUid": newChickenUid])
        },
        validateRegistrationCode: { batchId, code in
            let functions = Functions.functions(region: "europe-west1")
            let result = try await functions
                .httpsCallable("validateRegistrationCode")
                .call(["batchId": batchId, "code": code])
            let dict = result.data as? [String: Any] ?? [:]
            switch dict["status"] as? String {
            case "valid": return .valid
            case "alreadyUsed": return .alreadyUsed
            default: return .invalid
            }
        },
        computeZoneConfiguration: { input in
            // PP-69: defer to the central Cloud Function so the radius,
            // drift seed and shrink schedule match what every other
            // platform sees for the same pins. Client-side mirrors live
            // in `Models/GameSettings.swift` as a parity reference until
            // PP-13 Phase 2 deletes them.
            let functions = Functions.functions(region: "europe-west1")
            var payload: [String: Any] = [
                "startPoint": ["lat": input.startPoint.lat, "lng": input.startPoint.lng],
                "gameMode": input.gameMode.rawValue,
                "gameDurationMinutes": input.gameDurationMinutes,
                "forceNewSeed": input.forceNewSeed,
            ]
            if let finalPoint = input.finalPoint {
                payload["finalPoint"] = ["lat": finalPoint.lat, "lng": finalPoint.lng]
            } else {
                payload["finalPoint"] = NSNull()
            }
            if let radiusHint = input.radiusHint {
                payload["radiusHint"] = radiusHint
            } else {
                payload["radiusHint"] = NSNull()
            }
            if let existingSeed = input.existingSeed {
                payload["existingSeed"] = existingSeed
            }

            let result = try await functions
                .httpsCallable("computeZoneConfiguration")
                .call(payload)
            guard let dict = result.data as? [String: Any] else {
                throw NSError(
                    domain: "ApiClient",
                    code: -1,
                    userInfo: [NSLocalizedDescriptionKey: "computeZoneConfiguration: malformed response"]
                )
            }

            func asDouble(_ value: Any?) -> Double? {
                if let d = value as? Double { return d }
                if let i = value as? Int { return Double(i) }
                if let n = value as? NSNumber { return n.doubleValue }
                return nil
            }
            func asInt(_ value: Any?) -> Int? {
                if let i = value as? Int { return i }
                if let n = value as? NSNumber { return n.intValue }
                if let d = value as? Double { return Int(d) }
                return nil
            }
            func asLatLng(_ value: Any?) -> ComputeZoneConfigurationOutput.LatLng? {
                guard let map = value as? [String: Any],
                      let lat = asDouble(map["lat"]),
                      let lng = asDouble(map["lng"]) else { return nil }
                return ComputeZoneConfigurationOutput.LatLng(lat: lat, lng: lng)
            }

            let initialRadius = asDouble(dict["initialRadius"]) ?? 0
            let validatedFinal = asLatLng(dict["validatedFinal"])
            let driftSeed = asInt(dict["driftSeed"]) ?? 1
            let finalZoneRadius = asDouble(dict["finalZoneRadius"]) ?? 50
            let interiorMargin = asDouble(dict["interiorMargin"]) ?? 200
            let shrinkIntervalMinutes = asDouble(dict["shrinkIntervalMinutes"]) ?? 5
            let shrinkMetersPerUpdate = asDouble(dict["shrinkMetersPerUpdate"]) ?? 0
            let rawCircles = (dict["circles"] as? [[String: Any]]) ?? []
            let circles: [ComputeZoneConfigurationOutput.Circle] = rawCircles.compactMap { raw in
                guard let radius = asDouble(raw["radiusMeters"]),
                      let center = asLatLng(raw["center"]) else { return nil }
                return ComputeZoneConfigurationOutput.Circle(
                    radiusMeters: radius,
                    center: center
                )
            }
            return ComputeZoneConfigurationOutput(
                initialRadius: initialRadius,
                validatedFinal: validatedFinal,
                driftSeed: driftSeed,
                finalZoneRadius: finalZoneRadius,
                interiorMargin: interiorMargin,
                shrinkIntervalMinutes: shrinkIntervalMinutes,
                shrinkMetersPerUpdate: shrinkMetersPerUpdate,
                circles: circles
            )
        },
        launchGame: { gameId in
            let functions = Functions.functions(region: "europe-west1")
            let result = try await functions
                .httpsCallable("launchGame")
                .call(["gameId": gameId])
            let dict = result.data as? [String: Any] ?? [:]
            let millis = (dict["actualStartMillis"] as? Int)
                ?? (dict["actualStartMillis"] as? Int64).map { Int($0) }
                ?? Int(Date().timeIntervalSince1970 * 1000)
            return Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
        },
        debugAdvanceGame: { gameId, action in
            let functions = Functions.functions(region: "europe-west1")
            _ = try await functions
                .httpsCallable("debugAdvanceGame")
                .call(["gameId": gameId, "action": action])
        }
    )
}

extension DependencyValues {
    var apiClient: ApiClient {
        get { self[ApiClient.self] }
        set { self[ApiClient.self] = newValue }
    }
}
