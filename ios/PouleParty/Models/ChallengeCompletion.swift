import FirebaseFirestore
import Foundation

struct ChallengeCompletion: Codable, Equatable, Identifiable {
    @DocumentID var hunterId: String?
    var validatedChallengeIds: [String] = []
    var repeatableCounts: [String: Int] = [:]
    var totalPoints: Int = 0
    var teamName: String = ""

    var id: String { hunterId ?? UUID().uuidString }
}
