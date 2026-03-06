//
//  LiveActivityComponents.swift
//  PoulePartyWidgets
//
//  Reusable UI components for Live Activity surfaces.
//

import ActivityKit
import SwiftUI
import WidgetKit

// MARK: - Brand Colors

let crBeige = Color(red: 253/255, green: 249/255, blue: 213/255)
let crOrange = Color(red: 254/255, green: 106/255, blue: 0)
let crPink = Color(red: 239/255, green: 7/255, blue: 120/255)

// MARK: - Radius Badge

struct RadiusBadge: View {
    let radius: Int

    var body: some View {
        HStack(spacing: 1) {
            Image(systemName: "circle.dashed")
                .font(.caption)
            Text("\(radius)m")
                .font(.banger(size: 16))
                .lineLimit(1)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(crOrange.opacity(0.8))
        .clipShape(Capsule())
    }
}

// MARK: - Game Code Badge

struct GameCodeBadge: View {
    let code: String

    var body: some View {
        Text(code)
            .font(.banger(size: 14))
            .lineLimit(1)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(crOrange.opacity(0.8))
            .clipShape(Capsule())
    }
}

// MARK: - Hunter Status Row

struct HunterStatusRow: View {
    let activeHunters: Int
    let totalHunters: Int
    let winnersCount: Int

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "person.3.fill")
                .font(.caption)
                .foregroundStyle(.secondary)
            if winnersCount > 0 {
                Text("\(activeHunters)/\(totalHunters) hunting")
                    .font(.subheadline)
                + Text(" \u{00B7} \(winnersCount) found")
                    .font(.subheadline)
                    .foregroundColor(crPink)
            } else {
                Text("\(activeHunters)/\(totalHunters) hunting")
                    .font(.subheadline)
            }
        }
        .lineLimit(1)
    }
}

// MARK: - Shrink Countdown

struct ShrinkCountdown: View {
    let nextShrinkDate: Date?

    var body: some View {
        if let date = nextShrinkDate, date > .now {
            HStack(spacing: 4) {
                Image(systemName: "arrow.down.right.and.arrow.up.left")
                    .font(.caption)
                    .foregroundStyle(crOrange)
                Text(date, style: .timer)
                    .font(.subheadline)
                    .monospacedDigit()
            }
        }
    }
}

// MARK: - End Game Countdown

struct EndCountdown: View {
    let endGameDate: Date?

    var body: some View {
        if let date = endGameDate, date > .now {
            HStack(spacing: 4) {
                Image(systemName: "flag.checkered")
                    .font(.caption)
                Text(date, style: .timer)
                    .font(.caption)
                    .monospacedDigit()
            }
            .foregroundStyle(.secondary)
        }
    }
}

// MARK: - Game Start Bar

struct GameStartBar: View {
    let gameStartDate: Date

    var body: some View {
        if gameStartDate > .now {
            HStack(spacing: 6) {
                ProgressView(
                    timerInterval: Date.now...gameStartDate,
                    countsDown: true
                ) {
                    EmptyView()
                }
                .tint(crOrange)

                Text(gameStartDate, style: .time)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

// MARK: - Game Time Bar

struct GameTimeBar: View {
    let gameEndDate: Date

    var body: some View {
        if gameEndDate > .now {
            HStack(spacing: 6) {
                ProgressView(
                    timerInterval: Date.now...gameEndDate,
                    countsDown: true
                ) {
                    EmptyView()
                }
                .tint(crOrange)

                Text("Ends \(gameEndDate, style: .time)")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
