//
//  MapCommonSheets.swift
//  PouleParty
//
//  Shared sheet surface for both map screens: game info, power-up inventory,
//  and the power-up detail sheet driven by a local @State selection.
//

import SwiftUI

private struct MapCommonSheetsModifier<State: MapFeatureState>: ViewModifier {
    let state: State
    @Binding var selectedPowerUp: PowerUp?
    let leaveGameLabel: String?
    let onCancelGame: () -> Void
    let onGameInfoDismiss: () -> Void
    let onInventoryDismiss: () -> Void
    let onActivatePowerUp: (PowerUp) -> Void

    func body(content: Content) -> some View {
        content
            .sheet(isPresented: Binding(
                get: { state.showGameInfo },
                set: { _ in onGameInfoDismiss() }
            )) {
                if let leaveGameLabel {
                    GameInfoSheet(
                        game: state.game,
                        onCancelGame: onCancelGame,
                        leaveGameLabel: leaveGameLabel
                    )
                } else {
                    GameInfoSheet(
                        game: state.game,
                        onCancelGame: onCancelGame
                    )
                }
            }
            .sheet(isPresented: Binding(
                get: { state.showPowerUpInventory },
                set: { _ in onInventoryDismiss() }
            )) {
                PowerUpInventorySheet(
                    powerUps: state.collectedPowerUps,
                    onActivate: onActivatePowerUp
                )
            }
            .sheet(item: $selectedPowerUp) { powerUp in
                PowerUpDetailSheet(powerUpType: powerUp.type)
                    .presentationDetents([.medium, .large])
            }
    }
}

extension View {
    /// Attaches the sheets shared by both map screens. Pass `leaveGameLabel`
    /// only when the caller needs the alternate button label (hunter).
    func mapCommonSheets<S: MapFeatureState>(
        state: S,
        selectedPowerUp: Binding<PowerUp?>,
        leaveGameLabel: String? = nil,
        onCancelGame: @escaping () -> Void,
        onGameInfoDismiss: @escaping () -> Void,
        onInventoryDismiss: @escaping () -> Void,
        onActivatePowerUp: @escaping (PowerUp) -> Void
    ) -> some View {
        modifier(
            MapCommonSheetsModifier(
                state: state,
                selectedPowerUp: selectedPowerUp,
                leaveGameLabel: leaveGameLabel,
                onCancelGame: onCancelGame,
                onGameInfoDismiss: onGameInfoDismiss,
                onInventoryDismiss: onInventoryDismiss,
                onActivatePowerUp: onActivatePowerUp
            )
        )
    }
}
