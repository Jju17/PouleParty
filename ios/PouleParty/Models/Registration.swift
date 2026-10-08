//
//  Registration.swift
//  PouleParty
//

import FirebaseFirestore
import Foundation

struct Registration: Codable, Equatable, Identifiable {
    @DocumentID var docId: String?
    var teamName: String
    var joinedAt: Timestamp = .init(date: .now)

    var userId: String { docId ?? "" }
    var id: String { userId }

    init(userId: String, teamName: String, joinedAt: Timestamp = .init(date: .now)) {
        self.docId = userId
        self.teamName = teamName
        self.joinedAt = joinedAt
    }
}
