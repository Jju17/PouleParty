//
//  HunterLocation.swift
//  PouleParty
//
//  Created by Julien Rahier on 17/02/2026.
//

import Foundation
import FirebaseFirestore

struct HunterLocation: Codable, Equatable {
    let hunterId: String
    let location: GeoPoint
    let timestamp: Timestamp
}
