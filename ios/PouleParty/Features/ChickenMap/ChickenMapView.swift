//
//  ChickenMapView.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

struct ChickenMapView: View {
    @Bindable var store: StoreOf<ChickenMapFeature>
    @State private var selectedPowerUp: PowerUp?

    private var subtitle: String {
        if store.game.chickenCanSeeHunters {
            return "You can see them 👀"
        }
        switch store.game.gameMode {
        case .followTheChicken: return "Don't be seen !"
        case .stayInTheZone:    return "Stay in the zone 📍"
        }
    }

    var body: some View {
        ChickenMapContent(store: store, selectedPowerUp: $selectedPowerUp)
            .safeAreaInset(edge: .top) {
                MapTopBar(
                    title: "You are the 🐔",
                    subtitle: subtitle,
                    gradient: LinearGradient(colors: [.chickenYellow, .CROrange], startPoint: .leading, endPoint: .trailing),
                    onInfoTapped: { store.send(.infoButtonTapped) }
                )
            }
            .safeAreaInset(edge: .bottom) {
                MapBottomBar(
                    state: store.state,
                    isActionButtonVisible: true,
                    actionAccessibilityLabel: "I have been found",
                    onActionTapped: { store.send(.beenFoundButtonTapped) },
                    onInventoryTapped: { store.send(.powerUpInventoryTapped) },
                    isChicken: true
                )
            }
            .task {
                store.send(.gameInitialized)
                store.send(.onTask)
            }
            .idleTimerDisabled()
            .mapHaptics(store.state)
            .alert(
                $store.scope(
                    state: \.destination?.alert,
                    action: \.destination.alert
                )
            )
            .sheet(
                isPresented: Binding(
                    get: { store.destination.flatMap { if case .endGameCode = $0 { true } else { nil } } ?? false },
                    set: { if !$0 { store.send(.endGameCodeDismissed) } }
                )
            ) {
                NavigationStack {
                    EndGameCodeView(foundCode: {
                        if case let .endGameCode(code) = store.destination { return code }
                        return ""
                    }())
                        .toolbar {
                            ToolbarItem {
                                Button {
                                    store.send(.endGameCodeDismissed)
                                } label: {
                                    Image(systemName: "xmark")
                                        .foregroundStyle(.white)
                                }
                            }
                        }
                }
            }
            .mapCommonOverlays(store.state)
            .overlay {
                if !store.hasGameStarted {
                    PreGameOverlay(
                        role: .chicken,
                        gameModTitle: store.game.gameMode.title,
                        gameCode: store.game.gameCode,
                        targetDate: store.game.startDate,
                        nowDate: store.nowDate,
                        connectedHunters: store.game.hunterIds.count,
                        onCancelGame: { store.send(.cancelGameButtonTapped) }
                    )
                }
            }
            .mapCommonSheets(
                state: store.state,
                selectedPowerUp: $selectedPowerUp,
                onCancelGame: { store.send(.cancelGameButtonTapped) },
                onGameInfoDismiss: { store.send(.gameInfoDismissed) },
                onInventoryDismiss: { store.send(.powerUpInventoryDismissed) },
                onActivatePowerUp: { store.send(.powerUpActivated($0)) }
            )
    }
}

#Preview {
    ChickenMapView(store: Store(initialState: ChickenMapFeature.State(game: .mock)) {
        ChickenMapFeature()
    })
}
