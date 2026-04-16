//
//  Winner.swift
//  PouleParty
//

import FirebaseFirestore
import Foundation

struct Winner: Codable, Equatable {
    let hunterId: String
    let hunterName: String
    let timestamp: Timestamp
}
