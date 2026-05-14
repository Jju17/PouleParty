//
//  MapAnnotations.swift
//  PouleParty
//
//  Reusable label views for Mapbox MapViewAnnotation instances.
//  The annotation wrapping stays in each map feature; only the
//  visual content is centralized here.
//
//  Power-up-related markers (PowerUpMapMarker, DecoyMapMarker) live in
//  PowerUps/UI/PowerUpMapMarker.swift.
//

import SwiftUI

/// Avatar rendered for a hunter on the chicken's map (when
/// `chickenCanSeeHunters` is enabled) and on the GameMaster map.
/// Same size as `GMChickenMarker` (24 px disc, 13 pt glyph) so every
/// map renders chicken + hunters with equal visual weight.
struct HunterMapMarker: View {
    let displayName: String

    var body: some View {
        VStack(spacing: 1) {
            // White text with a 1-px black outline (4 directional
            // shadows) so the label stays readable on both light and
            // dark map tiles — Mapbox switches palette with the system
            // theme so any single colour would fail on one of the two.
            Text(displayName)
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(.white)
                .shadow(color: .black, radius: 0.5, x: 1, y: 0)
                .shadow(color: .black, radius: 0.5, x: -1, y: 0)
                .shadow(color: .black, radius: 0.5, x: 0, y: 1)
                .shadow(color: .black, radius: 0.5, x: 0, y: -1)
            ZStack {
                Circle()
                    .fill(Color.CROrange)
                    .frame(width: 24, height: 24)
                Image(systemName: "figure.walk")
                    .foregroundStyle(.white)
                    .font(.system(size: 13))
            }
            .shadow(color: .black.opacity(0.3), radius: 3, y: 1)
        }
    }
}
