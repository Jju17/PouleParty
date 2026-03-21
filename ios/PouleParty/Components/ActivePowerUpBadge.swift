//
//  ActivePowerUpBadge.swift
//  PouleParty
//
//  Shows active power-up effects as small badges below the compass.
//  Tapping a badge expands it to show details (name + remaining time).
//  Visible to all players (chicken and hunters).
//

import SwiftUI
import FirebaseFirestore

struct ActivePowerUpBadge: View {
    let game: Game
    @State private var expandedType: PowerUp.PowerUpType?
    @State private var now = Date.now

    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    private var activeEffects: [(type: PowerUp.PowerUpType, until: Date)] {
        var effects: [(PowerUp.PowerUpType, Date)] = []
        if let ts = game.activeInvisibilityUntil, now < ts.dateValue() {
            effects.append((.invisibility, ts.dateValue()))
        }
        if let ts = game.activeZoneFreezeUntil, now < ts.dateValue() {
            effects.append((.zoneFreeze, ts.dateValue()))
        }
        if let ts = game.activeRadarPingUntil, now < ts.dateValue() {
            effects.append((.radarPing, ts.dateValue()))
        }
        return effects
    }

    var body: some View {
        if !activeEffects.isEmpty {
            VStack(spacing: 6) {
                ForEach(activeEffects, id: \.type) { effect in
                    badgeView(for: effect.type, until: effect.until)
                }
            }
            .padding(.trailing, 8)
            .padding(.top, 4)
            .onReceive(timer) { _ in
                now = .now
            }
        }
    }

    @ViewBuilder
    private func badgeView(for type: PowerUp.PowerUpType, until: Date) -> some View {
        let isExpanded = expandedType == type
        let remaining = max(0, Int(until.timeIntervalSince(now)))

        Button {
            withAnimation(.spring(response: 0.3)) {
                expandedType = isExpanded ? nil : type
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: type.iconName)
                    .font(.system(size: 16))
                    .foregroundStyle(.white)

                if isExpanded {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(type.displayName)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(.white)
                        Text("\(remaining)s remaining")
                            .font(.system(size: 10))
                            .foregroundStyle(.white.opacity(0.8))
                    }
                }
            }
            .padding(.horizontal, isExpanded ? 10 : 0)
            .frame(width: isExpanded ? nil : 36, height: 36)
            .background(badgeColor(for: type))
            .clipShape(RoundedRectangle(cornerRadius: isExpanded ? 18 : 18))
            .shadow(color: .black.opacity(0.2), radius: 4, y: 2)
        }
    }

    private func badgeColor(for type: PowerUp.PowerUpType) -> Color {
        switch type {
        case .invisibility: return .purple
        case .zoneFreeze: return .cyan
        case .radarPing: return .orange
        case .zonePreview: return .blue
        }
    }
}
