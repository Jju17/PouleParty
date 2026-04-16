//
//  ChickenSeesHuntersStep.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

struct ChickenSeesHuntersStep: GameCreationStepView {
    static let step: GameCreationStep = .chickenSeesHunters
    @Bindable var store: StoreOf<GameCreationFeature>

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            StepHeader(
                title: "Chicken Visibility",
                subtitle: "Can the chicken see the hunters?"
            )

            VStack(spacing: 16) {
                SelectionCard(
                    title: "Yes",
                    emoji: "👀",
                    subtitle: "The chicken sees all hunters",
                    isSelected: store.currentGame.chickenCanSeeHunters,
                    gradient: Color.gradientChicken
                ) {
                    store.send(.chickenCanSeeHuntersChanged(true))
                }

                SelectionCard(
                    title: "No",
                    emoji: "🙈",
                    subtitle: "The chicken is blind",
                    isSelected: !store.currentGame.chickenCanSeeHunters,
                    gradient: Color.gradientHunter
                ) {
                    store.send(.chickenCanSeeHuntersChanged(false))
                }
            }
            .padding(.horizontal, 24)
            Spacer()
        }
    }
}
