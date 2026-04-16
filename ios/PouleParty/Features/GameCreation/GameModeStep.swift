//
//  GameModeStep.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

struct GameModeStep: GameCreationStepView {
    static let step: GameCreationStep = .gameMode
    @Bindable var store: StoreOf<GameCreationFeature>

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            StepHeader(
                title: "Game Mode",
                subtitle: "Choose the game mode"
            )

            VStack(spacing: 16) {
                SelectionCard(
                    title: "Follow the Chicken",
                    emoji: "🐔",
                    subtitle: "The zone shrinks toward the chicken",
                    isSelected: store.currentGame.gameMode == .followTheChicken,
                    gradient: Color.gradientChicken
                ) {
                    store.send(.gameModChanged(.followTheChicken))
                }

                SelectionCard(
                    title: "Stay in the Zone",
                    emoji: "📍",
                    subtitle: "Fixed zone that shrinks",
                    isSelected: store.currentGame.gameMode == .stayInTheZone,
                    gradient: Color.gradientHunter
                ) {
                    store.send(.gameModChanged(.stayInTheZone))
                }
            }
            .padding(.horizontal, 24)
            Spacer()
        }
    }
}
