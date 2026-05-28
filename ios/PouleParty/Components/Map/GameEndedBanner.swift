//
//  GameEndedBanner.swift
//  PouleParty
//
//  Shared "Game ended → tap to see leaderboard" banner shown on top
//  of every active map (chicken / hunter / GameMaster) once the game
//  reaches `status == .done`. The map stays on screen; tapping the
//  banner is the only path forward — it sends the parent reducer a
//  delegate that flips the AppFeature state to the Victory page
//  (which has the canonical "Back to menu" CTA).
//

import SwiftUI

struct GameEndedBanner: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                Image(systemName: "trophy.fill")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(.white)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Game ended")
                        .font(.headline.bold())
                        .foregroundStyle(.white)
                    Text("Tap to see the leaderboard")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.85))
                }
                Spacer(minLength: 8)
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.white.opacity(0.85))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(
                LinearGradient(
                    colors: [Color.CROrange, Color.CRPink],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .neonGlow(Color.CROrange, intensity: .subtle)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Game ended. Tap to see the leaderboard.")
    }
}

#Preview {
    ZStack {
        Color.gray.opacity(0.3).ignoresSafeArea()
        VStack {
            GameEndedBanner(onTap: {})
                .padding()
            Spacer()
        }
    }
}
