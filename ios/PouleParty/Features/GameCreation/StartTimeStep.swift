//
//  StartTimeStep.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

struct StartTimeStep: GameCreationStepView {
    static let step: GameCreationStep = .startTime
    @Bindable var store: StoreOf<GameCreationFeature>

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            StepHeader(
                title: "Start Time",
                subtitle: "When does the hunt begin?"
            )

            DatePicker(
                "Start at",
                selection: Binding(
                    get: { store.currentGame.startDate },
                    set: { store.send(.startDateChanged($0)) }
                ),
                in: store.minimumStartDate...
            )
            .datePickerStyle(.graphical)
            .tint(Color.CROrange)
            .padding(.horizontal, 24)

            VStack(alignment: .leading, spacing: 8) {
                Toggle(isOn: Binding(
                    get: { store.currentGame.manualStartEnabled },
                    set: { store.send(.manualStartChanged($0)) }
                )) {
                    Text("Manual launch")
                        .font(.headline.bold())
                        .foregroundStyle(Color.onSurface)
                }
                .tint(Color.CROrange)

                Text(
                    store.currentGame.manualStartEnabled
                        ? "At the planned time a LAUNCH button appears. The party starts when you tap it. The time above is indicative only."
                        : "Toggle on to gate the start behind a LAUNCH button. Useful when logistics run late."
                )
                .font(.subheadline)
                .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 24)

            Spacer()
        }
    }
}
