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

extension ChickenLocation {
    /// Decodes from a Realtime Database snapshot value (PP-102).
    /// Schema: `{ lat: Double, lng: Double, ts: <epoch ms>, invisible: Bool? }`.
    init?(rtdb value: Any?) {
        guard let dict = value as? [String: Any],
              let lat = rtdbDouble(dict["lat"]),
              let lng = rtdbDouble(dict["lng"]) else { return nil }
        self.location = GeoPoint(latitude: lat, longitude: lng)
        self.timestamp = Timestamp(date: Date(timeIntervalSince1970: (rtdbDouble(dict["ts"]) ?? 0) / 1000))
        self.invisible = dict["invisible"] as? Bool
    }
}
