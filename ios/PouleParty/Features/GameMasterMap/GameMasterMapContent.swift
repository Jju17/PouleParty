//
//  GameMasterMapContent.swift
//  PouleParty
//
//  Mapbox map for the GameMaster role. Renders the live chicken,
//  every hunter, the shrinking zone and any spawned power-ups in
//  read-only mode (no proximity / collection logic).
//

import ComposableArchitecture
import CoreLocation
import MapboxMaps
import SwiftUI

struct GameMasterMapContent: View {
    @Bindable var store: StoreOf<GameMasterMapFeature>

    @State private var viewport: Viewport = .camera(
        center: CLLocationCoordinate2D(
            latitude: AppConstants.defaultLatitude,
            longitude: AppConstants.defaultLongitude
        ),
        zoom: 14
    )
    @State private var mapBearing: Double = 0
    @State private var powerUpPulseClock: TimeInterval = 0

    var body: some View {
        Map(viewport: $viewport) {
            if let circle = store.mapCircle {
                zoneOverlayContent(circle: circle, overlayColor: UIColor.black.withAlphaComponent(0.3))
            }
            if let finalLocation = store.game.finalLocation {
                finalZoneGlowContent(center: finalLocation)
            }
            if let chicken = store.chickenLocation {
                MapViewAnnotation(coordinate: chicken) {
                    GMChickenMarker(isInvisible: store.chickenIsInvisible)
                }
                .allowOverlap(true)
                .allowOverlapWithPuck(true)
            }
            ForEvery(store.hunterAnnotations) { hunter in
                MapViewAnnotation(coordinate: hunter.coordinate) {
                    HunterMapMarker(displayName: hunter.displayName)
                }
                .allowOverlap(true)
                .allowOverlapWithPuck(true)
            }
            if store.hasGameStarted {
                powerUpsMapContent(
                    powerUps: store.powerUpAnnotations,
                    pulseAlpha: powerUpPulseAlpha(at: powerUpPulseClock)
                ) { _ in
                    // GM cannot collect — tap is no-op.
                }
            }
        }
        .onCameraChanged { context in
            let newBearing = context.cameraState.bearing
            Task { @MainActor in mapBearing = newBearing }
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
            MapCompassButton(mapCircle: store.mapCircle, mapBearing: mapBearing, viewport: $viewport)
        }
    }
}

private struct GMChickenMarker: View {
    let isInvisible: Bool

    var body: some View {
        ZStack {
            Circle()
                .fill(Color.chickenYellow)
                .frame(width: 40, height: 40)
                .opacity(isInvisible ? 0.45 : 1)
            Text("🐔")
                .font(.system(size: 22))
                .opacity(isInvisible ? 0.65 : 1)
            if isInvisible {
                Circle()
                    .strokeBorder(.white, style: StrokeStyle(lineWidth: 2, dash: [4, 3]))
                    .frame(width: 44, height: 44)
            }
        }
        .shadow(color: .black.opacity(0.3), radius: 4, y: 2)
    }
}
