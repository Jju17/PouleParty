//
//  MaxPlayersStep.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

struct MaxPlayersStep: GameCreationStepView {
    static let step: GameCreationStep = .maxPlayers
    @Bindable var store: StoreOf<GameCreationFeature>

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            StepHeader(
                title: "Number of players",
                subtitle: "How many hunters can join?"
            )

            VStack(spacing: 12) {
                BangerText("\(store.currentGame.maxPlayers)", size: 64)
                    .foregroundStyle(Color.CROrange)

                let range = store.maxPlayersRange
                Stepper(
                    value: Binding(
                        get: { store.currentGame.maxPlayers },
                        set: { store.send(.maxPlayersChanged($0)) }
                    ),
                    in: range,
                    step: 1
                ) {
                    Text("\(store.currentGame.maxPlayers) hunters")
                        .font(.gameboy(size: 10))
                        .foregroundStyle(Color.onBackground)
                }
                .labelsHidden()
                .tint(Color.CROrange)

                Text("Between \(range.lowerBound) and \(range.upperBound) hunters")
                    .font(.gameboy(size: 8))
                    .foregroundStyle(Color.onBackground.opacity(0.6))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }

            Spacer()
        }
    }
}
