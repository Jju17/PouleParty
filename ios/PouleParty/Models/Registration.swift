//
//  Registration.swift
//  PouleParty
//

import FirebaseFirestore
import Foundation

/// PP-107: per-game player team-name doc, read from `/games/{id}/players/{uid}`
/// (renamed from `/registrations` server-side). The doc shape is now
/// `{ teamName, joinedAt }` — `userId` no longer lives in the payload, it's the
/// doc id, decoded via `@DocumentID`. Clients never write this doc anymore; the
/// `joinGame` callable creates it server-side.
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
