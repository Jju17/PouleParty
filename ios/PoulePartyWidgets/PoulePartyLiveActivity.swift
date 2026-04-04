//
//  PoulePartyLiveActivity.swift
//  PoulePartyWidgets
//
//  The Widget entry point — routes views to Live Activity surfaces.
//  Views are defined in LiveActivityViews.swift.
//

import ActivityKit
import SwiftUI
import WidgetKit

struct PoulePartyLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: PoulePartyAttributes.self) { context in
            // Lock screen / StandBy banner
            LockScreenView(context: context)
        } dynamicIsland: { context in
            DynamicIsland {
                // ── Expanded: Leading ─────────────────────────
                // Emoji only — keeps it tight next to the camera
                DynamicIslandExpandedRegion(.leading) {
                    Text(roleEmoji(context))
                        .font(.title2)
                }

                // ── Expanded: Trailing ────────────────────────
                // Phase-dependent badge next to the camera
                DynamicIslandExpandedRegion(.trailing) {
                    switch context.state.gamePhase {
                    case .waitingToStart:
                        GameCodeBadge(code: context.attributes.gameCode)
                    case .chickenHeadStart, .hunting:
                        RadiusBadge(radius: context.state.radiusMeters)
                    case .gameOver:
                        EmptyView()
                    }
                }

                // ── Expanded: Bottom (full width) ─────────────
                // All detailed content goes here
                DynamicIslandExpandedRegion(.bottom) {
                    switch context.state.gamePhase {
                    case .waitingToStart:
                        HStack {
                            BangerText(context.attributes.gameName, size: 14)
                                .lineLimit(1)
                            Spacer()
                            HStack(spacing: 4) {
                                Image(systemName: "clock")
                                    .font(.caption)
                                Text(context.attributes.gameStartDate, style: .timer)
                                    .font(.subheadline)
                                    .monospacedDigit()
                            }
                            .foregroundStyle(.secondary)
                        }

                    case .chickenHeadStart:
                        if context.attributes.playerRole == .chicken {
                            Text("Run! Hunters incoming!")
                                .font(.caption)
                                .fontWeight(.semibold)
                                .foregroundStyle(crOrange)
                        } else {
                            Text("Chicken is hiding...")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                    case .hunting:
                        VStack(spacing: 4) {
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
                                    Text("Outside the zone!")
                                        .font(.caption2)
                                        .fontWeight(.bold)
                                        .foregroundStyle(.red)
                                    Spacer()
                                }
                            }
                        }

                    case .gameOver:
                        if context.state.winnersCount > 0 {
                            HStack(spacing: 4) {
                                Text("🏆")
                                Text("\(context.state.winnersCount) hunter\(context.state.winnersCount == 1 ? "" : "s") caught the chicken!")
                                    .font(.caption)
                                    .fontWeight(.semibold)
                            }
                        } else {
                            HStack(spacing: 4) {
                                Text("🐔")
                                Text("The chicken survived!")
                                    .font(.caption)
                                    .fontWeight(.semibold)
                            }
                        }
                    }
                }
            } compactLeading: {
                // ── Compact: Leading ──────────────────────────
                Text(roleEmoji(context))
                    .font(.title3)
            } compactTrailing: {
                // ── Compact: Trailing ─────────────────────────
                switch context.state.gamePhase {
                case .waitingToStart:
                    BangerText(context.attributes.gameCode, size: 12)
                        .foregroundStyle(crOrange)
                case .chickenHeadStart, .hunting:
                    BangerText("\(context.state.radiusMeters)m", size: 14)
                        .foregroundStyle(crOrange)
                case .gameOver:
                    Text(context.state.winnersCount > 0 ? "🏆" : "🐔")
                        .font(.caption)
                }
            } minimal: {
                // ── Minimal ───────────────────────────────────
                Text(roleEmoji(context))
                    .font(.caption)
            }
        }
    }

    private func roleEmoji(_ context: ActivityViewContext<PoulePartyAttributes>) -> String {
        context.attributes.playerRole == .chicken ? "🐔" : "🔍"
    }
}
