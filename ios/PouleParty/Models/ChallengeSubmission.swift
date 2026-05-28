import FirebaseFirestore
import Foundation

struct ChallengeSubmission: Codable, Equatable, Identifiable {
    @DocumentID var firestoreId: String?
    var challengeId: String = ""
    var hunterId: String = ""
    var type: Challenge.ChallengeType = .oneShot
    var submittedAt: Timestamp?
    var mediaUrl: String = ""
    var mediaType: MediaType = .image
    var status: SubmissionStatus = .pending
    var validatedBy: String? = nil
    var validatedAt: Timestamp? = nil

    /// Stable identifier for SwiftUI diffing. `firestoreId` is injected
    /// by the SDK from the doc path so this is non-nil for real
    /// submissions; the fallback derives from challenge+hunter so the
    /// id stays stable across renders even before injection (UUID()
    /// would generate a new value every access and cause SwiftUI
    /// infinite re-render in `ForEach`/`.sheet(item:)`).
    var id: String { firestoreId ?? "\(challengeId)-\(hunterId)" }

    enum MediaType: String, Codable, CaseIterable, Equatable {
        case image
        case video

        init(from decoder: Decoder) throws {
            let raw = try decoder.singleValueContainer().decode(String.self)
            self = MediaType(rawValue: raw) ?? .image
        }
    }

    enum SubmissionStatus: String, Codable, CaseIterable, Equatable {
        case pending
        case validated
        case rejected

        init(from decoder: Decoder) throws {
            let raw = try decoder.singleValueContainer().decode(String.self)
            self = SubmissionStatus(rawValue: raw) ?? .pending
        }
    }

    enum CodingKeys: String, CodingKey {
        case firestoreId
        case challengeId
        case hunterId
        case type
        case submittedAt
        case mediaUrl
        case mediaType
        case status
        case validatedBy
        case validatedAt
    }

    init(
        firestoreId: String? = nil,
        challengeId: String = "",
        hunterId: String = "",
        type: Challenge.ChallengeType = .oneShot,
        submittedAt: Timestamp? = nil,
        mediaUrl: String = "",
        mediaType: MediaType = .image,
        status: SubmissionStatus = .pending,
        validatedBy: String? = nil,
        validatedAt: Timestamp? = nil
    ) {
        self.firestoreId = firestoreId
        self.challengeId = challengeId
        self.hunterId = hunterId
        self.type = type
        self.submittedAt = submittedAt
        self.mediaUrl = mediaUrl
        self.mediaType = mediaType
        self.status = status
        self.validatedBy = validatedBy
        self.validatedAt = validatedAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        // Do NOT decode `firestoreId` here — the Firestore SDK injects
        // it post-decode via `@DocumentID` reflection on the property
        // wrapper. Writing to it from a custom decoder would clobber
        // the SDK-injected doc id with `nil` (the data has no
        // `firestoreId` field), which then makes `validate(...)` in
        // ValidationQueueFeature silently no-op because its early-
        // return guards on a non-empty `firestoreId`.
        challengeId = try c.decodeIfPresent(String.self, forKey: .challengeId) ?? ""
        hunterId = try c.decodeIfPresent(String.self, forKey: .hunterId) ?? ""
        type = try c.decodeIfPresent(Challenge.ChallengeType.self, forKey: .type) ?? .oneShot
        submittedAt = try c.decodeIfPresent(Timestamp.self, forKey: .submittedAt)
        mediaUrl = try c.decodeIfPresent(String.self, forKey: .mediaUrl) ?? ""
        mediaType = try c.decodeIfPresent(MediaType.self, forKey: .mediaType) ?? .image
        status = try c.decodeIfPresent(SubmissionStatus.self, forKey: .status) ?? .pending
        validatedBy = try c.decodeIfPresent(String.self, forKey: .validatedBy)
        validatedAt = try c.decodeIfPresent(Timestamp.self, forKey: .validatedAt)
    }
}
