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

            Spacer()
        }
    }
}
