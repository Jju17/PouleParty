//
//  HunterAnnotation.swift
//  PouleParty
//

import CoreLocation

struct HunterAnnotation: Equatable, Identifiable {
    let id: String
    var coordinate: CLLocationCoordinate2D
    var displayName: String
}
