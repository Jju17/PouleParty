//
//  PoulePartyWidgetsLiveActivity.swift
//  PoulePartyWidgets
//
//  Created by Julien on 04/03/2026.
//

import ActivityKit
import SwiftUI
import WidgetKit

struct PoulePartyLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: PoulePartyAttributes.self) { context in
            LockScreenView(context: context)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 4) {
                        Text(context.attributes.playerRole == .chicken ? "🐔" : "🔍")
                            .font(.title2)
                        Text(context.attributes.gameName)
                            .font(.custom("Bangers-Regular", fixedSize: 16))
                            .lineLimit(1)
                    }
                }

                DynamicIslandExpandedRegion(.trailing) {
                    RadiusBadge(radius: context.state.radiusMeters)
                }

                DynamicIslandExpandedRegion(.center) {
                    HunterStatusRow(
                        activeHunters: context.state.activeHunters,
                        totalHunters: context.attributes.totalHunters,
                        winnersCount: context.state.winnersCount
                    )
                }

                DynamicIslandExpandedRegion(.bottom) {
                    ExpandedBottomRow(context: context)
                }
            } compactLeading: {
                Text(context.attributes.playerRole == .chicken ? "🐔" : "🔍")
                    .font(.title3)
            } compactTrailing: {
                Text("\(context.state.radiusMeters)m")
                    .font(.custom("Bangers-Regular", fixedSize: 14))
                    .foregroundStyle(Color(red: 254/255, green: 106/255, blue: 0))
            } minimal: {
                Text(context.attributes.playerRole == .chicken ? "🐔" : "🔍")
                    .font(.caption)
            }
        }
    }
}
