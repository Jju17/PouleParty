import FirebaseFirestore
import Foundation

struct ChallengeSubmission: Codable, Equatable, Identifiable {
    @DocumentID var firestoreId: String?
    var challengeId: String = ""
    var hunterId: String = ""
    var type: Challenge.ChallengeType = .oneShot
    var submittedAt: Timestamp?
    var photoUrl: String = ""
    var status: SubmissionStatus = .pending
    var validatedBy: String? = nil
    var validatedAt: Timestamp? = nil

    var id: String { firestoreId ?? UUID().uuidString }

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
        case photoUrl
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
        photoUrl: String = "",
        status: SubmissionStatus = .pending,
        validatedBy: String? = nil,
        validatedAt: Timestamp? = nil
    ) {
        self.firestoreId = firestoreId
        self.challengeId = challengeId
        self.hunterId = hunterId
        self.type = type
        self.submittedAt = submittedAt
        self.photoUrl = photoUrl
        self.status = status
        self.validatedBy = validatedBy
        self.validatedAt = validatedAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        firestoreId = try c.decodeIfPresent(String.self, forKey: .firestoreId)
        challengeId = try c.decodeIfPresent(String.self, forKey: .challengeId) ?? ""
        hunterId = try c.decodeIfPresent(String.self, forKey: .hunterId) ?? ""
        type = try c.decodeIfPresent(Challenge.ChallengeType.self, forKey: .type) ?? .oneShot
        submittedAt = try c.decodeIfPresent(Timestamp.self, forKey: .submittedAt)
        photoUrl = try c.decodeIfPresent(String.self, forKey: .photoUrl) ?? ""
        status = try c.decodeIfPresent(SubmissionStatus.self, forKey: .status) ?? .pending
        validatedBy = try c.decodeIfPresent(String.self, forKey: .validatedBy)
        validatedAt = try c.decodeIfPresent(Timestamp.self, forKey: .validatedAt)
    }
}
