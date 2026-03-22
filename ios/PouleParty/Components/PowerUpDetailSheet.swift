//
//  PowerUpDetailSheet.swift
//  PouleParty
//

import SwiftUI

struct PowerUpDetailSheet: View {
    let powerUpType: PowerUp.PowerUpType

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: powerUpType.iconName)
                .font(.system(size: 36))
                .foregroundStyle(powerUpType.color)

            Text(powerUpType.displayName)
                .font(.headline)

            Text("\(powerUpType.targetEmoji) \(powerUpType.targetLabel)")
                .font(.caption)
                .fontWeight(.semibold)
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(Capsule().fill(Color(.systemGray5)))

            Text(powerUpType.description)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            if let duration = powerUpType.durationSeconds {
                Text("Duration: \(Int(duration))s")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding()
        .presentationDragIndicator(.visible)
    }
}
