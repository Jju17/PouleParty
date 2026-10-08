import FirebaseFirestore
import FirebaseFunctions
import Foundation
import Testing
@testable import PouleParty

struct ApiDecodingTests {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    private func game(_ id: String, _ status: Game.GameStatus, start: TimeInterval, end: TimeInterval) -> Game {
        var game = Game.mock
        game.id = id
        game.status = status
        game.timing.start = Timestamp(date: now.addingTimeInterval(start))
        game.timing.end = Timestamp(date: now.addingTimeInterval(end))
        return game
    }

    @Test func zoneCirclesAreSortedAndMalformedEntriesSkipped() {
        let raw: [Any] = [
            ["order": 1, "radiusMeters": 900.0, "lat": 50.0, "lng": 4.0],
            ["order": 0, "radiusMeters": 1000, "lat": 50.1, "lng": 4.1],
            ["order": 2, "radiusMeters": "oops", "lat": 50.0, "lng": 4.0],
            "not a map",
        ]
        let circles = decodeZoneCircles(raw)
        #expect(circles.map(\.order) == [0, 1])
        #expect(circles.first?.radiusMeters == 1000)
        #expect(decodeZoneCircles(nil).isEmpty)
    }

    @Test func leaderboardSortsByPointsThenNameThenId() {
        let entries: [String: Any] = [
            "h3": ["teamName": "zèbre", "totalPoints": 10],
            "h1": ["teamName": "Alpha", "totalPoints": 10],
            "h2": ["teamName": "alpha", "totalPoints": 10],
            "h4": ["teamName": "Top", "totalPoints": 25],
            "h5": "broken",
        ]
        #expect(decodeLeaderboard(entries).map(\.hunterId) == ["h4", "h1", "h2", "h3"])
    }

    @Test func runningGameWinsOverUpcoming() {
        let upcoming = game("up", .waiting, start: 60, end: 3600)
        let running = game("run", .inProgress, start: -60, end: 3600)
        let result = selectActiveGame([(upcoming, .hunter), (running, .chicken)], now: now)
        #expect(result?.0.id == "run")
        #expect(result?.1 == .chicken)
        #expect(result?.2 == .inProgress)
    }

    @Test func soonestUpcomingGameIsPicked() {
        let later = game("later", .waiting, start: 7200, end: 9000)
        let sooner = game("sooner", .waiting, start: 600, end: 4000)
        #expect(selectActiveGame([(later, .hunter), (sooner, .hunter)], now: now)?.0.id == "sooner")
    }

    @Test func overdueAndFinishedGamesAreIgnored() {
        let overdue = game("overdue", .inProgress, start: -7200, end: -60)
        let done = game("done", .done, start: -7200, end: 3600)
        let late = game("late", .waiting, start: -60, end: 3600)
        #expect(selectActiveGame([(overdue, .hunter), (done, .hunter), (late, .hunter)], now: now) == nil)
    }

    @Test func foundCodeReasonsAreParsed() {
        #expect(parseSubmitFoundCode(["success": true]) == nil)
        #expect(parseSubmitFoundCode(["success": false, "reason": "invalidCode"]) == .invalidCode)
        #expect(parseSubmitFoundCode(["success": false, "reason": "notAHunter"]) == .notAHunter)
        #expect(parseSubmitFoundCode(["success": false, "reason": "alreadyWinner"]) == .alreadyWinner)
        #expect(parseSubmitFoundCode(["success": false, "reason": "gameNotInProgress"]) == .gameNotInProgress)
        #expect(parseSubmitFoundCode(["success": false, "reason": "new"]) == .malformedResponse)
        #expect(parseSubmitFoundCode(nil) == .malformedResponse)
        let cooldown = parseSubmitFoundCode(["success": false, "reason": "cooldown", "lockedUntil": 1_800_000_060_000])
        #expect(cooldown == .cooldown(until: now.addingTimeInterval(60)))
    }

