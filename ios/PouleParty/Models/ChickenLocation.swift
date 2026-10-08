
import Foundation
import FirebaseFirestore

struct ChickenLocation: Codable {
    let location: GeoPoint
    let timestamp: Timestamp
    let invisible: Bool?
}

extension ChickenLocation {
    init?(rtdb value: Any?) {
        guard let dict = value as? [String: Any],
              let lat = rtdbDouble(dict["lat"]),
              let lng = rtdbDouble(dict["lng"]) else { return nil }
        self.location = GeoPoint(latitude: lat, longitude: lng)
        self.timestamp = Timestamp(date: Date(timeIntervalSince1970: (rtdbDouble(dict["ts"]) ?? 0) / 1000))
        self.invisible = dict["invisible"] as? Bool
    }
}
