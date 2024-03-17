//
//  ChickenLocation.swift
//  ChickenRush
//
//  Created by Julien Rahier on 16/03/2024.
//

import Foundation
import FirebaseFirestore

struct ChickenLocation: Codable {
    let location: GeoPoint
    let timestamp: Timestamp
}
