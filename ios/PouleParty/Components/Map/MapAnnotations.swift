//
//  MapAnnotations.swift
//  PouleParty
//
//  Reusable label views for Mapbox MapViewAnnotation instances.
//  The annotation wrapping stays in each map feature; only the
//  visual content is centralized here.
//

import SwiftUI

/// Button rendered inside a power-up map annotation.
/// Tapping invokes [onTap] (typically to present the power-up detail sheet).
struct PowerUpMapMarker: View {
    let powerUp: PowerUp
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Image(systemName: powerUp.type.iconName)
                .font(.system(size: 20))
                .foregroundStyle(.white)
                .padding(8)
                .background(powerUp.type.color)
                .clipShape(Circle())
                .shadow(color: powerUp.type.color.opacity(0.5), radius: 6, y: 2)
        }
    }
}

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

/// Emoji marker rendered on hunter maps while the decoy power-up is active.
struct DecoyMapMarker: View {
    var body: some View {
        Text("🐔")
            .font(.system(size: 28))
            .shadow(color: .black.opacity(0.3), radius: 4, y: 2)
    }
}
