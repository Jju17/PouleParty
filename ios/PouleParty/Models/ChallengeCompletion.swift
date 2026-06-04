import FirebaseFirestore
import Foundation

struct ChallengeCompletion: Codable, Equatable, Identifiable {
    @DocumentID var hunterId: String?
    var validatedChallengeIds: [String] = []
    var repeatableCounts: [String: Int] = [:]
    var totalPoints: Int = 0
    var teamName: String = ""

    var id: String { hunterId ?? UUID().uuidString }

    init(
        hunterId: String? = nil,
        validatedChallengeIds: [String] = [],
        repeatableCounts: [String: Int] = [:],
        totalPoints: Int = 0,
        teamName: String = ""
    ) {
        self.hunterId = hunterId
        self.validatedChallengeIds = validatedChallengeIds
        self.repeatableCounts = repeatableCounts
        self.totalPoints = totalPoints
        self.teamName = teamName
    }

    enum CodingKeys: String, CodingKey {
        case hunterId
        case validatedChallengeIds
        case repeatableCounts
        case totalPoints
        case teamName
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        hunterId = try c.decodeIfPresent(String.self, forKey: .hunterId)
        validatedChallengeIds = try c.decodeIfPresent([String].self, forKey: .validatedChallengeIds) ?? []
        repeatableCounts = try c.decodeIfPresent([String: Int].self, forKey: .repeatableCounts) ?? [:]
        totalPoints = try c.decodeIfPresent(Int.self, forKey: .totalPoints) ?? 0
        teamName = try c.decodeIfPresent(String.self, forKey: .teamName) ?? ""
    }
}
