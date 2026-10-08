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
        .background(Color.gradientBackgroundWarmth)
        .navigationTitle("Rules")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Color.background, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
    }

    // MARK: - How to play

    private var howToPlaySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            BangerText("How to play", size: 32)
                .foregroundStyle(Color.onBackground)

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
            BangerText("Game Modes", size: 32)
                .foregroundStyle(Color.onBackground)

            gameModeCard(
                title: Game.GameMode.followTheChicken.title,
                description: "The zone shrinks periodically toward the Chicken's position. Hunters don't see the Chicken's exact location, only the zone moving!",
                details: [
                    "The zone center follows the Chicken's live position",
                    "Hunters see the zone move but not where the Chicken is",
                    "The Chicken must stay inside the zone to survive"
                ]
            )

            gameModeCard(
                title: Game.GameMode.stayInTheZone.title,
                description: "The zone shrinks and drifts randomly around the starting point. Hunters must find the Chicken with no position clues!",
                details: [
                    "No position sharing between players",
                    "The zone drifts toward the final zone point set by the creator",
                    "Strategy: search the moving zone to find the Chicken!"
                ]
            )

            gameModeCard(
                title: String(localized: "Chicken can see hunters 👀"),
                description: "An option available in any game mode. The Chicken can see all Hunters on her map!",
                details: [
                    "Hunters send their position to the Chicken",
                    "The Chicken sees all Hunter positions in real time",
                    "Choose this option when creating the game"
                ]
            )
        }
    }

    // MARK: - Settings

    private var settingsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            BangerText("Game Settings", size: 32)
                .foregroundStyle(Color.onBackground)

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
                    explanation: "How many metres the zone shrinks each update."
                )
                settingRow(
                    name: "Map setup",
                    explanation: "Choose the starting location and initial radius of the zone."
                )
            }
        }
    }

    // MARK: - Components

    private func ruleRow(icon: String, text: LocalizedStringKey) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon)
                .foregroundStyle(Color.CROrange)
                .frame(width: 20)
            Text(text)
                .font(.system(.subheadline))
                .foregroundStyle(Color.onBackground)
        }
    }

    private func gameModeCard(title: String, description: LocalizedStringKey, details: [LocalizedStringKey]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            BangerText(title, size: 22)
                .foregroundStyle(Color.onBackground)

            Text(description)
                .font(.system(.subheadline))
                .foregroundStyle(Color.onBackground.opacity(0.7))

            ForEach(details.indices, id: \.self) { index in
                let detail = details[index]
                HStack(alignment: .top, spacing: 6) {
                    Text(">")
                        .font(.system(.subheadline, weight: .bold))
                        .foregroundStyle(Color.CROrange)
                    Text(detail)
                        .font(.system(.subheadline))
                        .foregroundStyle(Color.onBackground)
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.CROrange, lineWidth: 2)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.CROrange.opacity(0.1))
                )
        )
    }

    private func settingRow(name: LocalizedStringKey, explanation: LocalizedStringKey) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(name)
                .font(.gameboy(size: 10))
                .foregroundStyle(Color.CROrange)
            Text(explanation)
                .font(.system(.subheadline))
                .foregroundStyle(Color.onBackground.opacity(0.7))
        }
    }
}

#Preview {
    NavigationStack {
        GameRulesView()
    }
}
