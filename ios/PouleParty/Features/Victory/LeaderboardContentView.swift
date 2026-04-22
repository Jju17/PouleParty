//
//  LeaderboardContentView.swift
//  PouleParty
//
//  Shared leaderboard rendering used by VictoryView (post-game) and
//  GameLeaderboardSheet (opened from Settings > My Games for finished games).
//

import SwiftUI

/// Renders the podium + other finders + non-finders sections.
/// Callers provide pre-built `LeaderboardEntry` values — this view is purely presentational.
/// If `onReport` is non-nil, a flag button is shown next to each non-self row so users can
/// report offensive nicknames (UGC moderation requirement on Google Play / App Store).
struct LeaderboardContentView: View {
    let entries: [LeaderboardEntry]
    let hunterStartDate: Date
    var onReport: ((LeaderboardEntry) -> Void)? = nil

    private var sortedFinders: [LeaderboardEntry] {
        entries.filter { $0.hasFound }.sorted { a, b in
            (a.foundTimestamp ?? .distantFuture) < (b.foundTimestamp ?? .distantFuture)
        }
    }

    private var nonFinders: [LeaderboardEntry] {
        entries.filter { !$0.hasFound }.sorted {
            $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending
        }
    }

    var body: some View {
        let podium = Array(sortedFinders.prefix(3))
        let others = Array(sortedFinders.dropFirst(3))

        VStack(spacing: 16) {
            if !podium.isEmpty {
                VStack(spacing: 8) {
                    BangerText("Podium", size: 20)
                        .foregroundStyle(Color.CROrange)
                    ForEach(Array(podium.enumerated()), id: \.element.id) { index, entry in
                        LeaderboardRowView(rank: index + 1, entry: entry, hunterStartDate: hunterStartDate, onReport: onReport)
                    }
                }
            }

            if !others.isEmpty {
                VStack(spacing: 8) {
                    BangerText("Other hunters", size: 18)
                        .foregroundStyle(Color.onBackground.opacity(0.7))
                    ForEach(Array(others.enumerated()), id: \.element.id) { index, entry in
                        LeaderboardRowView(rank: index + 4, entry: entry, hunterStartDate: hunterStartDate, onReport: onReport)
                    }
                }
            }

            if !nonFinders.isEmpty {
                VStack(spacing: 8) {
                    BangerText("Did not find the chicken", size: 16)
                        .foregroundStyle(Color.onBackground.opacity(0.5))
                    ForEach(nonFinders) { entry in
                        LeaderboardRowView(rank: nil, entry: entry, hunterStartDate: hunterStartDate, onReport: onReport)
                    }
                }
            }
        }
    }
}

/// A single leaderboard row with rank label, name, and time.
/// Highlighted if the entry belongs to the current user.
struct LeaderboardRowView: View {
    let rank: Int?
    let entry: LeaderboardEntry
    let hunterStartDate: Date
    var onReport: ((LeaderboardEntry) -> Void)? = nil

    private var rankLabel: String {
        guard let rank else { return "—" }
        switch rank {
        case 1: return "🥇"
        case 2: return "🥈"
        case 3: return "🥉"
        default: return "#\(rank)"
        }
    }

    private var timeString: String? {
        guard let foundTimestamp = entry.foundTimestamp else { return nil }
        let totalSeconds = max(0, Int(foundTimestamp.timeIntervalSince(hunterStartDate)))
        return "+\(totalSeconds / 60)m \(String(format: "%02d", totalSeconds % 60))s"
    }

    private var canReport: Bool {
        onReport != nil && !entry.isCurrentUser
    }

    var body: some View {
        HStack {
            Text(rankLabel)
                .font(.system(size: (rank ?? 99) <= 3 ? 24 : 16))
                .frame(width: 44)

            BangerText(entry.displayName, size: 18)
                .foregroundStyle(Color.onBackground)
                .lineLimit(1)

            Spacer()

            if let timeString {
                Text(timeString)
                    .font(.gameboy(size: 10))
                    .foregroundStyle(Color.onBackground.opacity(0.5))
            }

            if canReport, let onReport {
                Button {
                    onReport(entry)
                } label: {
                    Image(systemName: "flag")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.onBackground.opacity(0.4))
                        .padding(6)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel(String(localized: "Report \(entry.displayName)"))
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(rankLabel) \(entry.displayName)\(timeString.map { ", \($0)" } ?? "")")
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(
            entry.isCurrentUser
                ? Color.CROrange.opacity(0.2)
                : Color.surface.opacity(0.4)
        )
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}
