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
        /// IDs of power-ups for which a collect transaction is currently
        /// in flight. The parent reducer checks this before dispatching a
        /// new attempt so a 1 Hz timer tick can't spam the server with
        /// N duplicate transactions while the user stands inside the disc.
        /// Android has carried the equivalent `collectingPowerUpIds` since
        /// the power-up rollout; iOS picked up the same guard at 1.11.1.
        var collectingIds: Set<String> = []
    }

    enum Action {
        /// User tapped the Activate button in the inventory sheet.
        case activateTapped(PowerUp)
        /// Atomic check-and-claim: parent calls this in the same reducer
        /// pass that kicks off the Firestore transaction. If the id is
        /// already in `collectingIds`, the parent bails instead of firing
        /// a duplicate write.
        case collectStarted(String)
        /// Transaction succeeded — removes the id from `collectingIds` and
        /// shows a "Collected: <name>!" banner so the user has the same
        /// feedback Android already gives (`BaseMapViewModel.notifyPowerUp`).
        case collectSucceeded(PowerUp)
        /// Transaction failed — removes the id from `collectingIds` and
        /// shows a "Failed to collect power-up" banner. Matches the
        /// Android toast path so any future rule / network regression is
        /// visible to the player instead of being swallowed in logs.
        case collectFailed(PowerUp)
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

            case let .collectStarted(id):
                state.collectingIds.insert(id)
                return .none

            case let .collectSucceeded(powerUp):
                state.collectingIds.remove(powerUp.id)
                state.notification = "Collected: \(powerUp.type.displayName)!"
                state.lastActivatedType = powerUp.type
                return .none

            case let .collectFailed(powerUp):
                state.collectingIds.remove(powerUp.id)
                state.notification = "Failed to collect power-up"
                return .none

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
