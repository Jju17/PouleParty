//
//  HunterMapView.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

struct HunterMapView: View {
    @Bindable var store: StoreOf<HunterMapFeature>
    @State private var selectedPowerUp: PowerUp?

    private var subtitle: String {
        if store.game.chickenCanSeeHunters {
            return "Catch the 🐔 (she sees you! 👀)"
        }
        switch store.game.gameMode {
        case .followTheChicken: return "Catch the 🐔 !"
        case .stayInTheZone:    return "Stay in the zone 📍"
        }
    }

    var body: some View {
        HunterMapContent(store: store, selectedPowerUp: $selectedPowerUp)
            .safeAreaInset(edge: .top) {
                MapTopBar(
                    title: "You are the Hunter",
                    subtitle: subtitle,
                    gradient: LinearGradient(colors: [.hunterRed, .CRPink], startPoint: .leading, endPoint: .trailing),
                    onInfoTapped: { store.send(.view(.infoButtonTapped)) }
                )
            }
            .safeAreaInset(edge: .bottom) {
                MapBottomBar(
                    state: store.state,
                    isActionButtonVisible: store.hasGameStarted,
                    actionAccessibilityLabel: "I found the chicken",
                    onActionTapped: { store.send(.view(.foundButtonTapped)) },
                    onInventoryTapped: { store.send(.powerUps(.inventoryTapped)) },
                    isChicken: false
                )
            }
            .task {
                store.send(.view(.onTask))
            }
            .idleTimerDisabled()
            .mapHaptics(store.state)
            .alert(
                $store.scope(
                    state: \.destination?.alert,
                    action: \.destination.alert
                )
            )
            .alert("Enter Found Code", isPresented: $store.isEnteringFoundCode) {
                TextField("4-digit code", text: $store.enteredCode)
                    .keyboardType(.numberPad)
                Button("Submit") {
                    store.send(.view(.submitCodeButtonTapped))
                }
                Button("Cancel", role: .cancel) { }
            } message: {
                Text("Enter the 4-digit code shown by the chicken.")
            }
            .mapCommonOverlays(store.state)
            .overlay {
                if !store.hasGameStarted {
                    PreGameOverlay(
                        role: .hunter,
                        gameModTitle: store.game.gameMode.title,
                        gameCode: nil,
                        targetDate: store.game.hunterStartDate,
                        nowDate: store.nowDate,
                        connectedHunters: store.game.hunterIds.count
                    )
                }
            }
            .mapCommonSheets(
                state: store.state,
                selectedPowerUp: $selectedPowerUp,
                leaveGameLabel: "Leave game",
                onCancelGame: { store.send(.view(.cancelGameButtonTapped)) },
                onGameInfoDismiss: { store.send(.view(.gameInfoDismissed)) },
                onInventoryDismiss: { store.send(.powerUps(.inventoryDismissed)) },
                onActivatePowerUp: { store.send(.powerUps(.activateTapped($0))) }
            )
    }
}

#Preview {
    HunterMapView(store: Store(initialState: HunterMapFeature.State(game: .mock)) {
        HunterMapFeature()
    })
}
