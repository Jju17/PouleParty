//
//  MapCommonOverlays.swift
//  PouleParty
//
//  Attaches the four overlays both maps render identically:
//  winner banner, zone-warning banner, game-start countdown, and
//  power-up notification banner.
//

import SwiftUI

private struct MapCommonOverlaysModifier<State: MapFeatureState>: ViewModifier {
    let state: State

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .top) {
                WinnerNotificationOverlay(notification: state.winnerNotification)
            }
            .overlay(alignment: .top) {
                if state.isOutsideZone {
                    ZoneWarningOverlay()
                }
            }
            .overlay {
                GameStartCountdownOverlay(
                    countdownNumber: state.countdownNumber,
                    countdownText: state.countdownText
                )
            }
            .overlay(alignment: .top) {
                PowerUpNotificationBanner(
                    notification: state.powerUpNotification,
                    powerUpType: state.lastActivatedPowerUpType
                )
            }
    }
}

extension View {
    /// Adds the four always-on overlays shared by both map screens.
    func mapCommonOverlays(_ state: some MapFeatureState) -> some View {
        modifier(MapCommonOverlaysModifier(state: state))
    }
}
