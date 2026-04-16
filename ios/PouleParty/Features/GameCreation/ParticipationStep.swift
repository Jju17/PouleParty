//
//  ParticipationStep.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

struct ParticipationStep: GameCreationStepView {
    static let step: GameCreationStep = .participation
    @Bindable var store: StoreOf<GameCreationFeature>

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            StepHeader(
                title: "Are you playing?",
                subtitle: "Choose your role"
            )

            VStack(spacing: 16) {
                SelectionCard(
                    title: "I am the Chicken",
                    emoji: "🐔",
                    subtitle: "You run, you hide",
                    isSelected: store.isParticipating,
                    gradient: Color.gradientChicken
                ) {
                    store.send(.participationChanged(true))
                }

                SelectionCard(
                    title: "I'm organizing",
                    emoji: "📋",
                    subtitle: "You create the game for others",
                    isSelected: !store.isParticipating,
                    gradient: Color.gradientHunter
                ) {
                    store.send(.participationChanged(false))
                }
            }
            .padding(.horizontal, 24)
            Spacer()
        }
    }
}
