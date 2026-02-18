//
//  GeoPoint+Utils.swift
//  ChickenRush
//
//  Created by Julien Rahier on 16/03/2024.
//

import FirebaseFirestore
import CoreLocation
import Foundation

extension GeoPoint {
    var toCLCoordinates: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: self.latitude, longitude: self.longitude)
    }
}

extension CLLocationCoordinate2D: @retroactive Equatable {
    public static func == (lhs: CLLocationCoordinate2D, rhs: CLLocationCoordinate2D) -> Bool {
        lhs.latitude == rhs.latitude && lhs.longitude == rhs.longitude
    }
}
