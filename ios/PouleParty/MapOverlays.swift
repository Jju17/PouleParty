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

// MARK: - Winner Notification Overlay

/// Shared overlay for displaying winner notifications on map screens.
struct WinnerNotificationOverlay: View {
    let notification: String?

    var body: some View {
        if let notification {
            Text(notification)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(.green.opacity(0.9))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .padding(.top, 100)
                .transition(.move(edge: .top).combined(with: .opacity))
                .animation(.easeInOut, value: notification)
        }
    }
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
        center: CLLocationCoordinate2D(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude),
        latitudeDelta: 0.01,
        longitudeDelta: 0.01
    )
}
