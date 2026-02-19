//
//  MapOverlays.swift
//  PouleParty
//
//  Equatable overlay types for TCA state.
//  Converted to MapKit views (MapCircle, Marker) in the SwiftUI layer.
//

import CoreLocation
import MapKit
import SwiftUI

struct CircleOverlay: Equatable {
    var center: CLLocationCoordinate2D
    var radius: CLLocationDistance
}

struct MarkerOverlay: Equatable {
    var title: String
    var coordinate: CLLocationCoordinate2D
}

struct CameraRegion: Equatable {
    var center: CLLocationCoordinate2D
    var latitudeDelta: Double
    var longitudeDelta: Double

    var toMapCameraPosition: MapCameraPosition {
        MapCameraPosition.region(MKCoordinateRegion(
            center: center,
            span: MKCoordinateSpan(latitudeDelta: latitudeDelta, longitudeDelta: longitudeDelta)
        ))
    }

    static let brussels = CameraRegion(
        center: CLLocationCoordinate2D(latitude: 50.8503, longitude: 4.3517),
        latitudeDelta: 0.01,
        longitudeDelta: 0.01
    )
}
