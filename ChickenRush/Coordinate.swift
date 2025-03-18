//
//  Coordinate.swift
//  ChickenRush
//
//  Created by Julien Rahier on 12/04/2024.
//

import FirebaseFirestore
import Foundation

struct LocationData: Codable, Equatable {
    let latitude: Double
    let longiture: Double
    let timestamp: Timestamp
}
