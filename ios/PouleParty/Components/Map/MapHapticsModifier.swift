//
//  MapHapticsModifier.swift
//  PouleParty
//
//  Fires haptic feedback on key state transitions shared by the two map screens.
//

import SwiftUI

private struct MapHapticsModifier<State: MapFeatureState>: ViewModifier {
    let state: State

    func body(content: Content) -> some View {
        content
            .onChange(of: state.countdownNumber) { _, new in
                if new != nil { HapticManager.impact(.heavy) }
            }
            .onChange(of: state.countdownText) { _, new in
                if new != nil { HapticManager.impact(.heavy) }
            }
            .onChange(of: state.isOutsideZone) { old, new in
                if !old && new { HapticManager.notification(.warning) }
            }
            .onChange(of: state.game.winners.count) { old, new in
                if new > old { HapticManager.notification(.success) }
            }
            .onChange(of: state.powerUpNotification) { _, new in
                if new != nil { HapticManager.notification(.success) }
            }
    }
}

extension View {
    /// Attaches the haptic feedback rules shared by both map screens.
    func mapHaptics(_ state: some MapFeatureState) -> some View {
        modifier(MapHapticsModifier(state: state))
    }
}
