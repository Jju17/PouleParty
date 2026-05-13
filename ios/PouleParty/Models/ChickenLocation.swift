//
//  ChickenLocation.swift
//  PouleParty
//
//  Created by Julien Rahier on 16/03/2024.
//

import Foundation
import FirebaseFirestore

struct ChickenLocation: Codable {
    let location: GeoPoint
    let timestamp: Timestamp
    /// `true` while the chicken has Invisibility active (PP-87). The
    /// chicken keeps writing positions during Invisibility — hunters
    /// filter the marker out client-side based on this flag, and the
    /// GameMaster (PP-24) ignores the flag entirely. Optional so
    /// pre-PP-87 docs without the field decode to `false`.
    let invisible: Bool?
}
