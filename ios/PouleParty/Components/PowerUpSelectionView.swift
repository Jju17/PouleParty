//
//  PowerUpSelectionView.swift
//  PouleParty
//

import SwiftUI

struct PowerUpSelectionView: View {
    let enabledTypes: [String]
    let onToggle: (PowerUp.PowerUpType) -> Void

    @Environment(\.dismiss) private var dismiss

    private let columns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12),
    ]

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(PowerUp.PowerUpType.allCases, id: \.self) { type in
                        let isEnabled = enabledTypes.contains(type.rawValue)
                        PowerUpCard(type: type, isEnabled: isEnabled) {
                            onToggle(type)
                        }
                    }
                }
                .padding(16)
            }
            .navigationTitle("Power-Ups")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

private struct PowerUpCard: View {
    let type: PowerUp.PowerUpType
    let isEnabled: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 8) {
                Image(systemName: type.iconName)
                    .font(.title)
                    .foregroundStyle(isEnabled ? .white : .secondary)
                Text(type.displayName)
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundStyle(isEnabled ? .white : .secondary)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(isEnabled ? Color.CROrange : Color(.systemGray5))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(isEnabled ? Color.CROrange : Color(.systemGray3), lineWidth: 1)
            )
        }
    }
}
