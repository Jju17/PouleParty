//
//  LiveActivityPreviews.swift
//  PoulePartyWidgets
//
//  #Preview blocks for all Live Activity surfaces and phases.
//

import ActivityKit
import SwiftUI
import WidgetKit

// MARK: - Preview Helpers

private let chickenAttrs = PoulePartyAttributes(
    gameName: "Brussels Hunt",
    gameCode: "8096C5",
    playerRole: .chicken,
    gameModeName: "Follow the chicken",
    gameStartDate: .now.addingTimeInterval(300),
    gameEndDate: .now.addingTimeInterval(3900),
    totalHunters: 8
)

private let chickenActiveAttrs = PoulePartyAttributes(
    gameName: "Brussels Hunt",
    gameCode: "8096C5",
    playerRole: .chicken,
    gameModeName: "Follow the chicken",
    gameStartDate: .now.addingTimeInterval(-600),
    gameEndDate: .now.addingTimeInterval(3600),
    totalHunters: 8
)

private let hunterAttrs = PoulePartyAttributes(
    gameName: "Team Alpha",
    gameCode: "XYZ789",
    playerRole: .hunter,
    gameModeName: "Follow the chicken",
    gameStartDate: .now.addingTimeInterval(300),
    gameEndDate: .now.addingTimeInterval(3900),
    totalHunters: 8
)

private let hunterActiveAttrs = PoulePartyAttributes(
    gameName: "Team Alpha",
    gameCode: "XYZ789",
    playerRole: .hunter,
    gameModeName: "Follow the chicken",
    gameStartDate: .now.addingTimeInterval(-600),
    gameEndDate: .now.addingTimeInterval(3600),
    totalHunters: 8
)

// MARK: - Lock Screen — Chicken

#Preview("🔒 Chicken - Waiting", as: .content, using: chickenAttrs) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 1500, nextShrinkDate: nil,
        activeHunters: 8, winnersCount: 0,
        isOutsideZone: false, gamePhase: .waitingToStart
    )
}

#Preview("🔒 Chicken - Head Start", as: .content, using: chickenActiveAttrs) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 1500, nextShrinkDate: nil,
        activeHunters: 8, winnersCount: 0,
        isOutsideZone: false, gamePhase: .chickenHeadStart
    )
}

#Preview("🔒 Chicken - Hunting", as: .content, using: chickenActiveAttrs) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 1200, nextShrinkDate: .now.addingTimeInterval(180),
        activeHunters: 6, winnersCount: 2,
        isOutsideZone: false, gamePhase: .hunting
    )
    PoulePartyAttributes.ContentState(
        radiusMeters: 800, nextShrinkDate: .now.addingTimeInterval(60),
        activeHunters: 4, winnersCount: 4,
        isOutsideZone: true, gamePhase: .hunting
    )
}

#Preview("🔒 Chicken - Game Over", as: .content, using: chickenActiveAttrs) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 200, nextShrinkDate: nil,
        activeHunters: 3, winnersCount: 5,
        isOutsideZone: false, gamePhase: .gameOver
    )
    PoulePartyAttributes.ContentState(
        radiusMeters: 500, nextShrinkDate: nil,
        activeHunters: 8, winnersCount: 0,
        isOutsideZone: false, gamePhase: .gameOver
    )
}

// MARK: - Lock Screen — Hunter

#Preview("🔒 Hunter - Waiting", as: .content, using: hunterAttrs) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 1500, nextShrinkDate: nil,
        activeHunters: 8, winnersCount: 0,
        isOutsideZone: false, gamePhase: .waitingToStart
    )
}

#Preview("🔒 Hunter - Head Start", as: .content, using: hunterActiveAttrs) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 1500, nextShrinkDate: nil,
        activeHunters: 8, winnersCount: 0,
        isOutsideZone: false, gamePhase: .chickenHeadStart
    )
}

