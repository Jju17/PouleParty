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
/// Same size as `GMChickenMarker` (34 px disc, 18 pt glyph) so every
/// map renders chicken + hunters with equal visual weight.
struct HunterMapMarker: View {
    let displayName: String

    var body: some View {
        VStack(spacing: 2) {
            Text(displayName)
                .font(.caption2)
                .fontWeight(.semibold)
            ZStack {
                Circle()
                    .fill(Color.CROrange)
                    .frame(width: 34, height: 34)
                Image(systemName: "figure.walk")
                    .foregroundStyle(.white)
                    .font(.system(size: 18))
            }
            .shadow(color: .black.opacity(0.3), radius: 4, y: 2)
        }
    }
}
