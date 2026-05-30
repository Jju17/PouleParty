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

extension HunterLocation {
    /// Decodes from a Realtime Database child snapshot (PP-102). The `hunterId`
    /// is the RTDB key (not stored in the payload). Schema: `{ lat, lng, ts }`.
    init?(hunterId: String, rtdb value: Any?) {
        guard let dict = value as? [String: Any],
              let lat = rtdbDouble(dict["lat"]),
              let lng = rtdbDouble(dict["lng"]) else { return nil }
        self.hunterId = hunterId
        self.location = GeoPoint(latitude: lat, longitude: lng)
        self.timestamp = Timestamp(date: Date(timeIntervalSince1970: (rtdbDouble(dict["ts"]) ?? 0) / 1000))
    }
}
