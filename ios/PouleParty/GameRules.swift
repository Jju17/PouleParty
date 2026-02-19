//
//  GameRules.swift
//  PouleParty
//
//  Created by Julien Rahier on 17/02/2026.
//

import SwiftUI

struct GameRulesView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                howToPlaySection
                gameModesSection
                settingsSection
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 16)
        }
        .scrollContentBackground(.hidden)
        .background(Color.CRBeige)
        .navigationTitle("Rules")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Color.CRBeige, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.light, for: .navigationBar)
    }

    // MARK: - How to play

    private var howToPlaySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("How to play")
                .font(.banger(size: 32))
                .foregroundStyle(.black)

            VStack(alignment: .leading, spacing: 10) {
                ruleRow(icon: "person.fill", text: "One player is the Chicken, the others are Hunters.")
                ruleRow(icon: "map.fill", text: "The game takes place in a circular zone on the map.")
                ruleRow(icon: "arrow.down.right.and.arrow.up.left", text: "The zone shrinks over time based on game settings.")
                ruleRow(icon: "trophy.fill", text: "Hunters win by finding the Chicken. The Chicken wins by surviving until the end!")
            }
        }
    }

    // MARK: - Game Modes

    private var gameModesSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Game Modes")
                .font(.banger(size: 32))
                .foregroundStyle(.black)

            gameModeCard(
                title: Game.GameMod.followTheChicken.title,
                description: "The Hunters see a circle that follows the Chicken's position in real time. The Chicken must run and hide!",
                details: [
                    "Chicken sends its position to all Hunters",
                    "Hunters see the zone move with the Chicken",
                    "Chicken does NOT see the Hunters"
                ]
            )

            gameModeCard(
                title: Game.GameMod.stayInTheZone.title,
                description: "The zone stays fixed on the map and shrinks over time. Everyone must stay inside!",
                details: [
                    "No position sharing between players",
                    "The zone is centered on the starting location",
                    "Strategy: stay hidden inside the shrinking zone"
                ]
            )

            gameModeCard(
                title: Game.GameMod.mutualTracking.title,
                description: "Like Follow the Chicken, but the Chicken can also see all Hunters on her map!",
                details: [
                    "Chicken sends its position to Hunters",
                    "Hunters send their position to the Chicken",
                    "Both sides can track each other in real time"
                ]
            )
        }
    }

    // MARK: - Settings

    private var settingsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Game Settings")
                .font(.banger(size: 32))
                .foregroundStyle(.black)

            VStack(alignment: .leading, spacing: 10) {
                settingRow(
                    name: "Start / End time",
                    explanation: "When the game starts and ends. The Chicken wins if the time runs out!"
                )
                settingRow(
                    name: "Radius interval update",
                    explanation: "How often the zone shrinks (in minutes)."
                )
                settingRow(
                    name: "Radius decline",
                    explanation: "How many meters the zone shrinks each update."
                )
                settingRow(
                    name: "Map setup",
                    explanation: "Choose the starting location and initial radius of the zone."
                )
            }
        }
    }

    // MARK: - Components

    private func ruleRow(icon: String, text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon)
                .foregroundStyle(Color.CROrange)
                .frame(width: 20)
            Text(text)
                .font(.gameboy(size: 10))
                .foregroundStyle(.black)
        }
    }

    private func gameModeCard(title: String, description: String, details: [String]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.banger(size: 22))
                .foregroundStyle(.black)

            Text(description)
                .font(.gameboy(size: 8))
                .foregroundStyle(.black.opacity(0.7))

            ForEach(details, id: \.self) { detail in
                HStack(alignment: .top, spacing: 6) {
                    Text(">")
                        .font(.gameboy(size: 8))
                        .foregroundStyle(Color.CROrange)
                    Text(detail)
                        .font(.gameboy(size: 8))
                        .foregroundStyle(.black)
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.CROrange, lineWidth: 2)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.CROrange.opacity(0.1))
                )
        )
    }

    private func settingRow(name: String, explanation: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(name)
                .font(.gameboy(size: 10))
                .foregroundStyle(Color.CROrange)
            Text(explanation)
                .font(.gameboy(size: 8))
                .foregroundStyle(.black.opacity(0.7))
        }
    }
}

#Preview {
    NavigationStack {
        GameRulesView()
    }
}
