//
//  PowerUpInventorySheet.swift
//  PouleParty
//

import SwiftUI

struct PowerUpInventorySheet: View {
    let powerUps: [PowerUp]
    let onActivate: (PowerUp) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                if powerUps.isEmpty {
                    Text("No power-ups collected yet")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(powerUps) { powerUp in
                        HStack {
                            Image(systemName: powerUp.type.iconName)
                                .font(.title2)
                                .foregroundStyle(powerUp.type.color)
                                .frame(width: 40)
                            VStack(alignment: .leading) {
                                Text(powerUp.type.displayName)
                                    .font(.headline)
                                if let duration = powerUp.type.durationSeconds {
                                    Text("Duration: \(Int(duration))s")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                } else {
                                    Text("Instant")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            Button("Activate") {
                                onActivate(powerUp)
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(powerUp.type.color)
                        }
                    }
                }
            }
            .navigationTitle("Power-Ups")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }
}
