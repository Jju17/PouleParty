//
//  MapOverlays.swift
//  PouleParty
//
//  Equatable overlay types for TCA state.
//  Zone overlay (inverted polygon) is handled natively by Mapbox PolygonAnnotation.
//

import CoreLocation
import Foundation
import MapboxMaps
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
///
/// Defensive defaults: a non-finite or non-positive radius (or polar latitude)
/// would otherwise divide by zero / produce NaN and leave the camera blank.
/// Falls back to zoom 15 in those cases — same as Android `zoomForRadius`.
func zoomForRadius(_ radiusMeters: CLLocationDistance, latitude: CLLocationDegrees) -> CGFloat {
    guard radiusMeters.isFinite, radiusMeters > 0 else { return 15 }
    guard latitude.isFinite else { return 15 }
    let earthCircumference = 40_075_016.686
    let latRad = latitude * .pi / 180.0
    let cosLat = cos(latRad)
    let safeCosLat = abs(cosLat) > 1e-9 ? cosLat : 1e-9
    let arg = earthCircumference * safeCosLat / (2.0 * radiusMeters)
    guard arg.isFinite, arg > 0 else { return 15 }
    let zoom = log2(arg) - 1.0
    guard zoom.isFinite else { return 15 }
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

    static let brussels = CameraRegion(
        center: CLLocationCoordinate2D(latitude: AppConstants.defaultLatitude, longitude: AppConstants.defaultLongitude)
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

// MARK: - Zone Overlay Map Content

/// Shared Mapbox map content that renders:
/// 1. An inverted polygon (dims everything outside the zone with `overlayColor`).
/// 2. A layered neon green border around the zone.
@MapContentBuilder
func zoneOverlayContent(circle: CircleOverlay, overlayColor: UIColor) -> some MapContent {
    let circlePolygon = Polygon(center: circle.center, radius: circle.radius, vertices: 72)
    let outerCoords = outerBoundsCoordinates(center: circle.center)
    // Close the polygon ring. outerBoundsCoordinates is a static 4-corner rect,
    // but guard anyway so a future caller change can't crash the map.
    let closingCoord = outerCoords.first ?? circle.center
    let invertedPolygon = Polygon(
        outerRing: Ring(coordinates: outerCoords + [closingCoord]),
        innerRings: [circlePolygon.outerRing]
    )
    PolygonAnnotation(polygon: invertedPolygon)
        .fillColor(StyleColor(overlayColor))
        .fillOpacity(1.0)

    // Zone border circle — neon glow effect (layered polylines)
    PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
        .lineColor(StyleColor(UIColor(Color.zoneGreen).withAlphaComponent(0.08)))
        .lineWidth(16)
    PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
        .lineColor(StyleColor(UIColor(Color.zoneGreen).withAlphaComponent(0.15)))
        .lineWidth(8)
    PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
        .lineColor(StyleColor(UIColor(Color.zoneGreen).withAlphaComponent(0.35)))
        .lineWidth(4)
    PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
        .lineColor(StyleColor(UIColor(Color.zoneGreen).withAlphaComponent(0.9)))
        .lineWidth(2.5)
}

// Power-up map content (collection disc + marker + pulse alpha) lives in
// PowerUps/UI/PowerUpMapContent.swift.

// MARK: - Debug preview (all shrunk circles at once)

/// Wide HSV hue sweep so successive shrink circles are as distinct
/// as possible visually while staying on a coherent monotonic curve.
/// Goes orange (~28°) → yellow → green → cyan → blue → purple →
/// magenta (~332°) — every neighbouring pair differs by enough hue
/// for the chicken to read the shrink order at a glance, no matter
/// how many circles the schedule produces. Stable across iOS +
/// Android by matching the Kotlin `zonePreviewColor` HSV formula.
func zonePreviewColor(forIndex index: Int, totalCount: Int) -> Color {
    guard totalCount > 1 else { return Color.CROrange }
    let t = max(0, min(1, Double(index) / Double(totalCount - 1)))
    // Hue values are SwiftUI's [0, 1] fraction (× 360 = degrees).
    let hue = 0.08 + t * (0.92 - 0.08)
    return Color(hue: hue, saturation: 0.95, brightness: 0.95)
}

/// Draws every future shrunk circle stacked on top of the map. Used
/// by both the wizard recap step and the long-press-on-Create-Party
/// debug preview.
@MapContentBuilder
func zonePreviewCirclesContent(circles: [DebugShrinkCircle]) -> some MapContent {
    let total = circles.count
    ForEvery(Array(circles.enumerated()), id: \.offset) { pair in
        let color = zonePreviewColor(forIndex: pair.offset, totalCount: total)
        let ring = Polygon(center: pair.element.center, radius: pair.element.radius, vertices: 96)
        PolylineAnnotation(lineCoordinates: ring.outerRing.coordinates)
            .lineColor(StyleColor(UIColor(color).withAlphaComponent(0.95)))
            .lineWidth(2.5)
    }
}

/// Layered neon glow marking the game's final zone center on the chicken map.
@MapContentBuilder
func finalZoneGlowContent(center: CLLocationCoordinate2D) -> some MapContent {
    let finalCircle = Polygon(center: center, radius: 50, vertices: 36)
    PolylineAnnotation(lineCoordinates: finalCircle.outerRing.coordinates)
        .lineColor(StyleColor(UIColor(Color.zoneGreen).withAlphaComponent(0.15)))
        .lineWidth(8)
    PolylineAnnotation(lineCoordinates: finalCircle.outerRing.coordinates)
        .lineColor(StyleColor(UIColor(Color.zoneGreen).withAlphaComponent(0.5)))
        .lineWidth(3)
    PolylineAnnotation(lineCoordinates: finalCircle.outerRing.coordinates)
        .lineColor(StyleColor(UIColor(Color.zoneGreen).withAlphaComponent(0.9)))
        .lineWidth(1.5)
}
