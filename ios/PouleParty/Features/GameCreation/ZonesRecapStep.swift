//
//  ZonesRecapStep.swift
//  PouleParty
//
//  PP-13 phase 1 — third sub-step of the zone setup flow. Renders a
//  read-only map preview of the initial circle + every future shrunk
//  circle (rainbow palette, numbered) so the chicken can sanity-check
//  the trajectory before locking in the timing. PP-14 phase 1 ships
//  the Shuffle button inside the same view (stayInTheZone only).
//
//  Phase 2 (post-PP-69) will swap the client-side `computeZoneRadius`
//  + `computeDebugShiftedCircles` calls for a CF response.
//

import ComposableArchitecture
import CoreLocation
import MapboxMaps
import SwiftUI

struct ZonesRecapStep: GameCreationStepView {
    static let step: GameCreationStep = .zonesRecap
    @Bindable var store: StoreOf<GameCreationFeature>

    @State private var viewport: Viewport = .camera(
        center: CLLocationCoordinate2D(
            latitude: AppConstants.defaultLatitude,
            longitude: AppConstants.defaultLongitude
        ),
        zoom: 13
    )

    private var previewCircles: [DebugShrinkCircle] {
        computeDebugShiftedCircles(game: store.currentGame)
    }

    private var initialRadius: Double { store.currentGame.zone.radius }
    private var initialCenter: CLLocationCoordinate2D { store.currentGame.initialLocation }
    private var startPin: CLLocationCoordinate2D { store.currentGame.startPinLocation }
    private var finalCenter: CLLocationCoordinate2D? { store.currentGame.finalLocation }
    private var isStayInTheZone: Bool { store.currentGame.gameMode == .stayInTheZone }

    var body: some View {
        ZStack {
            Map(viewport: $viewport) {
                initialRingContent
                shrinkCirclesContent
                finalGlowContent
                badgeAnnotationsContent
                pinAnnotationsContent
            }
            .mapStyle(.streets)
            .ignoresSafeArea()

            shuffleButton
        }
        .onAppear {
            store.send(.zonesRecapEntered)
            // Defer focus to the next runloop tick so the
            // `zonesRecapEntered` mutation (radius + drift seed +
            // computed center) is propagated into our `initialCenter`
            // / `initialRadius` reads before we recenter — otherwise
            // we'd fit on the stale defaults from the previous step.
            Task { @MainActor in
                focusViewport()
            }
        }
        .onChange(of: initialCenter.latitude) { _, _ in focusViewport() }
        .onChange(of: initialCenter.longitude) { _, _ in focusViewport() }
        .onChange(of: initialRadius) { _, _ in focusViewport() }
    }

    // MARK: - Map content (split to keep each closure small enough for
    // the SwiftUI / MapContent type checker)

    @MapContentBuilder
    private var initialRingContent: some MapContent {
        let polygon = Polygon(center: initialCenter, radius: initialRadius, vertices: 72)
        let coords = polygon.outerRing.coordinates
        PolylineAnnotation(lineCoordinates: coords)
            .lineColor(StyleColor(UIColor(Color.CROrange).withAlphaComponent(0.15)))
            .lineWidth(8)
        PolylineAnnotation(lineCoordinates: coords)
            .lineColor(StyleColor(UIColor(Color.CROrange).withAlphaComponent(0.35)))
            .lineWidth(4)
        PolylineAnnotation(lineCoordinates: coords)
            .lineColor(StyleColor(UIColor(Color.CROrange).withAlphaComponent(0.9)))
            .lineWidth(2)
    }

    @MapContentBuilder
    private var shrinkCirclesContent: some MapContent {
        if isStayInTheZone {
            zonePreviewCirclesContent(circles: previewCircles)
        }
    }

    @MapContentBuilder
    private var finalGlowContent: some MapContent {
        if isStayInTheZone, let finalCenter {
            PouleParty.finalZoneGlowContent(center: finalCenter)
        }
    }

    @MapContentBuilder
    private var badgeAnnotationsContent: some MapContent {
        // Each badge lands on its circle's outline at a stable
        // pseudo-random angle in the NW quadrant (north → west) so
        // the chicken always knows where to look. Angle is a pure
        // function of `displayIndex` — Shuffle doesn't move the
        // badges, only the circles themselves.
        MapViewAnnotation(coordinate: badgeAnchor(
            center: initialCenter,
            radius: initialRadius,
            displayIndex: 1
        )) {
            ShrinkOrderBadge(index: 1, color: Color.CROrange)
        }
        .allowOverlap(true)

        if isStayInTheZone {
            ForEvery(Array(previewCircles.enumerated()), id: \.offset) { pair in
                let badgeCoord = badgeAnchor(
                    center: pair.element.center,
                    radius: pair.element.radius,
                    displayIndex: pair.offset + 2
                )
                MapViewAnnotation(coordinate: badgeCoord) {
                    ShrinkOrderBadge(
                        index: pair.offset + 2,
                        color: zonePreviewColor(
                            forIndex: pair.offset,
                            totalCount: previewCircles.count
                        )
                    )
                }
                .allowOverlap(true)
            }
        }
    }

