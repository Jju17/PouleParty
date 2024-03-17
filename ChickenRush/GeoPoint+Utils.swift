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
