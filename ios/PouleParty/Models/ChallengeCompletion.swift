//
//  ChallengeCompletion.swift
//  PouleParty
//
//  One document per hunter, stored under
//  /games/{gameId}/challengeCompletions/{hunterId}.
//  Tracks which challenges that hunter has completed and their total score.
//

import FirebaseFirestore
import Foundation

struct ChallengeCompletion: Codable, Equatable, Identifiable {
    @DocumentID var hunterId: String?
    var completedChallengeIds: [String] = []
    var totalPoints: Int = 0
    var teamName: String = ""

    var id: String { hunterId ?? UUID().uuidString }
}
