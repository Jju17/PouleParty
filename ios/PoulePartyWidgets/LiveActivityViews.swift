//
//  LiveActivityViews.swift
//  PoulePartyWidgets
//
//  Lock screen view and phase-specific layouts.
//  Components are in LiveActivityComponents.swift.
//

import ActivityKit
import SwiftUI
import WidgetKit

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
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .activityBackgroundTint(backgroundColor)
    }
}

// MARK: - Top Row (lock screen only)

private struct TopRow: View {
    let context: ActivityViewContext<PoulePartyAttributes>

    var body: some View {
        HStack {
            Text(context.attributes.playerRole == .chicken ? "🐔" : "🔍")
                .font(.title2)

            Text(context.attributes.gameName)
                .font(.banger(size: 17))
                .lineLimit(1)

            Spacer()

            RadiusBadge(radius: context.state.radiusMeters)
        }
    }
}

// MARK: - Waiting Phase

private struct WaitingPhaseView: View {
    let context: ActivityViewContext<PoulePartyAttributes>

    var body: some View {
        VStack(spacing: 8) {
            TopRow(context: context)

            HStack {
                Label {
                    Text("Code: **\(context.attributes.gameCode)**")
                        .font(.subheadline)
                } icon: {
                    Image(systemName: "ticket")
                }

                Spacer()

                Label {
                    Text("Starts \(context.attributes.gameStartDate, style: .time)")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                } icon: {
                    Image(systemName: "clock")
                }
            }

            GameStartBar(gameStartDate: context.attributes.gameStartDate)
        }
    }
}

// MARK: - Head Start Phase

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

// MARK: - Hunting Phase

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

// MARK: - Game Over Phase

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
