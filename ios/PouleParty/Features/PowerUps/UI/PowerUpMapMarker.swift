//
//  PowerUpMapMarker.swift
//  PouleParty
//
//  Button rendered inside a power-up map annotation.
//  Also hosts the `DecoyMapMarker` which is the visual marker for the decoy
//  power-up on the hunter's map.
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
                .font(.system(size: 12))
                .foregroundStyle(.white)
                .padding(5)
                .background(powerUp.type.color)
                .clipShape(Circle())
                .shadow(color: powerUp.type.color.opacity(0.5), radius: 4, y: 1)
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
