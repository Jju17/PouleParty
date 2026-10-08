import ComposableArchitecture
import CoreLocation
import Foundation

/// Phase of the game the Home banner surfaces.
enum GamePhase: Equatable {
    case inProgress
    case upcoming
}

/// Every backend operation the features use. Reads throw instead of
/// returning an empty value; streams resubscribe after a listener error.
struct ApiClient {
    var findActiveGame: (_ userId: String) async throws -> (Game, GameRole, GamePhase)?
    var submitFoundCode: (_ gameId: String, _ foundCode: String, _ hunterName: String) async throws -> Void
    var getFoundCode: (_ gameId: String) async throws -> String
    var fetchZoneSchedule: (_ gameId: String) async throws -> [ZoneCircle]
    var findGameByCode: (_ code: String) async throws -> Game?
    var joinGame: (_ gameId: String, _ teamName: String) async throws -> Void
    var leaveGame: (_ gameId: String) async throws -> Void
    var updateGameStatus: (_ gameId: String, _ status: Game.GameStatus) async throws -> Void
    var chickenLocationStream: (_ gameId: String) -> AsyncStream<ChickenLocation?>
    var gameConfigStream: (_ gameId: String) -> AsyncStream<Game?>
    var hunterLocationsStream: (_ gameId: String) -> AsyncStream<[HunterLocation]>
    var setChickenLocation: (_ gameId: String, _ coordinate: CLLocationCoordinate2D, _ invisible: Bool) throws -> Void
    var setConfig: (Game) async throws -> Void
    var setHunterLocation: (_ gameId: String, _ hunterId: String, _ coordinate: CLLocationCoordinate2D) throws -> Void
    var collectPowerUp: (_ gameId: String, _ powerUpId: String, _ coordinate: CLLocationCoordinate2D) async throws -> Void
    var activatePowerUp: (_ gameId: String, _ powerUpId: String) async throws -> Void
    var powerUpsStream: (_ gameId: String) -> AsyncStream<[PowerUp]>
    var updateHeartbeat: (_ gameId: String) async throws -> Void
    var fetchMyGames: (_ userId: String) async throws -> [MyGame]
    var findRegistration: (_ gameId: String, _ userId: String) async throws -> Registration?
    var fetchAllRegistrations: (_ gameId: String) async throws -> [Registration]
    var registrationsStream: (_ gameId: String) -> AsyncStream<[Registration]>
    var challengesStream: (_ gameId: String) -> AsyncStream<[Challenge]>
    var leaderboardStream: (_ gameId: String) -> AsyncStream<[ChallengeCompletion]>
    var myCompletionStream: (_ gameId: String, _ hunterId: String) -> AsyncStream<ChallengeCompletion?>
    var hunterSubmissionsStream: (_ gameId: String, _ hunterId: String) -> AsyncStream<[ChallengeSubmission]>
    var pendingSubmissionsStream: (_ gameId: String) -> AsyncStream<[ChallengeSubmission]>
    var submitChallenge: (_ gameId: String, _ challengeId: String, _ hunterId: String, _ type: Challenge.ChallengeType, _ mediaData: Data, _ mediaType: ChallengeSubmission.MediaType) async throws -> ChallengeSubmission
    var validateChallengeSubmission: (_ gameId: String, _ submissionId: String, _ accept: Bool) async throws -> Void
    var applyOutOfZonePenalty: (_ gameId: String) async throws -> Void
    var reportPlayer: (_ gameId: String, _ reportedUserId: String, _ reportedNickname: String) async throws -> Void
    var newGameId: () -> String
    var setGameMasterPassword: (_ gameId: String, _ password: String) async throws -> Void
    var joinAsGameMaster: (_ gameId: String, _ password: String) async throws -> JoinAsGameMasterResult
    var designateChicken: (_ gameId: String, _ newChickenUid: String) async throws -> Void
    var validateRegistrationCode: (_ batchId: String, _ code: String) async throws -> ValidationCodeResult
    var launchGame: (_ gameId: String) async throws -> Date
    var debugAdvanceGame: (_ gameId: String, _ action: DebugAction) async throws -> Void
}

enum SubmitFoundCodeError: Error, Equatable {
    case invalidCode
    case cooldown(until: Date?)
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

enum ValidationCodeResult: Equatable {
    case valid
    case invalid
    case alreadyUsed
}

enum DebugAction: String, Equatable {
    case advanceStep
    case endNow
}

extension DependencyValues {
    var apiClient: ApiClient {
        get { self[ApiClient.self] }
        set { self[ApiClient.self] = newValue }
    }
}
