//
//  ChickenSelectionStep.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

struct ChickenSelectionStep: GameCreationStepView {
    static let step: GameCreationStep = .chickenSelection
    @Bindable var store: StoreOf<GameCreationFeature>

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            StepHeader(
                title: "Who's the Chicken?",
                subtitle: "How will the chicken be chosen?"
            )

            VStack(spacing: 16) {
                SelectionCard(
                    title: "Random",
                    emoji: "🎲",
                    subtitle: "A random player",
                    isSelected: true,
                    gradient: Color.gradientChicken
                ) { }

                SelectionCard(
                    title: "First to join",
                    emoji: "🏃",
                    subtitle: "First player to join",
                    isSelected: false,
                    gradient: Color.gradientFire
                ) { }

                SelectionCard(
                    title: "I'll choose",
                    emoji: "👆",
                    subtitle: "I'll pick the chicken later",
                    isSelected: false,
                    gradient: Color.gradientHunter
                ) { }
            }
            .padding(.horizontal, 24)
            Spacer()
        }
    }
}
