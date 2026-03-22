//
//  PowerUpNotificationBanner.swift
//  PouleParty
//
//  Shared power-up notification banner for map screens.
//

import SwiftUI

struct PowerUpNotificationBanner: View {
    let notification: String?
    let powerUpType: PowerUp.PowerUpType?

    var body: some View {
        if let notification {
            Text(notification)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background((powerUpType?.color ?? Color.CROrange).opacity(0.9))
                .neonGlow(powerUpType?.color ?? .CROrange, intensity: .subtle)
                .clipShape(Capsule())
                .padding(.top, 100)
                .transition(.move(edge: .top).combined(with: .opacity))
                .animation(.easeInOut, value: notification)
        }
    }
}
