//
//  PlanSelection.swift
//  PouleParty
//

import ComposableArchitecture
import SwiftUI

@Reducer
struct PlanSelectionFeature {

    @ObservableState
    struct State: Equatable {
        var selectedPlan: Game.PricingModel?
        var numberOfPlayers: Double = 10
        var pricePerPlayer: String = ""
        var depositAmount: String = ""

        var step: Step {
            selectedPlan == nil ? .choosePlan : .configurePricing
        }

        enum Step { case choosePlan, configurePricing }
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case planTileTapped(Game.PricingModel)
        case backToPlans
        case confirmPricing
        case planSelected(Game.PricingModel, numberOfPlayers: Int, pricePerPlayerCents: Int, depositAmountCents: Int)
    }

    var body: some ReducerOf<Self> {
        BindingReducer()
        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case let .planTileTapped(plan):
                if plan == .free {
                    return .send(.planSelected(.free, numberOfPlayers: 5, pricePerPlayerCents: 0, depositAmountCents: 0))
                }
                state.selectedPlan = plan
                if plan == .flat {
                    state.numberOfPlayers = 10
                    state.pricePerPlayer = ""
                }
                if plan == .deposit {
                    state.depositAmount = ""
                    state.pricePerPlayer = ""
                }
                return .none
            case .backToPlans:
                state.selectedPlan = nil
                return .none
            case .confirmPricing:
                guard let plan = state.selectedPlan else { return .none }
                let numPlayers = plan == .flat ? Int(state.numberOfPlayers) : 10
                let priceCents: Int
                let depositCents: Int
                switch plan {
                case .flat:
                    let perPlayer: Int
                    switch numPlayers {
                    case ...10: perPlayer = 300
                    case ...20: perPlayer = 200
                    default: perPlayer = 100
                    }
                    priceCents = perPlayer
                    depositCents = 0
                case .deposit:
                    priceCents = (Int(state.pricePerPlayer) ?? 0) * 100
                    depositCents = (Int(state.depositAmount) ?? 0) * 100
                case .free:
                    priceCents = 0
                    depositCents = 0
                }
                return .send(.planSelected(plan, numberOfPlayers: numPlayers, pricePerPlayerCents: priceCents, depositAmountCents: depositCents))
            case .planSelected:
                return .none
            }
        }
    }
}

struct PlanSelectionView: View {
    @Bindable var store: StoreOf<PlanSelectionFeature>

    var body: some View {
        VStack(spacing: 0) {
            BangerText("Create Party", size: 28)
                .foregroundStyle(Color.onBackground)
                .padding(.top, 32)
                .padding(.bottom, 8)

            Text(store.step == .choosePlan ? "Choose your plan" : store.selectedPlan?.title ?? "")
                .font(.gameboy(size: 10))
                .foregroundStyle(Color.onBackground.opacity(0.7))
                .padding(.bottom, 32)

            switch store.step {
            case .choosePlan:
                planTiles
            case .configurePricing:
                pricingConfig
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.gradientBackgroundWarmth)
        .animation(.easeInOut(duration: 0.25), value: store.step)
    }

    private var planTiles: some View {
        VStack(spacing: 16) {
            PlanTile(
                title: "FREE",
                subtitle: "1 game per day, up to 5 players",
                icon: "🎮"
            ) {
                store.send(.planTileTapped(.free))
            }

            PlanTile(
                title: "Forfait",
                subtitle: "Pay once for unlimited players",
                icon: "⭐",
                gradient: Color.gradientFire,
                isFeatured: true
            ) {
                store.send(.planTileTapped(.flat))
            }

            PlanTile(
                title: "Caution + %",
                subtitle: "Deposit + commission per player",
                icon: "💎",
                gradient: Color.gradientDeposit
            ) {
                store.send(.planTileTapped(.deposit))
            }
        }
        .padding(.horizontal, 24)
    }

    @ViewBuilder
    private var pricingConfig: some View {
        VStack(spacing: 24) {
            if store.selectedPlan == .flat {
                flatConfig
            } else if store.selectedPlan == .deposit {
                depositConfig
            }

            Button {
                store.send(.confirmPricing)
            } label: {
                BangerText("Continue", size: 22)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Color.gradientFire)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
            }
            .buttonStyle(.plain)

            Button {
                store.send(.backToPlans)
            } label: {
                Text("Back")
                    .font(.gameboy(size: 9))
                    .foregroundStyle(Color.onBackground.opacity(0.6))
            }
        }
        .padding(.horizontal, 24)
    }

