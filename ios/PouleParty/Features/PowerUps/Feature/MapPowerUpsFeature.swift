//
//  MapPowerUpsFeature.swift
//  PouleParty
//
//  Child reducer scoped into ChickenMapFeature and HunterMapFeature.
//  Owns the power-up UI surface (inventory sheet, notification banner,
//  visible power-up lists). API side-effects (collect/activate writes)
//  stay in the parent — the child emits a delegate action on activation
//  so the parent can perform the Firestore work.
//

import ComposableArchitecture
import Foundation

@Reducer
struct MapPowerUpsFeature {
    @ObservableState
    struct State: Equatable {
        var available: [PowerUp] = []
        var collected: [PowerUp] = []
        var lastActivatedType: PowerUp.PowerUpType? = nil
        var notification: String? = nil
        var showInventory: Bool = false
    }

    enum Action {
        /// User tapped the Activate button in the inventory sheet.
        case activateTapped(PowerUp)
        /// Parent hands in the filtered lists after a Firestore stream tick.
        case dataUpdated(available: [PowerUp], collected: [PowerUp])
        case delegate(Delegate)
        case inventoryDismissed
        case inventoryTapped
        case notificationCleared
        /// Shows a transient banner (e.g. "Collected: Jammer!" or an opponent's activation).
        case notificationShown(text: String, type: PowerUp.PowerUpType?)

        @CasePathable
        enum Delegate {
            /// Emitted when the user activates a power-up so the parent can perform
            /// the Firestore write and schedule the banner clearance.
            case activated(PowerUp)
        }
    }

    var body: some Reducer<State, Action> {
        Reduce { state, action in
            switch action {
            case let .activateTapped(powerUp):
                state.showInventory = false
                state.notification = "Activated: \(powerUp.type.displayName)!"
                state.lastActivatedType = powerUp.type
                return .send(.delegate(.activated(powerUp)))

            case let .dataUpdated(available, collected):
                state.available = available
                state.collected = collected
                return .none

            case .delegate:
                return .none

            case .inventoryDismissed:
                state.showInventory = false
                return .none

            case .inventoryTapped:
                state.showInventory = true
                return .none

            case .notificationCleared:
                state.notification = nil
                return .none

            case let .notificationShown(text, type):
                state.notification = text
                state.lastActivatedType = type
                return .none
            }
        }
    }
}