    /// Places the numbered badge on the circle's outline at a
    /// stable pseudo-random angle in the **NW quadrant**
    /// (270° → 360°, i.e. west through north). Same circle index
    /// → same bearing on every render, so the chicken's eye
    /// learns where each badge sits.
    private func badgeAnchor(
        center: CLLocationCoordinate2D,
        radius: Double,
        displayIndex: Int
    ) -> CLLocationCoordinate2D {
        let bearingDeg = badgeBearingDegrees(displayIndex: displayIndex)
        let bearingRad = bearingDeg * .pi / 180
        let metersPerDegLat = 111_111.0
        let cosLat = cos(center.latitude * .pi / 180)
        let metersPerDegLng = cosLat == 0 ? metersPerDegLat : 111_111 * cosLat
        let dLat = (radius * cos(bearingRad)) / metersPerDegLat
        let dLng = (radius * sin(bearingRad)) / metersPerDegLng
        return CLLocationCoordinate2D(
            latitude: center.latitude + dLat,
            longitude: center.longitude + dLng
        )
    }

    /// Pure-function bearing in `[270°, 360°)` (north-west quadrant)
    /// derived from `displayIndex` alone via splitmix64.
    private func badgeBearingDegrees(displayIndex: Int) -> Double {
        var state = UInt64(displayIndex) &* 0x9E3779B97F4A7C15
        if state == 0 { state = 1 }
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        z = z ^ (z >> 31)
        let r = Double(z) / Double(UInt64.max)
        return 270 + r * 90
    }

    @MapContentBuilder
    private var pinAnnotationsContent: some MapContent {
        MapViewAnnotation(coordinate: startPin) {
            ZoneRecapPinLabel(text: "START", background: Color.CROrange, textColor: .white)
        }
        .allowOverlap(true)

        if isStayInTheZone, let finalCenter {
            MapViewAnnotation(coordinate: finalCenter) {
                ZoneRecapPinLabel(text: "FINAL", background: Color.zoneGreen, textColor: .black)
            }
            .allowOverlap(true)
        }
    }

    // MARK: - Chrome overlays

    @ViewBuilder
    private var shuffleButton: some View {
        if isStayInTheZone {
            VStack {
                Spacer()
                Button {
                    store.send(.shuffleDriftSeed)
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "shuffle")
                        Text("Shuffle")
                            .font(.gameboy(size: 12))
                    }
                    .foregroundStyle(.black)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .background(Capsule().fill(Color.CROrange))
                    .shadow(color: .black.opacity(0.2), radius: 4, y: 2)
                }
                .padding(.bottom, 24)
            }
        }
    }

    private func focusViewport() {
        // Mapbox's `withViewportAnimation` sometimes swallows the
        // update when called from `onAppear` before the binding has
        // committed — set the viewport directly first, then animate.
        let targetZoom = zoomForRadius(initialRadius * 1.15, latitude: initialCenter.latitude)
        let target = Viewport.camera(center: initialCenter, zoom: targetZoom)
        viewport = target
        withViewportAnimation(.default(maxDuration: 0.6)) {
            viewport = target
        }
    }
}

/// PP-13 numbered badge attached to each shrink circle so the chicken
/// reads the shrink order at a glance. The fill colour matches the
/// circle's stroke palette so users can pair label and outline.
private struct ShrinkOrderBadge: View {
    let index: Int
    let color: Color

    var body: some View {
        Text("\(index)")
            .font(.gameboy(size: 9))
            .foregroundStyle(.black)
            .frame(minWidth: 18, minHeight: 18)
            .padding(.horizontal, 4)
            .background(
                Capsule()
                    .fill(color)
                    .shadow(color: .black.opacity(0.25), radius: 2, y: 1)
            )
            .overlay(Capsule().stroke(.white, lineWidth: 1))
    }
}

private struct ZoneRecapPinLabel: View {
    let text: String
    let background: Color
    let textColor: Color

    var body: some View {
        Text(text)
            .font(.gameboy(size: 7))
            .foregroundStyle(textColor)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Capsule().fill(background))
    }
}
