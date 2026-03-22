//
//  GameInfoSheet.swift
//  PouleParty
//

import SwiftUI

struct GameInfoSheet: View {
    let game: Game
    var onCancelGame: (() -> Void)? = nil
    var leaveGameLabel: String = "Cancel game"
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Game Code") {
                    GameCodeRow(gameCode: game.gameCode)
                }

                Section("Game Mode") {
                    Text(game.gameMod.title)
                }

                Section("Schedule") {
                    HStack {
                        Text("Start")
                        Spacer()
                        Text(game.startDate, style: .time)
                            .foregroundStyle(.secondary)
                    }
                    HStack {
                        Text("End")
                        Spacer()
                        Text(game.endDate, style: .time)
                            .foregroundStyle(.secondary)
                    }
                }

                if let onCancelGame {
                    Section {
                        Button(role: .destructive) {
                            dismiss()
                            onCancelGame()
                        } label: {
                            HStack {
                                Spacer()
                                Text(leaveGameLabel)
                                Spacer()
                            }
                        }
                    }
                }
            }
            .navigationTitle("Game Info")
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
