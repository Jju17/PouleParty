import FirebaseFirestore
import FirebaseFunctions
import Foundation

/// Stable error codes sent by the callables in `details.code`.
enum ApiErrorCode: String, CaseIterable, Equatable, Sendable {
    case unauthenticated
    case invalidArgument
    case gameNotFound
    case gameNotJoinable
    case gameFull
    case alreadyHasRole
    case registrationRequired
    case notAllowed
    case notAHunter
    case notInProgress
    case gameOver
    case tooManyAttempts
    case gameMasterDisabled
    case notWaiting
    case chickenCannotLeave
    case powerUpNotFound
    case powerUpTaken
    case powerUpWrongRole
    case powerUpTooFar
    case powerUpAlreadyActive
    case positionUnknown
    case submissionNotFound
    case submissionAlreadyHandled
    case challengeNotFound
    case alreadyLaunched
    case notReadyToLaunch
    case notADebugGame
    case network
    case unknown
}

/// A failure the UI can translate; the server message is never displayed.
struct ApiError: Error, Equatable {
    let code: ApiErrorCode
    var lockedUntil: Date?
}

extension ApiError {
    init(_ error: Error) {
        if let apiError = error as? ApiError {
            self = apiError
            return
        }
        let nsError = error as NSError
        if nsError.domain == FunctionsErrorDomain {
            let details = nsError.userInfo[FunctionsErrorDetailsKey] as? [String: Any]
            let lockedUntil = (details?["lockedUntil"] as? NSNumber).map { Date(timeIntervalSince1970: $0.doubleValue / 1000) }
            if let wire = details?["code"] as? String {
                self.init(code: ApiErrorCode(rawValue: wire) ?? .unknown, lockedUntil: lockedUntil)
                return
            }
            switch FunctionsErrorCode(rawValue: nsError.code) {
            case .unavailable, .deadlineExceeded: self.init(code: .network)
            case .resourceExhausted: self.init(code: .tooManyAttempts, lockedUntil: lockedUntil)
            case .unauthenticated: self.init(code: .unauthenticated)
            default: self.init(code: .unknown)
            }
            return
        }
        if nsError.domain == FirestoreErrorDomain, nsError.code == FirestoreErrorCode.unavailable.rawValue {
            self.init(code: .network)
            return
        }
        if nsError.domain == NSURLErrorDomain {
            self.init(code: .network)
            return
        }
        self.init(code: .unknown)
    }
}

extension ApiErrorCode {
    var message: String {
        switch self {
        case .unauthenticated: String(localized: "Your session has expired. Restart the app and try again.")
        case .invalidArgument: String(localized: "Something in the request is not valid. Check what you entered.")
        case .gameNotFound: String(localized: "This game no longer exists.")
        case .gameNotJoinable: String(localized: "This game can no longer be joined.")
        case .gameFull: String(localized: "This game is full.")
        case .alreadyHasRole: String(localized: "You already play in this game.")
        case .registrationRequired: String(localized: "This event needs the code from your registration email.")
        case .notAllowed: String(localized: "You are not allowed to do this in this game.")
        case .notAHunter: String(localized: "Only a hunter can do this.")
        case .notInProgress: String(localized: "The game has not started yet.")
        case .gameOver: String(localized: "The game is over.")
        case .tooManyAttempts: String(localized: "Too many attempts. Wait a moment before trying again.")
        case .gameMasterDisabled: String(localized: "This game has no game master code.")
        case .notWaiting: String(localized: "This is only possible before the game starts.")
        case .chickenCannotLeave: String(localized: "The chicken cannot leave. Cancel the game instead.")
        case .powerUpNotFound: String(localized: "This power-up is gone.")
        case .powerUpTaken: String(localized: "Someone was faster on this power-up.")
        case .powerUpWrongRole: String(localized: "This power-up is for the other side.")
        case .powerUpTooFar: String(localized: "Get closer to the power-up to collect it.")
        case .powerUpAlreadyActive: String(localized: "This power-up is already active.")
        case .positionUnknown: String(localized: "Your position is not known yet. Wait for the GPS.")
        case .submissionNotFound: String(localized: "This proof no longer exists.")
        case .submissionAlreadyHandled: String(localized: "Another referee already handled this proof.")
        case .challengeNotFound: String(localized: "This challenge no longer exists.")
        case .alreadyLaunched: String(localized: "The game has already been launched.")
        case .notReadyToLaunch: String(localized: "The game cannot be launched yet.")
        case .notADebugGame: String(localized: "This action only works in a test game.")
        case .network: String(localized: "No connection. Check your network and try again.")
        case .unknown: String(localized: "Something went wrong. Try again.")
        }
    }
}

extension Error {
    /// The translated message to show for any failure.
    var userMessage: String {
        if let rejection = self as? SubmissionRejection { return rejection.message }
        return ApiError(self).code.message
    }
}

extension SubmissionRejection {
    var message: String {
        switch self {
        case .alreadyPending: String(localized: "A proof for this challenge is already waiting for validation.")
        case .alreadyValidated: String(localized: "This challenge is already validated.")
        }
    }
}
