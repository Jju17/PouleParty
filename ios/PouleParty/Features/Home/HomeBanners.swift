//
//  HomeBanners.swift
//  PouleParty
//
//  Three banners surfaced from `HomeView`: pending-registration, its
//  collapsed tab variant, and the rejoin-active-game banner. Extracted
//  so HomeView's body stays focused on layout.
//

import SwiftUI

/// Expanded banner shown when the user has a pending registration.
/// Tapping the body invokes `onJoin`; the chevron collapses via `onCollapse`.
struct PendingRegistrationBanner: View {
    let pending: PendingRegistration
    let onJoin: () -> Void
    let onCollapse: () -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            VStack(spacing: 12) {
                Text(pending.isFinished ? "Game ended" : "Registered to game")
                    .font(.gameboy(size: 12))
                    .foregroundStyle(.white)

                Text(pending.gameCode)
                    .font(.gameboy(size: 20))
                    .foregroundStyle(.white)
                Text(pending.teamName)
                    .font(.gameboy(size: 9))
                    .foregroundStyle(.white.opacity(0.8))
                if !pending.isFinished {
                    Text("Starting in \(pending.startDate, style: .relative)")
                        .font(.gameboy(size: 9))
                        .foregroundStyle(.white.opacity(0.8))
                }

                Button(action: onJoin) {
                    Text(pending.isFinished ? "View results" : "Join")
                        .font(.gameboy(size: 16))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(.white, lineWidth: 3)
                        )
                }
                .accessibilityLabel("Open registered game")
            }
            .padding(20)
            .frame(maxWidth: .infinity)

            Button(action: onCollapse) {
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(8)
            }
            .accessibilityLabel("Collapse")
            .padding(8)
        }
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.gradientFire)
                .shadow(color: .black.opacity(0.2), radius: 4, y: 2)
        )
        .padding(.horizontal, 24)
        .transition(.move(edge: .trailing).combined(with: .opacity))
    }
}

/// Collapsed tab on the trailing edge that the user can tap to expand
/// the pending-registration banner back.
struct CollapsedPendingRegistrationTab: View {
    let onExpand: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Spacer(minLength: 0)
            Button(action: onExpand) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(.white)
                    .padding(.vertical, 16)
                    .padding(.leading, 14)
                    .padding(.trailing, 10)
                    .background(
                        UnevenRoundedRectangle(
                            topLeadingRadius: 16,
                            bottomLeadingRadius: 16,
                            bottomTrailingRadius: 0,
                            topTrailingRadius: 0
                        )
                        .fill(Color.gradientFire)
                        .shadow(color: .black.opacity(0.25), radius: 4, x: -2, y: 2)
                    )
            }
            .accessibilityLabel("Expand registered game banner")
        }
        .frame(maxWidth: .infinity, minHeight: 190, alignment: .topTrailing)
        .transition(.move(edge: .trailing))
    }
}

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
