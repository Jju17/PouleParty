//
//  MapBottomBar.swift
//  PouleParty
//
//  Shared bottom bar for both map screens: radius + countdown on the left,
//  power-up inventory shortcut + primary action button on the right.
//

import SwiftUI

struct MapBottomBar<S: MapFeatureState>: View {
    let state: S
    /// Whether the main action button should be shown. Chicken always shows it;
    /// hunter only after game has started.
    let isActionButtonVisible: Bool
    /// Accessibility label for the primary action (e.g. "I have been found" /
    /// "I found the chicken"). The visible title stays "FOUND" for both.
    let actionAccessibilityLabel: String
    let onActionTapped: () -> Void
    let onInventoryTapped: () -> Void
    /// Whether the chicken's start date should drive the countdown (true for chicken).
    let isChicken: Bool

    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text("Radius : \(state.radius)m")
                    .font(.gameboy(size: 14))
                    .foregroundStyle(.white)
                    .accessibilityLabel("Radius \(state.radius) meters")
                CountdownView(
                    nowDate: .constant(state.nowDate),
                    nextUpdateDate: .constant(state.nextRadiusUpdate),
                    chickenStartDate: state.game.startDate,
                    hunterStartDate: state.game.hunterStartDate,
                    isChicken: isChicken
                )
            }
            Spacer()
            if !state.collectedPowerUps.isEmpty {
                Button(action: onInventoryTapped) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color.CROrange)
                        HStack(spacing: 2) {
                            Image(systemName: "bolt.fill")
                                .font(.system(size: 10))
                            Text("\(state.collectedPowerUps.count)")
                                .font(.system(size: 11, weight: .bold))
                        }
                        .foregroundStyle(.white)
                    }
                }
                .accessibilityLabel("Power-ups inventory")
                .frame(width: 44, height: 40)
                .neonGlow(.CROrange, intensity: .subtle)
            }
            if isActionButtonVisible {
                Button(action: onActionTapped) {
                    ZStack {
                        Capsule()
                            .fill(Color.hunterRed)
                        Text("FOUND")
                            .font(Font.system(size: 11))
                            .fontWeight(.bold)
                            .foregroundStyle(.white)
                    }
                }
                .accessibilityLabel(actionAccessibilityLabel)
                .frame(width: 50, height: 40)
                .neonGlow(.hunterRed, intensity: .subtle)
            }
        }
        .padding()
        .background(Color.darkBackground.opacity(0.85))
    }
}
