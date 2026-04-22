//
//  SettingsSections.swift
//  PouleParty
//
//  Five sections of the Settings screen, extracted from the monolithic
//  SettingsView so each block lives in a focused, reviewable struct.
//

import SwiftUI

// MARK: - Nickname

struct SettingsNicknameSection: View {
    @Binding var text: String
    var isFocused: FocusState<Bool>.Binding
    let onSubmit: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("Nickname", systemImage: "person")
                .font(.banger(size: 18))
                .foregroundStyle(Color.onBackground)

            TextField("Your nickname", text: $text)
                .font(.banger(size: 22))
                .foregroundStyle(Color.onBackground)
                .multilineTextAlignment(.center)
                .padding()
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.surface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.onBackground.opacity(0.2), lineWidth: 1)
                )
                .focused(isFocused)
                .submitLabel(.done)
                .onChange(of: text) { _, newValue in
                    if newValue.count > AppConstants.nicknameMaxLength {
                        text = String(newValue.prefix(AppConstants.nicknameMaxLength))
                    }
                }
                .onSubmit { onSubmit(text) }

            BangerText("\(text.count)/\(AppConstants.nicknameMaxLength)", size: 14)
                .foregroundStyle(Color.onBackground.opacity(0.4))
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .settingsCard()
    }
}

// MARK: - My Games

struct SettingsMyGamesSection: View {
    let isLoading: Bool
    let games: [MyGame]
    let onTap: (MyGame) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("My Games", systemImage: "gamecontroller")
                .font(.banger(size: 18))
                .foregroundStyle(Color.onBackground)

            if isLoading {
                HStack {
                    Spacer()
                    ProgressView()
                        .tint(Color.CROrange)
                    Spacer()
                }
                .padding(.vertical, 12)
            } else if games.isEmpty {
                Text("No games yet")
                    .font(.gameboy(size: 8))
                    .foregroundStyle(Color.onBackground.opacity(0.4))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(games.enumerated()), id: \.element.id) { index, myGame in
                        if index > 0 {
                            Divider().padding(.horizontal, 14)
                        }
                        Button { onTap(myGame) } label: {
                            GameRowView(myGame: myGame)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .settingsCard()
    }
}

// MARK: - Links

struct SettingsLinksSection: View {
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(spacing: 0) {
            SettingsRow(icon: "hand.raised", title: "Privacy Policy") {
                if let url = URL(string: "https://pouleparty.be/privacy") {
                    openURL(url)
                }
            }
            Divider().padding(.horizontal, 14)
            SettingsRow(icon: "doc.text", title: "Terms of Use") {
                if let url = URL(string: "https://pouleparty.be/terms") {
                    openURL(url)
                }
            }
            Divider().padding(.horizontal, 14)
            SettingsRow(icon: "envelope", title: "Contact Support") {
                if let url = URL(string: "mailto:julien@rahier.dev") {
                    openURL(url)
                }
            }
        }
        .settingsCard(padding: 0)
    }
}

// MARK: - Danger zone

struct SettingsDangerSection: View {
    let onDelete: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button(action: onDelete) {
                HStack {
                    Image(systemName: "trash")
                        .font(.banger(size: 18))
                    BangerText("Delete My Data", size: 18)
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.danger.opacity(0.85))
                )
            }

            BangerText("This will delete your anonymous account and all associated data. A new anonymous account will be created automatically.", size: 13)
                .foregroundStyle(Color.onBackground.opacity(0.4))
        }
        .settingsCard()
    }
}

// MARK: - Version

struct SettingsVersionSection: View {
    var body: some View {
        VStack(spacing: 4) {
            HStack {
                BangerText("Version", size: 16)
                    .foregroundStyle(Color.onBackground)
                Spacer()
                BangerText(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "—", size: 16)
                    .foregroundStyle(Color.onBackground.opacity(0.4))
            }
            HStack {
                BangerText("Build", size: 14)
                    .foregroundStyle(Color.onBackground.opacity(0.4))
                Spacer()
                BangerText(Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "—", size: 14)
                    .foregroundStyle(Color.onBackground.opacity(0.4))
            }
        }
        .settingsCard()
    }
}

// MARK: - Row helper

struct SettingsRow: View {
    let icon: String
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                Image(systemName: icon)
                    .font(.banger(size: 18))
                    .frame(width: 24)
                BangerText(title, size: 18)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.onBackground.opacity(0.3))
            }
            .foregroundStyle(Color.onBackground)
            .padding(14)
        }
    }
}
