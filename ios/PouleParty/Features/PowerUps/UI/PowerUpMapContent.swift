//
//  PowerUpMapContent.swift
//  PouleParty
//
//  Shared Mapbox map-content helpers for rendering power-ups on the map
//  (collection disc + marker) plus the pulse-alpha computation used by
//  the breathing animation. Imported by both ChickenMapContent and
//  HunterMapContent to avoid duplicating the `ForEvery(...)` + disc/marker
//  pair.
//

import CoreLocation
import Foundation
import MapboxMaps
import SwiftUI

/// Semi-transparent filled disc matching the power-up collection radius.
/// Rendered beneath the `PowerUpMapMarker` so hunters see exactly where
/// auto-collection triggers. The caller supplies [pulseAlpha] so all discs
/// pulse in sync (driven by a timer on the parent view).
@MapContentBuilder
func powerUpCollectionOverlay(
    coordinate: CLLocationCoordinate2D,
    color: Color,
    pulseAlpha: Double
) -> some MapContent {
    let polygon = Polygon(
        center: coordinate,
        radius: AppConstants.powerUpCollectionRadiusMeters,
        vertices: 36
    )
    PolygonAnnotation(polygon: polygon)
        .fillColor(StyleColor(UIColor(color).withAlphaComponent(CGFloat(pulseAlpha))))
        .fillOutlineColor(StyleColor(UIColor.clear))
}

/// Shared map content that renders every power-up marker plus its pulsing
/// collection radius disc. Used by both `ChickenMapContent` and
/// `HunterMapContent` to avoid duplicating the `ForEvery(...)` + disc/marker
/// pair.
@MapContentBuilder
func powerUpsMapContent(
    powerUps: [PowerUp],
    pulseAlpha: Double,
    onTap: @escaping (PowerUp) -> Void
) -> some MapContent {
    // Two separate loops: Mapbox dispatches PolygonAnnotation (style layer)
    // and MapViewAnnotation (UIKit overlay) through different pipelines;
    // mixing both inside a single ForEvery silently drops the view-annotation
    // branch of the tuple.
    ForEvery(powerUps) { powerUp in
        powerUpCollectionOverlay(
            coordinate: powerUp.coordinate,
            color: powerUp.type.color,
            pulseAlpha: pulseAlpha
        )
    }
    ForEvery(powerUps) { powerUp in
        MapViewAnnotation(coordinate: powerUp.coordinate) {
            PowerUpMapMarker(powerUp: powerUp) {
                onTap(powerUp)
            }
        }
        .allowOverlap(true)
        .allowOverlapWithPuck(true)
    }
}

/// Computes the pulse alpha for the collection overlay from a monotonic time.
/// The disc breathes between [minAlpha, maxAlpha] over a 2-second period.
/// Exposed as a plain function so it can be unit-tested independently of the map.
func powerUpPulseAlpha(
    at time: TimeInterval,
    periodSeconds: Double = 2.0,
    minAlpha: Double = 0.08,
    maxAlpha: Double = 0.18
) -> Double {
    let phase = (time.truncatingRemainder(dividingBy: periodSeconds)) / periodSeconds
    let sine = (sin(phase * 2 * .pi) + 1) / 2  // 0…1
    return minAlpha + (maxAlpha - minAlpha) * sine
}
