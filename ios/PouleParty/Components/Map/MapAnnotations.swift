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

/// Avatar rendered for a hunter on the chicken's map when
/// `chickenCanSeeHunters` is enabled.
struct HunterMapMarker: View {
    let displayName: String

    var body: some View {
        VStack(spacing: 2) {
            Text(displayName)
                .font(.caption2)
                .fontWeight(.semibold)
            Image(systemName: "figure.walk")
                .foregroundStyle(.white)
                .padding(6)
                .background(Color.CROrange)
                .clipShape(Circle())
        }
    }
}
