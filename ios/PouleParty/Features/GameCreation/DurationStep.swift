//
//  DurationStep.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

struct DurationStep: GameCreationStepView {
    static let step: GameCreationStep = .duration
    @Bindable var store: StoreOf<GameCreationFeature>

    private let durationOptions: [(String, Double)] = [
        ("1h", 60),
        ("1h30", 90),
        ("2h", 120),
        ("2h30", 150),
        ("3h", 180),
    ]

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            StepHeader(
                title: "Game Duration",
                subtitle: "How long should the game last?"
            )

            VStack(spacing: 16) {
                ForEach(durationOptions, id: \.1) { option in
                    let isSelected = store.gameDurationMinutes == option.1
                    Button {
                        store.send(.gameDurationChanged(option.1))
                    } label: {
                        HStack {
                            BangerText(option.0, size: 28)
                                .foregroundStyle(isSelected ? .black : Color.onBackground)
                            Spacer()
                            if isSelected {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.title2)
                                    .foregroundStyle(.black)
                            }
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 14)
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(isSelected ? AnyShapeStyle(Color.gradientFire) : AnyShapeStyle(Color.surface))
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(isSelected ? Color.clear : Color.onBackground.opacity(0.2), lineWidth: 1)
                        )
                    }
                }
            }
            .padding(.horizontal, 24)

            let endDate = store.currentGame.startDate.addingTimeInterval(store.gameDurationMinutes * 60)
            HStack {
                Text("Ends at")
                    .font(.gameboy(size: 10))
                    .foregroundStyle(Color.onBackground.opacity(0.6))
                Spacer()
                Text(endDate, style: .time)
                    .font(.gameboy(size: 10))
                    .foregroundStyle(Color.CROrange)
            }
            .padding(.horizontal, 28)

            Spacer()
        }
    }
}
