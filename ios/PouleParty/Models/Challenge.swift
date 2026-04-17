//
//  Challenge.swift
//  PouleParty
//
//  A challenge hunters can complete during a game to score points.
//  Managed from the Firebase Console — clients are read-only.
//

import FirebaseFirestore
import Foundation

struct Challenge: Codable, Equatable, Identifiable {
    @DocumentID var firestoreId: String?
    var title: String = ""
    var body: String = ""
    var points: Int = 0
    var lastUpdated: Timestamp?

    var id: String { firestoreId ?? UUID().uuidString }
}

extension Challenge {
    static var mock: Challenge {
        Challenge(
            firestoreId: "mock-challenge",
            title: "Climb the highest roof",
            body: "Take a selfie on top of the tallest rooftop you can find.",
            points: 50,
            lastUpdated: Timestamp(date: .now)
        )
    }
}
