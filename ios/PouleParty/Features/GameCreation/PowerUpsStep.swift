//
//  PowerUpsStep.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

struct PowerUpsStep: GameCreationStepView {
    static let step: GameCreationStep = .powerUps
    @Bindable var store: StoreOf<GameCreationFeature>

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            StepHeader(
                title: "Power-Ups",
                subtitle: "Enable special abilities?"
            )

            VStack(spacing: 16) {
                SelectionCard(
                    title: "Power-Ups ON",
                    emoji: "⚡",
                    subtitle: "Collect and use abilities",
                    isSelected: store.currentGame.powerUps.enabled,
                    gradient: Color.gradientFire
                ) {
                    store.send(.powerUpsToggled(true))
                }

                SelectionCard(
                    title: "Power-Ups OFF",
                    emoji: "🚫",
                    subtitle: "Classic mode, no power-ups",
                    isSelected: !store.currentGame.powerUps.enabled,
                    gradient: LinearGradient(colors: [.gray, .gray.opacity(0.7)], startPoint: .topLeading, endPoint: .bottomTrailing)
                ) {
                    store.send(.powerUpsToggled(false))
                }

                if store.currentGame.powerUps.enabled {
                    let unavailableRaw: Set<String> = store.currentGame.gameMode == .stayInTheZone
                        ? [PowerUp.PowerUpType.invisibility.rawValue, PowerUp.PowerUpType.decoy.rawValue, PowerUp.PowerUpType.jammer.rawValue]
                        : []
                    let enabledCount = store.currentGame.powerUps.enabledTypes.filter { !unavailableRaw.contains($0) }.count
                    let totalCount = PowerUp.PowerUpType.allCases.filter { !unavailableRaw.contains($0.rawValue) }.count

                    Button {
                        store.showPowerUpSelection = true
                    } label: {
                        HStack {
                            Text("Choose Power-Ups")
                                .font(.gameboy(size: 10))
                                .foregroundStyle(Color.onBackground)
                            Spacer()
                            Text("\(enabledCount)/\(totalCount)")
                                .font(.gameboy(size: 10))
                                .foregroundStyle(.secondary)
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 14)
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.surface)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color.onBackground.opacity(0.2), lineWidth: 1)
                        )
                    }
                }
            }
            .padding(.horizontal, 24)
            Spacer()
        }
    }
}