#Preview("🔒 Hunter - Hunting", as: .content, using: hunterActiveAttrs) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 1200, nextShrinkDate: .now.addingTimeInterval(180),
        activeHunters: 6, winnersCount: 2,
        isOutsideZone: false, gamePhase: .hunting
    )
    PoulePartyAttributes.ContentState(
        radiusMeters: 800, nextShrinkDate: .now.addingTimeInterval(60),
        activeHunters: 4, winnersCount: 4,
        isOutsideZone: true, gamePhase: .hunting
    )
}

#Preview("🔒 Hunter - Game Over", as: .content, using: hunterActiveAttrs) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 200, nextShrinkDate: nil,
        activeHunters: 3, winnersCount: 5,
        isOutsideZone: false, gamePhase: .gameOver
    )
    PoulePartyAttributes.ContentState(
        radiusMeters: 500, nextShrinkDate: nil,
        activeHunters: 8, winnersCount: 0,
        isOutsideZone: false, gamePhase: .gameOver
    )
}

// MARK: - Dynamic Island Expanded (per phase)

#Preview("🏝️ DI Expanded - Waiting", as: .dynamicIsland(.expanded), using: chickenAttrs) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 1500, nextShrinkDate: nil,
        activeHunters: 8, winnersCount: 0,
        isOutsideZone: false, gamePhase: .waitingToStart
    )
}

#Preview("🏝️ DI Expanded - Head Start", as: .dynamicIsland(.expanded), using: chickenActiveAttrs) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 1500, nextShrinkDate: nil,
        activeHunters: 8, winnersCount: 0,
        isOutsideZone: false, gamePhase: .chickenHeadStart
    )
}

#Preview("🏝️ DI Expanded - Hunting", as: .dynamicIsland(.expanded), using: chickenActiveAttrs) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 1200, nextShrinkDate: .now.addingTimeInterval(180),
        activeHunters: 6, winnersCount: 2,
        isOutsideZone: false, gamePhase: .hunting
    )
}

#Preview("🏝️ DI Expanded - Game Over", as: .dynamicIsland(.expanded), using: chickenActiveAttrs) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 200, nextShrinkDate: nil,
        activeHunters: 3, winnersCount: 5,
        isOutsideZone: false, gamePhase: .gameOver
    )
}

// MARK: - Dynamic Island Compact (per phase)

#Preview("💊 DI Compact - Waiting", as: .dynamicIsland(.compact), using: chickenAttrs) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 1500, nextShrinkDate: nil,
        activeHunters: 8, winnersCount: 0,
        isOutsideZone: false, gamePhase: .waitingToStart
    )
}

#Preview("💊 DI Compact - Hunting", as: .dynamicIsland(.compact), using: chickenActiveAttrs) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 1200, nextShrinkDate: .now.addingTimeInterval(180),
        activeHunters: 6, winnersCount: 2,
        isOutsideZone: false, gamePhase: .hunting
    )
}

#Preview("💊 DI Compact - Game Over", as: .dynamicIsland(.compact), using: chickenActiveAttrs) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 200, nextShrinkDate: nil,
        activeHunters: 3, winnersCount: 5,
        isOutsideZone: false, gamePhase: .gameOver
    )
}

// MARK: - Dynamic Island Minimal

#Preview("⚫ DI Minimal - Chicken", as: .dynamicIsland(.minimal), using: chickenActiveAttrs) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 1200, nextShrinkDate: .now.addingTimeInterval(180),
        activeHunters: 6, winnersCount: 2,
        isOutsideZone: false, gamePhase: .hunting
    )
}

#Preview("⚫ DI Minimal - Hunter", as: .dynamicIsland(.minimal), using: hunterActiveAttrs) {
    PoulePartyLiveActivity()
} contentStates: {
    PoulePartyAttributes.ContentState(
        radiusMeters: 800, nextShrinkDate: .now.addingTimeInterval(60),
        activeHunters: 4, winnersCount: 4,
        isOutsideZone: false, gamePhase: .hunting
    )
}
