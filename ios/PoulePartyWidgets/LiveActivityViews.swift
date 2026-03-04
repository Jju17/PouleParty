//
//  LiveActivityViews.swift
//  PoulePartyWidgets
//

import ActivityKit
import SwiftUI
import WidgetKit

// MARK: - Brand Colors

private let crBeige = Color(red: 253/255, green: 249/255, blue: 213/255)
private let crOrange = Color(red: 254/255, green: 106/255, blue: 0)
private let crPink = Color(red: 239/255, green: 7/255, blue: 120/255)

// MARK: - Lock Screen View

struct LockScreenView: View {
    let context: ActivityViewContext<PoulePartyAttributes>

    private var backgroundColor: Color {
        context.state.isOutsideZone ? Color.red.opacity(0.3) : Color.black.opacity(0.15)
    }

    var body: some View {
        VStack(spacing: 8) {
            switch context.state.gamePhase {
            case .waitingToStart:
                WaitingPhaseView(context: context)
            case .chickenHeadStart:
                HeadStartPhaseView(context: context)
            case .hunting:
                HuntingPhaseView(context: context)
            case .gameOver:
                GameOverPhaseView(context: context)
            }
        }
        .padding(16)
        .activityBackgroundTint(backgroundColor)
    }
}

// MARK: - Phase Views

private struct WaitingPhaseView: View {
    let context: ActivityViewContext<PoulePartyAttributes>

    var body: some View {
        VStack(spacing: 8) {
            TopRow(context: context)

            HStack {
                Label {
                    Text("Game code: **\(context.attributes.gameCode)**")
                        .font(.subheadline)
                } icon: {
                    Image(systemName: "ticket")
                }

                Spacer()

                Label {
                    Text("Starting soon...")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                } icon: {
                    Image(systemName: "hourglass")
                }
            }

            GameTimeBar(gameEndDate: context.attributes.gameEndDate)
        }
    }
}

private struct HeadStartPhaseView: View {
    let context: ActivityViewContext<PoulePartyAttributes>

    var body: some View {
        VStack(spacing: 8) {
            TopRow(context: context)

            HStack {
                if context.attributes.playerRole == .chicken {
                    Label {
                        Text("Run! Hunters incoming!")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundStyle(crOrange)
                    } icon: {
                        Text("🏃")
                    }
                } else {
                    Label {
                        Text("Chicken is hiding...")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    } icon: {
                        Text("🐔")
                    }
                }
                Spacer()
            }

            GameTimeBar(gameEndDate: context.attributes.gameEndDate)
        }
    }
}

private struct HuntingPhaseView: View {
    let context: ActivityViewContext<PoulePartyAttributes>

    var body: some View {
        VStack(spacing: 8) {
            TopRow(context: context)

            HStack {
                HunterStatusRow(
                    activeHunters: context.state.activeHunters,
                    totalHunters: context.attributes.totalHunters,
                    winnersCount: context.state.winnersCount
                )

                Spacer()

                ShrinkCountdown(nextShrinkDate: context.state.nextShrinkDate)
            }

            if context.state.isOutsideZone {
                HStack(spacing: 4) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(.red)
                    Text("You are outside the zone!")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundStyle(.red)
                    Spacer()
                }
            }

            GameTimeBar(gameEndDate: context.attributes.gameEndDate)
        }
    }
}

private struct GameOverPhaseView: View {
    let context: ActivityViewContext<PoulePartyAttributes>

    var body: some View {
        VStack(spacing: 8) {
            TopRow(context: context)

            HStack {
                if context.state.winnersCount > 0 {
                    Label {
                        Text("\(context.state.winnersCount) hunter\(context.state.winnersCount == 1 ? "" : "s") caught the chicken!")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                    } icon: {
                        Text("🏆")
                    }
                } else {
                    Label {
                        Text("The chicken survived!")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                    } icon: {
                        Text("🐔")
                    }
                }
                Spacer()
            }
        }
    }
}

// MARK: - Shared Components

private struct TopRow: View {
    let context: ActivityViewContext<PoulePartyAttributes>

    var body: some View {
        HStack {
            Text(context.attributes.playerRole == .chicken ? "🐔" : "🔍")
                .font(.title2)

            Text(context.attributes.gameName)
                .font(.custom("Bangers-Regular", fixedSize: 18))
                .lineLimit(1)

            Spacer()

            RadiusBadge(radius: context.state.radiusMeters)
        }
    }
}

struct RadiusBadge: View {
    let radius: Int

    var body: some View {
        HStack(spacing: 2) {
            Image(systemName: "circle.dashed")
                .font(.caption)
            Text("\(radius)m")
                .font(.custom("Bangers-Regular", fixedSize: 16))
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(crOrange.opacity(0.8))
        .clipShape(Capsule())
    }
}

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
                + Text(" · \(winnersCount) found")
                    .font(.subheadline)
                    .foregroundColor(crPink)
            } else {
                Text("\(activeHunters)/\(totalHunters) hunting")
                    .font(.subheadline)
            }
        }
    }
}

struct ShrinkCountdown: View {
    let nextShrinkDate: Date?

