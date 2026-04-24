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

/// Real-chicken marker rendered on the Hunter map while Radar Ping is active.
/// Kept visually distinct from `DecoyMapMarker` with a pulsating radar halo —
/// otherwise a decoy and a real ping landing simultaneously would be
/// indistinguishable to the Hunter.
struct ChickenMapMarker: View {
    @State private var halo: CGFloat = 0.6

    var body: some View {
        ZStack {
            Circle()
                .stroke(Color.powerupRadar.opacity(halo), lineWidth: 3)
                .frame(width: 56, height: 56)
            Circle()
                .fill(Color.powerupRadar.opacity(0.25))
                .frame(width: 36, height: 36)
            Text("🐔")
                .font(.system(size: 26))
        }
        .shadow(color: Color.powerupRadar.opacity(0.6), radius: 6)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true)) {
                halo = 1.0
            }
        }
    }
}
