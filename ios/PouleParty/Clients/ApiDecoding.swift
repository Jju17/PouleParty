import Foundation

/// Points, then team name ignoring case, then id: equal teams never swap.
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

func decodeZoneCircles(_ raw: Any?) -> [ZoneCircle] {
    guard let list = raw as? [Any] else { return [] }
    return list.compactMap { entry -> ZoneCircle? in
        guard let m = entry as? [String: Any],
              let radius = (m["radiusMeters"] as? NSNumber)?.doubleValue,
              let lat = (m["lat"] as? NSNumber)?.doubleValue,
              let lng = (m["lng"] as? NSNumber)?.doubleValue
        else { return nil }
        return ZoneCircle(order: (m["order"] as? NSNumber)?.intValue ?? 0, radiusMeters: radius, lat: lat, lng: lng)
    }.sorted { $0.order < $1.order }
}

func decodeLeaderboard(_ raw: Any?) -> [ChallengeCompletion] {
    guard let entries = raw as? [String: Any] else { return [] }
    return entries.compactMap { hunterId, value -> ChallengeCompletion? in
        guard let entry = value as? [String: Any] else { return nil }
        var completion = ChallengeCompletion()
        completion.hunterId = hunterId
        completion.totalPoints = (entry["totalPoints"] as? NSNumber)?.intValue ?? 0
        completion.teamName = (entry["teamName"] as? String) ?? ""
        return completion
    }.sorted(by: leaderboardOrdersBefore)
}

func roleOf(_ game: Game, userId: String) -> GameRole? {
    if game.isChicken(userId) { return .chicken }
    if game.isGameMaster(userId) { return .gameMaster }
    if game.isHunter(userId) { return .hunter }
    return nil
}

/// A running game wins over an upcoming one; overdue and finished games are ignored.
func selectActiveGame(_ candidates: [(Game, GameRole)], now: Date) -> (Game, GameRole, GamePhase)? {
    let running = candidates.filter { $0.0.status == .inProgress && $0.0.endDate > now }
    if let (game, role) = running.max(by: { $0.0.startDate < $1.0.startDate }) {
        return (game, role, .inProgress)
    }
    let upcoming = candidates.filter { $0.0.status == .waiting && $0.0.startDate > now }
    if let (game, role) = upcoming.min(by: { $0.0.startDate < $1.0.startDate }) {
        return (game, role, .upcoming)
    }
    return nil
}

func parseSubmitFoundCode(_ raw: [String: Any]?) -> SubmitFoundCodeError? {
    guard let raw, let success = raw["success"] as? Bool else { return .malformedResponse }
    if success { return nil }
    switch raw["reason"] as? String {
    case "invalidCode": return .invalidCode
    case "cooldown":
        let until = (raw["lockedUntil"] as? NSNumber).map { Date(timeIntervalSince1970: $0.doubleValue / 1000) }
        return .cooldown(until: until)
    case "notAHunter": return .notAHunter
    case "alreadyWinner": return .alreadyWinner
    case "gameNotInProgress": return .gameNotInProgress
    default: return .malformedResponse
    }
}

func parseValidationCode(_ raw: [String: Any]?) -> ValidationCodeResult {
    switch raw?["status"] as? String {
    case "valid": .valid
    case "alreadyUsed": .alreadyUsed
    default: .invalid
    }
}

func parseJoinAsGameMaster(_ raw: [String: Any]?) -> JoinAsGameMasterResult {
    JoinAsGameMasterResult(
        success: (raw?["success"] as? Bool) ?? false,
        attemptsRemaining: (raw?["attemptsRemaining"] as? NSNumber)?.intValue ?? 0,
        lockedUntilMs: (raw?["lockedUntil"] as? NSNumber)?.intValue
    )
}

func parseLaunchedAt(_ raw: [String: Any]?, fallback: Date) -> Date {
    guard let millis = (raw?["actualStartMillis"] as? NSNumber)?.doubleValue else { return fallback }
    return Date(timeIntervalSince1970: millis / 1000)
}

enum SubmissionRejection: Error, Equatable {
    case alreadyPending
    case alreadyValidated
}

/// Refuses a duplicate proof before anything is uploaded.
func blockingSubmission(_ existing: [ChallengeSubmission], type: Challenge.ChallengeType) -> SubmissionRejection? {
    if existing.contains(where: { $0.status == .pending }) { return .alreadyPending }
    if type == .oneShot, existing.contains(where: { $0.status == .validated }) { return .alreadyValidated }
    return nil
}
