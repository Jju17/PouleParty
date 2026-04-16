//
//  RegistrationStep.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

struct RegistrationStep: GameCreationStepView {
    static let step: GameCreationStep = .registration
    @Bindable var store: StoreOf<GameCreationFeature>

    var body: some View {
        let isDepositPlan = store.currentGame.pricing.model == .deposit
        let showDeadline = store.currentGame.registration.required
        return VStack(spacing: 0) {
            Spacer()
            StepHeader(
                title: String(localized: "Registration"),
                subtitle: String(localized: "Do hunters need to register before joining?")
            )
            .padding(.bottom, 20)

            // Cards block — moves up/down as a unit
            VStack(spacing: 12) {
                SelectionCard(
                    title: String(localized: "Open join"),
                    emoji: "🚪",
                    subtitle: String(localized: "Anyone with the code can join directly"),
                    isSelected: !store.currentGame.registration.required,
                    gradient: Color.gradientHunter
                ) {
                    if !isDepositPlan {
                        _ = withAnimation(.easeOut(duration: 0.3)) {
                            store.send(.requiresRegistrationChanged(false))
                        }
                    }
                }
                .opacity(isDepositPlan ? 0.4 : 1)
                .compositingGroup()

                SelectionCard(
                    title: String(localized: "Registration required"),
                    emoji: "📝",
                    subtitle: String(localized: "Hunters must register a team name before joining"),
                    isSelected: store.currentGame.registration.required,
                    gradient: Color.gradientFire
                ) {
                    if !isDepositPlan {
                        _ = withAnimation(.easeOut(duration: 0.3)) {
                            store.send(.requiresRegistrationChanged(true))
                        }
                    }
                }
                .compositingGroup()

                if isDepositPlan {
                    Text("Registration is required for paid (deposit) games.")
                        .font(.gameboy(size: 8))
                        .foregroundStyle(Color.onBackground.opacity(0.6))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }
            }
            .padding(.horizontal, 24)
            .zIndex(1) // cards stay on top

            // Deadline picker — slides out from behind the cards
            if showDeadline {
                deadlinePicker
                    .padding(.horizontal, 24)
                    .padding(.top, 12)
                    .transition(.offset(y: -40).combined(with: .opacity))
            }

            Spacer()
        }
    }

    private var deadlinePicker: some View {
        VStack(spacing: 12) {
            Text(String(localized: "Registration closes"))
                .font(.gameboy(size: 9))
                .foregroundStyle(Color.onBackground.opacity(0.7))

            let options: [(label: String, minutes: Int?)] = [
                (String(localized: "At game start"), nil),
                (String(localized: "15 min before"), 15),
                (String(localized: "30 min before"), 30),
                (String(localized: "1 hour before"), 60),
                (String(localized: "2 hours before"), 120),
                (String(localized: "1 day before"), 1440),
            ]
            let current = store.currentGame.registration.closesMinutesBefore
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                ForEach(Array(options.enumerated()), id: \.offset) { _, option in
                    let isSelected = current == option.minutes
                    Button {
                        store.send(.registrationClosesBeforeStartChanged(option.minutes))
                    } label: {
                        Text(option.label)
                            .font(.gameboy(size: 9))
                            .frame(maxWidth: .infinity, minHeight: 38)
                            .foregroundStyle(isSelected ? .white : Color.onBackground)
                            .background(
                                isSelected
                                    ? AnyShapeStyle(Color.gradientFire)
                                    : AnyShapeStyle(Color.surface)
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.onBackground.opacity(isSelected ? 0 : 0.15), lineWidth: 1)
                            )
                    }
                }
            }
        }
    }
}
