//
//  PowerUpSelectionView.swift
//  PouleParty
//

import SwiftUI

struct PowerUpSelectionView: View {
    let enabledTypes: [String]
    let gameMod: Game.GameMod
    let onToggle: (PowerUp.PowerUpType) -> Void

    @Environment(\.dismiss) private var dismiss

    private let columns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12),
    ]

    /// Power-ups that have no effect in stayInTheZone (no position sharing)
    private static let unavailableInZone: Set<PowerUp.PowerUpType> = [
        .invisibility, .decoy, .jammer
    ]

    private func isUnavailable(_ type: PowerUp.PowerUpType) -> Bool {
        gameMod == .stayInTheZone && Self.unavailableInZone.contains(type)
    }

    private var chickenPowerUps: [PowerUp.PowerUpType] {
        PowerUp.PowerUpType.allCases.filter { !$0.isHunterPowerUp }
    }

    private var hunterPowerUps: [PowerUp.PowerUpType] {
        PowerUp.PowerUpType.allCases.filter { $0.isHunterPowerUp }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    // Chicken section
                    sectionHeader(title: "Chicken Power-Ups", emoji: "🐔", gradient: Color.gradientChicken)
                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(chickenPowerUps, id: \.self) { type in
                            let unavailable = isUnavailable(type)
                            let isEnabled = !unavailable && enabledTypes.contains(type.rawValue)
                            PowerUpCard(type: type, isEnabled: isEnabled, unavailable: unavailable) {
                                if !unavailable { onToggle(type) }
                            }
                        }
                    }

                    // Hunter section
                    sectionHeader(title: "Hunter Power-Ups", emoji: "🎯", gradient: Color.gradientHunter)
                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(hunterPowerUps, id: \.self) { type in
                            let unavailable = isUnavailable(type)
                            let isEnabled = !unavailable && enabledTypes.contains(type.rawValue)
                            PowerUpCard(type: type, isEnabled: isEnabled, unavailable: unavailable) {
                                if !unavailable { onToggle(type) }
                            }
                        }
                    }
                }
                .padding(16)
            }
            .background(Color.background)
            .navigationTitle("Power-Ups")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private func sectionHeader(title: String, emoji: String, gradient: LinearGradient) -> some View {
        HStack(spacing: 8) {
            Text(emoji)
                .font(.title2)
            BangerText(title, size: 20)
                .foregroundStyle(.white)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(gradient)
        )
    }
}

private struct PowerUpCard: View {
    let type: PowerUp.PowerUpType
    let isEnabled: Bool
    var unavailable: Bool = false
    let onTap: () -> Void

    // Darker variant of the power-up color for the gradient
    private var darkerColor: Color {
        type.color.opacity(0.55)
    }

    var body: some View {
        Button(action: onTap) {
            ZStack {
                // Glossy radial overlay
                if isEnabled {
                    RadialGradient(
                        colors: [.white.opacity(0.25), .clear],
                        center: UnitPoint(x: 0.3, y: 0.3),
                        startRadius: 0,
                        endRadius: 120
                    )
                }

                VStack(spacing: 8) {
                    Text(type.emoji)
                        .font(.system(size: 36))
                        .shadow(color: .black.opacity(0.3), radius: 2, y: 1)
                        .grayscale(unavailable ? 1 : 0)

                    BangerText(type.displayName, size: 18)
                        .foregroundStyle(isEnabled ? type.textColor : .secondary)
                        .shadow(color: isEnabled ? .black.opacity(0.2) : .clear, radius: 2, y: 1)

                    if unavailable {
                        Text("Not available in this mode")
                            .font(.system(size: 8, weight: .medium))
                            .foregroundStyle(.secondary.opacity(0.7))
                            .multilineTextAlignment(.center)
                    } else {
                        Text(type.description)
                            .font(.system(size: 9))
                            .foregroundStyle(isEnabled ? type.textColor.opacity(0.8) : .secondary)
                            .multilineTextAlignment(.center)
                            .lineLimit(3)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    if let duration = type.durationSeconds {
                        Text("\(Int(duration))s")
                            .font(.gameboy(size: 8))
                            .foregroundStyle(isEnabled ? type.textColor.opacity(0.7) : .secondary.opacity(0.5))
                    }
                }
                .padding(.vertical, 16)
                .padding(.horizontal, 10)
            }
            .frame(maxWidth: .infinity, minHeight: 180)
            .background(
                RoundedRectangle(cornerRadius: 18)
                    .fill(
                        isEnabled
                            ? AnyShapeStyle(LinearGradient(
                                colors: [type.color, darkerColor],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                              ))
                            : AnyShapeStyle(Color(.systemGray5))
                    )
            )
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .shadow(color: isEnabled ? type.color.opacity(0.5) : .clear, radius: 7, y: 0)
            .shadow(color: .black.opacity(isEnabled ? 0.2 : 0.05), radius: 4, y: 4)
            .opacity(unavailable ? 0.5 : 1)
        }
        .disabled(unavailable)
    }
}
