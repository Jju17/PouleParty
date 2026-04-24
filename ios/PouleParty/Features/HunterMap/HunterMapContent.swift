//
//  HunterMapContent.swift
//  PouleParty
//
//  The Mapbox map itself — zone overlay, zone-preview, power-up markers,
//  decoy marker and compass. Chrome lives on the parent `HunterMapView`.
//

import ComposableArchitecture
import CoreLocation
import MapboxMaps
import SwiftUI

struct HunterMapContent: View {
    @Bindable var store: StoreOf<HunterMapFeature>
    @Binding var selectedPowerUp: PowerUp?

    @State private var viewport: Viewport = .camera(
        center: CLLocationCoordinate2D(
            latitude: AppConstants.defaultLatitude,
            longitude: AppConstants.defaultLongitude
        ),
        zoom: 14
    )
    @State private var mapBearing: Double = 0
    @State private var powerUpPulseClock: TimeInterval = 0

    private var overlayColor: UIColor {
        store.isOutsideZone
            ? UIColor(Color.zoneDanger).withAlphaComponent(0.4)
            : UIColor.black.withAlphaComponent(0.3)
    }

    var body: some View {
        Map(viewport: $viewport) {
            Puck2D(bearing: .heading)

            // Inverted zone overlay (only visible after game starts)
            if store.hasGameStarted, let circle = store.mapCircle {
                zoneOverlayContent(circle: circle, overlayColor: overlayColor)
            }

            // Zone Preview power-up effect (dashed preview of next zone)
            if let preview = store.previewCircle {
                let previewPolygon = Polygon(center: preview.center, radius: preview.radius, vertices: 72)
                PolylineAnnotation(lineCoordinates: previewPolygon.outerRing.coordinates)
                    .lineColor(StyleColor(UIColor(Color.powerupFreeze).withAlphaComponent(0.6)))
                    .lineWidth(2)
            }

            // Power-up markers + collection-radius discs (hunter power-ups only)
            if store.hasGameStarted {
                powerUpsMapContent(
                    powerUps: store.availablePowerUps,
                    pulseAlpha: powerUpPulseAlpha(at: powerUpPulseClock)
                ) { powerUp in
                    selectedPowerUp = powerUp
                }
            }

            // Decoy: fake chicken marker when decoy is active
            if let decoyLocation = store.decoyLocation {
                MapViewAnnotation(coordinate: decoyLocation) {
                    DecoyMapMarker()
                }
                .allowOverlap(true)
                .allowOverlapWithPuck(true)
            }

            // Radar Ping reveal: real Chicken marker, visible only while
            // `powerUps.activeEffects.radarPing` is active. The Chicken
            // broadcasts its position continuously so this marker shows
            // the live point — the power-up is purely a visibility gate,
            // not a trigger for the broadcast itself (a 3 s Radar Ping
            // window is too short to rely on a fresh write landing
            // exactly within it).
            if store.game.isRadarPingActive, let chickenLocation = store.chickenLocation {
                MapViewAnnotation(coordinate: chickenLocation) {
                    ChickenMapMarker()
                }
                .allowOverlap(true)
                .allowOverlapWithPuck(true)
            }
        }
        .onCameraChanged { context in
            let newBearing = context.cameraState.bearing
            Task { @MainActor in
                mapBearing = newBearing
            }
        }
        .ignoresSafeArea()
        .onReceive(Timer.publish(every: 0.1, on: .main, in: .common).autoconnect()) { _ in
            powerUpPulseClock = Date().timeIntervalSinceReferenceDate
        }
        .onChange(of: store.mapCircle) { _, newCircle in
            guard let center = newCircle?.center, let radius = newCircle?.radius else { return }
            withViewportAnimation(.default(maxDuration: 1)) {
                viewport = .camera(center: center, zoom: zoomForRadius(radius, latitude: center.latitude))
            }
        }
        .overlay(alignment: .topTrailing) {
            VStack(spacing: 0) {
                MapCompassButton(mapCircle: store.mapCircle, mapBearing: mapBearing, viewport: $viewport)
                if store.game.powerUps.enabled {
                    ActivePowerUpBadge(game: store.game, now: store.nowDate)
                }
            }
        }
    }
}
