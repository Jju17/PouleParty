//
//  Registration.swift
//  PouleParty
//

import FirebaseFirestore
import Foundation

struct Registration: Codable, Equatable, Identifiable {
    var id: String { userId }
    let userId: String
    var teamName: String
    var joinedAt: Timestamp = .init(date: .now)
}
