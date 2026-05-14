//
//  HomeBanners.swift
//  PouleParty
//
//  Single banner surfaced from `HomeView`: the rejoin-active-game
//  banner. Pre-PP-90 also hosted PendingRegistration banners; those
//  are gone now that registration is no longer a first-class step.
//

import SwiftUI

/// Banner shown when the user has an active game they can open or rejoin.
///
/// Two phases drive the copy + CTA:
///  - `.inProgress` → "Partie en cours" + "Reprendre"
///  - `.upcoming`   → "Prochaine partie" + "Préparer" (chicken) /
///                    "Rejoindre" (hunter)
///
/// Starting-in countdown is rendered only in the upcoming phase.
struct RejoinGameBanner: View {
    let gameCode: String?
    let phase: GamePhase
    let role: GameRole
    let startDate: Date?
    let onRejoin: () -> Void
    let onDismiss: () -> Void

    private var title: String {
        switch phase {
        case .inProgress: return String(localized: "Game in progress")
        case .upcoming: return String(localized: "Next game")
        }
    }

    private var ctaLabel: String {
        switch (phase, role) {
        case (.inProgress, _): return String(localized: "Rejoin")
        case (.upcoming, .chicken): return String(localized: "Open")
        case (.upcoming, .hunter): return String(localized: "Join")
        case (.upcoming, .gameMaster): return String(localized: "Watch")
        }
    }

    private var accessibilityLabel: String {
        switch phase {
        case .inProgress: return "Rejoin game in progress"
        case .upcoming: return "Open upcoming game"
        }
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            VStack(spacing: 12) {
                Text(title)
                    .font(.gameboy(size: 14))
                    .foregroundStyle(.white)

                if let code = gameCode {
                    Text(code)
                        .font(.gameboy(size: 20))
                        .foregroundStyle(.white)
                }

                if phase == .upcoming, let startDate {
                    Text("Starting in \(startDate, style: .relative)")
                        .font(.gameboy(size: 9))
                        .foregroundStyle(.white.opacity(0.8))
                }

                Button(action: onRejoin) {
                    Text(ctaLabel)
                        .font(.gameboy(size: 16))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(.white, lineWidth: 3)
                        )
                }
                .accessibilityLabel(accessibilityLabel)
            }
            .padding(20)
            .frame(maxWidth: .infinity)

            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(8)
            }
            .accessibilityLabel("Dismiss")
            .padding(8)
        }
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.gradientFire)
                .shadow(color: .black.opacity(0.2), radius: 4, y: 2)
        )
        .padding(.horizontal, 24)
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}
