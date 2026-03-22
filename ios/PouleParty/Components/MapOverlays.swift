//
//  MapOverlays.swift
//  PouleParty
//
//  Equatable overlay types for TCA state.
//  Zone overlay (inverted polygon) is handled natively by Mapbox PolygonAnnotation.
//

import CoreLocation
import Foundation
import SwiftUI

// MARK: - Zone Warning Overlay

/// Red banner shown when the player is outside the zone.
struct ZoneWarningOverlay: View {
    var body: some View {
        Text("Return to the zone!")
            .font(.system(size: 16, weight: .bold))
            .foregroundStyle(.white)
            .padding(.horizontal, 24)
            .padding(.vertical, 12)
            .background(Color.zoneDanger.opacity(0.9))
            .neonGlow(.zoneDanger, intensity: .subtle)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .padding(.top, 140)
    }
}

// MARK: - Circle Overlay

struct CircleOverlay: Equatable {
    var center: CLLocationCoordinate2D
    var radius: CLLocationDistance
}

// MARK: - Zoom Calculation

/// Calculates the Mapbox zoom level needed to show a circle of given radius on screen.
/// Provides ~25% padding around the circle on a typical mobile viewport.
func zoomForRadius(_ radiusMeters: CLLocationDistance, latitude: CLLocationDegrees) -> CGFloat {
    let earthCircumference = 40_075_016.686
    let latRad = latitude * .pi / 180.0
    let zoom = log2(earthCircumference * cos(latRad) / (2.0 * radiusMeters)) - 1.0
    return CGFloat(min(max(zoom, 8.0), 18.0))
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
                .background(Color.zoneGreen.opacity(0.9))
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

    static let brussels = CameraRegion(
        center: CLLocationCoordinate2D(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude),
        latitudeDelta: 0.01,
        longitudeDelta: 0.01
    )
}

// MARK: - Outer Bounds (inverted zone overlay)

/// Large rectangle centered on the given coordinate, used as the outer boundary for inverted zone overlay.
func outerBoundsCoordinates(center: CLLocationCoordinate2D, padding: Double = 20.0) -> [CLLocationCoordinate2D] {
    let north = min(85.0, center.latitude + padding)
    let south = max(-85.0, center.latitude - padding)
    let west = center.longitude - padding
    let east = center.longitude + padding
    return [
        CLLocationCoordinate2D(latitude: north, longitude: west),
        CLLocationCoordinate2D(latitude: north, longitude: east),
        CLLocationCoordinate2D(latitude: south, longitude: east),
        CLLocationCoordinate2D(latitude: south, longitude: west)
    ]
}