    private var flatConfig: some View {
        VStack(spacing: 16) {
            Text("Number of players")
                .font(.gameboy(size: 9))
                .foregroundStyle(Color.onBackground.opacity(0.7))

            BangerText("\(Int(store.numberOfPlayers))", size: 48)
                .foregroundStyle(Color.onBackground)

            Slider(value: $store.numberOfPlayers, in: 6...50, step: 1)
                .tint(Color.crOrange)

            PriceSummaryRow(
                label: "Price per player",
                value: "\(flatPricePerPlayer)€"
            )

            PriceSummaryRow(
                label: "Total",
                value: "\(Int(store.numberOfPlayers) * flatPricePerPlayer)€",
                isTotal: true
            )
        }
    }

    private var depositConfig: some View {
        VStack(spacing: 16) {
            PriceField(
                label: "Deposit amount",
                placeholder: "10",
                text: $store.depositAmount,
                suffix: "€"
            )

            PriceField(
                label: "Price per hunter",
                placeholder: "5",
                text: $store.pricePerPlayer,
                suffix: "€"
            )

            if let deposit = Int(store.depositAmount), let price = Int(store.pricePerPlayer), price > 0 {
                let commission = Int(Double(price) * 0.15)
                PriceSummaryRow(
                    label: "Your deposit",
                    value: "\(deposit)€"
                )
                PriceSummaryRow(
                    label: "Commission (15%)",
                    value: "\(commission)€ / player"
                )
                PriceSummaryRow(
                    label: "Hunter pays",
                    value: "\(price)€",
                    isTotal: true
                )
            }
        }
    }

    private var flatPricePerPlayer: Int {
        let n = Int(store.numberOfPlayers)
        switch n {
        case ...10: return 3
        case ...20: return 2
        default: return 1
        }
    }
}

// MARK: - Subviews

private struct PlanTile: View {
    let title: String
    let subtitle: String
    let icon: String
    var gradient: LinearGradient? = nil
    var isFeatured: Bool = false
    let action: () -> Void

    private var hasGradient: Bool { gradient != nil }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Text(icon)
                    .font(.system(size: 32))

                VStack(alignment: .leading, spacing: 4) {
                    BangerText(title, size: 22)
                        .foregroundStyle(hasGradient ? .white : Color.onBackground)

                    Text(subtitle)
                        .font(.gameboy(size: 7))
                        .foregroundStyle(hasGradient ? .white.opacity(0.8) : Color.onBackground.opacity(0.6))
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(hasGradient ? .white.opacity(0.8) : Color.onBackground.opacity(0.4))
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 18)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(gradient.map { AnyShapeStyle($0) } ?? AnyShapeStyle(Color.background))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(hasGradient ? Color.clear : Color.onBackground.opacity(0.15), lineWidth: 1.5)
            )
            .shadow(color: .black.opacity(isFeatured ? 0.2 : hasGradient ? 0.1 : 0.05), radius: isFeatured ? 8 : 5, y: 3)
        }
        .buttonStyle(.plain)
    }
}

private struct PriceSummaryRow: View {
    let label: String
    let value: String
    var isTotal: Bool = false

    var body: some View {
        HStack {
            Text(label)
                .font(.gameboy(size: isTotal ? 9 : 8))
                .foregroundStyle(Color.onBackground.opacity(isTotal ? 1 : 0.6))
            Spacer()
            BangerText(value, size: isTotal ? 24 : 18)
                .foregroundStyle(isTotal ? Color.crOrange : Color.onBackground)
        }
    }
}

private struct PriceField: View {
    let label: String
    let placeholder: String
    @Binding var text: String
    let suffix: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.gameboy(size: 8))
                .foregroundStyle(Color.onBackground.opacity(0.7))

            HStack {
                TextField(placeholder, text: $text)
                    .keyboardType(.numberPad)
                    .font(.banger(size: 24))
                    .foregroundStyle(Color.onBackground)

                BangerText(suffix, size: 24)
                    .foregroundStyle(Color.onBackground.opacity(0.5))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.background)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.onBackground.opacity(0.15), lineWidth: 1.5)
            )
        }
    }
}