    @Test func registrationAndGameMasterResultsAreParsed() {
        #expect(parseValidationCode(["status": "valid"]) == .valid)
        #expect(parseValidationCode(["status": "alreadyUsed"]) == .alreadyUsed)
        #expect(parseValidationCode(nil) == .invalid)
        #expect(parseJoinAsGameMaster(["success": false, "attemptsRemaining": 2, "lockedUntil": 99])
            == JoinAsGameMasterResult(success: false, attemptsRemaining: 2, lockedUntilMs: 99))
        #expect(parseLaunchedAt(["actualStartMillis": 1_800_000_000_000], fallback: .distantPast) == now)
        #expect(parseLaunchedAt([:], fallback: now) == now)
    }

    @Test func duplicateProofsAreRefusedBeforeUpload() {
        var pending = ChallengeSubmission()
        pending.status = .pending
        var validated = ChallengeSubmission()
        validated.status = .validated
        #expect(blockingSubmission([pending], type: .repeatable) == .alreadyPending)
        #expect(blockingSubmission([validated], type: .oneShot) == .alreadyValidated)
        #expect(blockingSubmission([validated], type: .repeatable) == nil)
        #expect(blockingSubmission([], type: .oneShot) == nil)
    }
}

struct ApiErrorTests {
    @Test func callableDetailsCodeIsUsed() {
        let error = NSError(domain: FunctionsErrorDomain, code: FunctionsErrorCode.failedPrecondition.rawValue, userInfo: [
            FunctionsErrorDetailsKey: ["code": "powerUpTooFar"],
        ])
        #expect(ApiError(error).code == .powerUpTooFar)
    }

    @Test func rateLimitCarriesTheLock() {
        let error = NSError(domain: FunctionsErrorDomain, code: FunctionsErrorCode.resourceExhausted.rawValue, userInfo: [
            FunctionsErrorDetailsKey: ["code": "tooManyAttempts", "lockedUntil": 1_800_000_000_000],
        ])
        let apiError = ApiError(error)
        #expect(apiError.code == .tooManyAttempts)
        #expect(apiError.lockedUntil == Date(timeIntervalSince1970: 1_800_000_000))
    }

    @Test func transportFailuresMapToNetwork() {
        #expect(ApiError(NSError(domain: FunctionsErrorDomain, code: FunctionsErrorCode.unavailable.rawValue)).code == .network)
        #expect(ApiError(URLError(.notConnectedToInternet)).code == .network)
        #expect(ApiError(NSError(domain: FirestoreErrorDomain, code: FirestoreErrorCode.unavailable.rawValue)).code == .network)
        #expect(ApiError(NSError(domain: "x", code: 1)).code == .unknown)
    }

    @Test func unknownWireCodesDoNotCrash() {
        let error = NSError(domain: FunctionsErrorDomain, code: FunctionsErrorCode.internal.rawValue, userInfo: [
            FunctionsErrorDetailsKey: ["code": "brandNew"],
        ])
        #expect(ApiError(error).code == .unknown)
    }

    @Test func everyCodeHasAMessage() {
        for code in ApiErrorCode.allCases {
            #expect(!code.message.isEmpty)
        }
    }
}

struct ResubscribingStreamTests {
    @Test func delayDoublesAndIsCapped() {
        #expect(resubscribeDelay(failures: 0, cap: .seconds(30)) == .seconds(1))
        #expect(resubscribeDelay(failures: 3, cap: .seconds(30)) == .seconds(8))
        #expect(resubscribeDelay(failures: 9, cap: .seconds(30)) == .seconds(30))
        #expect(resubscribeDelay(failures: 2, cap: .seconds(3)) == .seconds(3))
    }

    @Test func aFailedListenerIsReattachedAndKeepsStreaming() async {
        let attachCount = LockIsolatedCounter()
        let stream: AsyncStream<Int> = resubscribingStream("test", cap: .milliseconds(10), whenDenied: -1) { yield, fail in
            let attempt = attachCount.increment()
            yield(attempt)
            if attempt < 2 { fail(NSError(domain: "test", code: 1)) }
            return {}
        }
        var received: [Int] = []
        for await value in stream {
            received.append(value)
            if received.count == 3 { break }
        }
        #expect(received == [1, -1, 2])
        #expect(attachCount.value == 2)
    }
}

private final class LockIsolatedCounter: @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0
    var value: Int { lock.withLock { count } }
    func increment() -> Int { lock.withLock { count += 1; return count } }
}
