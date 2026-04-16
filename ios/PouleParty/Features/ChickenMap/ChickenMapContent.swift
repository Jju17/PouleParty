//
//  ChickenMapContent.swift
//  PouleParty
//
//  The Mapbox map itself — zone overlay, power-up markers, hunter
//  annotations and the camera/compass widgets. All other chrome
//  (bars, overlays, sheets) lives on the parent `ChickenMapView`.
//

import ComposableArchitecture
import CoreLocation
import MapboxMaps
import SwiftUI

struct ChickenMapContent: View {
    @Bindable var store: StoreOf<ChickenMapFeature>
    @Binding var selectedPowerUp: PowerUp?

    @State private var viewport: Viewport = .camera(
        center: CLLocationCoordinate2D(
            latitude: AppConstants.defaultLatitude,
            longitude: AppConstants.defaultLongitude
        ),
        zoom: 14
    )
    @State private var mapBearing: Double = 0

    private var overlayColor: UIColor {
        store.isOutsideZone
            ? UIColor(Color.zoneDanger).withAlphaComponent(0.4)
            : UIColor.black.withAlphaComponent(0.3)
    }

    var body: some View {
        Map(viewport: $viewport) {
            Puck2D(bearing: .heading)

            if let circle = store.mapCircle {
                zoneOverlayContent(circle: circle, overlayColor: overlayColor)
            }

            // Final zone glow (always visible for chicken)
            if let finalLocation = store.game.finalLocation {
                finalZoneGlowContent(center: finalLocation)
            }

            // Power-up markers (chicken power-ups only)
            if store.hasGameStarted {
                ForEvery(store.availablePowerUps) { powerUp in
                    MapViewAnnotation(coordinate: powerUp.coordinate) {
                        PowerUpMapMarker(powerUp: powerUp) {
                            selectedPowerUp = powerUp
                        }
                    }
                    .allowOverlap(true)
                    .allowOverlapWithPuck(true)
                }
            }

            // Hunter annotations (chickenCanSeeHunters) -- only after hunt starts
            if store.hasHuntStarted {
                ForEvery(store.hunterAnnotations) { hunter in
                    MapViewAnnotation(coordinate: hunter.coordinate) {
                        HunterMapMarker(displayName: hunter.displayName)
                    }
                }
            }
        }
        .onCameraChanged { context in
            let newBearing = context.cameraState.bearing
            Task { @MainActor in
                mapBearing = newBearing
            }
        }
        .ignoresSafeArea()
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