    var body: some View {
        if let date = nextShrinkDate, date > .now {
            Label {
                Text(date, style: .timer)
                    .font(.subheadline)
                    .monospacedDigit()
                    .multilineTextAlignment(.trailing)
            } icon: {
                Image(systemName: "arrow.down.right.and.arrow.up.left")
                    .font(.caption)
                    .foregroundStyle(crOrange)
            }
        }
    }
}

private struct GameTimeBar: View {
    let gameEndDate: Date

    var body: some View {
        HStack(spacing: 6) {
            ProgressView(
                timerInterval: Date.now...gameEndDate,
                countsDown: true
            ) {
                EmptyView()
            }
            .tint(crOrange)

            Text(gameEndDate, style: .time)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
    }
}

// MARK: - Dynamic Island Expanded Bottom

struct ExpandedBottomRow: View {
    let context: ActivityViewContext<PoulePartyAttributes>

    var body: some View {
        switch context.state.gamePhase {
        case .hunting:
            HStack {
                ShrinkCountdown(nextShrinkDate: context.state.nextShrinkDate)
                Spacer()
                HStack(spacing: 4) {
                    Image(systemName: "flag.checkered")
                        .font(.caption)
                    Text(context.attributes.gameEndDate, style: .timer)
                        .font(.caption)
                        .monospacedDigit()
                }
                .foregroundStyle(.secondary)
            }
        case .waitingToStart:
            Text("Game code: \(context.attributes.gameCode)")
                .font(.caption)
                .fontWeight(.semibold)
        case .chickenHeadStart:
            if context.attributes.playerRole == .chicken {
                Text("Run! Hunters are coming!")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundStyle(crOrange)
            } else {
                Text("Chicken is getting a head start...")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        case .gameOver:
            if context.state.winnersCount > 0 {
                Text("Chicken caught!")
                    .font(.caption)
                    .fontWeight(.semibold)
            } else {
                Text("Chicken survived!")
                    .font(.caption)
                    .fontWeight(.semibold)
            }
        }
    }
}

// MARK: - Previews

#Preview("Lock Screen - Hunting", as: .content, using: PoulePartyAttributes(
    gameName: "Brussels Hunt",
    gameCode: "ABC123",
    playerRole: .hunter,
    gameModeName: "Follow the chicken",
    gameEndDate: .now.addingTimeInterval(3600),
    totalHunters: 8
)) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 1200,
        nextShrinkDate: .now.addingTimeInterval(180),
        activeHunters: 6,
        winnersCount: 2,
        isOutsideZone: false,
        gamePhase: .hunting
    )
    PoulePartyAttributes.ContentState(
        radiusMeters: 800,
        nextShrinkDate: .now.addingTimeInterval(60),
        activeHunters: 4,
        winnersCount: 4,
        isOutsideZone: true,
        gamePhase: .hunting
    )
}

#Preview("Lock Screen - Waiting", as: .content, using: PoulePartyAttributes(
    gameName: "Team Alpha",
    gameCode: "XYZ789",
    playerRole: .chicken,
    gameModeName: "Stay in the zone",
    gameEndDate: .now.addingTimeInterval(3900),
    totalHunters: 5
)) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 1500,
        nextShrinkDate: nil,
        activeHunters: 5,
        winnersCount: 0,
        isOutsideZone: false,
        gamePhase: .waitingToStart
    )
}

#Preview("Lock Screen - Game Over", as: .content, using: PoulePartyAttributes(
    gameName: "Brussels Hunt",
    gameCode: "ABC123",
    playerRole: .hunter,
    gameModeName: "Follow the chicken",
    gameEndDate: .now,
    totalHunters: 8
)) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 200,
        nextShrinkDate: nil,
        activeHunters: 3,
        winnersCount: 5,
        isOutsideZone: false,
        gamePhase: .gameOver
    )
}

#Preview("Dynamic Island Compact", as: .dynamicIsland(.compact), using: PoulePartyAttributes(
    gameName: "Brussels Hunt",
    gameCode: "ABC123",
    playerRole: .chicken,
    gameModeName: "Follow the chicken",
    gameEndDate: .now.addingTimeInterval(3600),
    totalHunters: 8
)) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 1200,
        nextShrinkDate: .now.addingTimeInterval(180),
        activeHunters: 6,
        winnersCount: 2,
        isOutsideZone: false,
        gamePhase: .hunting
    )
}

#Preview("Dynamic Island Expanded", as: .dynamicIsland(.expanded), using: PoulePartyAttributes(
    gameName: "Brussels Hunt",
    gameCode: "ABC123",
    playerRole: .hunter,
    gameModeName: "Follow the chicken",
    gameEndDate: .now.addingTimeInterval(3600),
    totalHunters: 8
)) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 1200,
        nextShrinkDate: .now.addingTimeInterval(180),
        activeHunters: 6,
        winnersCount: 2,
        isOutsideZone: false,
        gamePhase: .hunting
    )
}

#Preview("Dynamic Island Minimal", as: .dynamicIsland(.minimal), using: PoulePartyAttributes(
    gameName: "Brussels Hunt",
    gameCode: "ABC123",
    playerRole: .chicken,
    gameModeName: "Follow the chicken",
    gameEndDate: .now.addingTimeInterval(3600),
    totalHunters: 8
)) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 1200,
        nextShrinkDate: .now.addingTimeInterval(180),
        activeHunters: 6,
        winnersCount: 2,
        isOutsideZone: false,
        gamePhase: .hunting
    )
}
