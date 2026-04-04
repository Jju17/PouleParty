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
        var plansConfig: PartyPlansConfig?
        var isLoading: Bool = true
        var loadFailed: Bool = false
        var selectedPlan: Game.PricingModel?
        // Flat
        var numberOfPlayers: Double = 10
        // Deposit
        var pricePerHunter: String = ""
        var hasMaxPlayers: Bool = false
        var maxPlayers: Double = 20

        var step: Step {
            selectedPlan == nil ? .choosePlan : .configurePricing
        }

        enum Step { case choosePlan, configurePricing }
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case onAppear
        case plansConfigLoaded(PartyPlansConfig)
        case plansConfigFailed
        case retryTapped
        case planTileTapped(Game.PricingModel)
        case backToPlans
        case confirmPricing
        case planSelected(Game.PricingModel, numberOfPlayers: Int, pricePerPlayerCents: Int, depositAmountCents: Int)
    }

    @Dependency(\.apiClient) var apiClient

    var body: some ReducerOf<Self> {
        BindingReducer()
        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case .onAppear:
                state.isLoading = true
                state.loadFailed = false
                return .run { send in
                    do {
                        let config = try await apiClient.fetchPartyPlansConfig()
                        await send(.plansConfigLoaded(config))
                    } catch {
                        await send(.plansConfigFailed)
                    }
                }
            case let .plansConfigLoaded(config):
                state.plansConfig = config
                state.numberOfPlayers = Double(config.flat.minPlayers)
                state.isLoading = false
                return .none
            case .plansConfigFailed:
                state.isLoading = false
                state.loadFailed = true
                return .none
            case .retryTapped:
                return .send(.onAppear)
            case let .planTileTapped(plan):
                if plan == .free {
                    let maxPlayers = state.plansConfig?.free.maxPlayers ?? 5
                    return .send(.planSelected(.free, numberOfPlayers: maxPlayers, pricePerPlayerCents: 0, depositAmountCents: 0))
                }
                state.selectedPlan = plan
                if plan == .flat, let config = state.plansConfig {
                    state.numberOfPlayers = Double(config.flat.minPlayers)
                }
                if plan == .deposit {
                    state.pricePerHunter = ""
                    state.hasMaxPlayers = false
                    state.maxPlayers = 20
                }
                return .none
            case .backToPlans:
                state.selectedPlan = nil
                return .none
            case .confirmPricing:
                guard let plan = state.selectedPlan, let config = state.plansConfig else { return .none }
                switch plan {
                case .flat:
                    let numPlayers = Int(state.numberOfPlayers)
                    return .send(.planSelected(
                        .flat,
                        numberOfPlayers: numPlayers,
                        pricePerPlayerCents: config.flat.pricePerPlayerCents,
                        depositAmountCents: 0
                    ))
                case .deposit:
                    let priceCents = (Int(state.pricePerHunter) ?? 0) * 100
                    let numPlayers = state.hasMaxPlayers ? Int(state.maxPlayers) : 50
                    return .send(.planSelected(
                        .deposit,
                        numberOfPlayers: numPlayers,
                        pricePerPlayerCents: priceCents,
                        depositAmountCents: config.deposit.depositAmountCents
                    ))
                case .free:
                    return .none
                }
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

            if store.isLoading {
                Spacer()
                ProgressView()
                    .tint(Color.CROrange)
                Spacer()
            } else if store.loadFailed {
                Spacer()
                VStack(spacing: 16) {
                    Text("Failed to load pricing")
                        .font(.gameboy(size: 9))
                        .foregroundStyle(Color.onBackground.opacity(0.7))
                    Button {
                        store.send(.retryTapped)
                    } label: {
                        BangerText("Retry", size: 20)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 32)
                            .padding(.vertical, 12)
                            .background(Color.CROrange)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
            } else {
                ScrollView {
                    Text(store.step == .choosePlan ? "Choose your plan" : store.selectedPlan?.title ?? "")
                        .font(.gameboy(size: 10))
                        .foregroundStyle(Color.onBackground.opacity(0.7))
                        .padding(.bottom, 32)

                    switch store.step {
                    case .choosePlan:
                        planTiles
                    case .configurePricing:
                        plansConfigView
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.gradientBackgroundWarmth)
        .animation(.easeInOut(duration: 0.25), value: store.step)
        .task { store.send(.onAppear) }
    }

    @ViewBuilder
    private var planTiles: some View {
        if let config = store.plansConfig {
            VStack(spacing: 16) {
                PlanTile(
                    title: "FREE",
                    subtitle: "1 game per day, up to \(config.free.maxPlayers) players",
                    icon: "🎮",
                    isEnabled: config.free.enabled
                ) {
                    store.send(.planTileTapped(.free))
                }

                PlanTile(
                    title: "Forfait",
                    subtitle: "\(config.flat.pricePerPlayerCents / 100)€ per player",
                    icon: "⭐",
                    gradient: Color.gradientFire,
                    isFeatured: true,
                    isEnabled: config.flat.enabled
                ) {
                    store.send(.planTileTapped(.flat))
                }

                PlanTile(
                    title: "Caution + %",
                    subtitle: "\(config.deposit.depositAmountCents / 100)€ deposit + \(Int(config.deposit.commissionPercent))% commission",
                    icon: "💎",
                    gradient: Color.gradientDeposit,
                    isEnabled: config.deposit.enabled
                ) {
                    store.send(.planTileTapped(.deposit))
                }
            }
            .padding(.horizontal, 24)
        }
    }

    @ViewBuilder
    private var plansConfigView: some View {
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

    @ViewBuilder
    private var flatConfig: some View {
        let config = store.plansConfig ?? PartyPlansConfig()
        let pricePerPlayer = config.flat.pricePerPlayerCents / 100
        let total = Int(store.numberOfPlayers) * pricePerPlayer

        return VStack(spacing: 16) {
            Text("Number of players")
                .font(.gameboy(size: 9))
                .foregroundStyle(Color.onBackground.opacity(0.7))

            BangerText("\(Int(store.numberOfPlayers))", size: 48)
                .foregroundStyle(Color.onBackground)

            Slider(
                value: $store.numberOfPlayers,
                in: Double(config.flat.minPlayers)...Double(config.flat.maxPlayers),
                step: 1
            )
            .tint(Color.CROrange)

            PriceSummaryRow(
                label: "Price per player",
                value: "\(pricePerPlayer)€"
            )

            PriceSummaryRow(
                label: "Total",
                value: "\(total)€",
                isTotal: true
            )
        }
    }

    @ViewBuilder
    private var depositConfig: some View {
        let config = store.plansConfig ?? PartyPlansConfig()
        let depositEuros = config.deposit.depositAmountCents / 100
        let commissionPct = Int(config.deposit.commissionPercent)

        return VStack(spacing: 16) {
            // Admin-defined info
            PriceSummaryRow(label: "Deposit", value: "\(depositEuros)€")
            PriceSummaryRow(label: "Commission", value: "\(commissionPct)%")

            Divider()

            // User sets price per hunter
            PriceField(
                label: "Price per hunter",
                placeholder: "5",
                text: $store.pricePerHunter,
                suffix: "€"
            )

            // Optional max players
            Toggle(isOn: $store.hasMaxPlayers) {
                Text("Limit number of players")
                    .font(.gameboy(size: 8))
                    .foregroundStyle(Color.onBackground.opacity(0.7))
            }
            .tint(Color.CROrange)

            if store.hasMaxPlayers {
                VStack(spacing: 8) {
                    BangerText("\(Int(store.maxPlayers)) players max", size: 22)
                        .foregroundStyle(Color.onBackground)

                    Slider(value: $store.maxPlayers, in: 2...50, step: 1)
                        .tint(Color.CROrange)
                }
            }

            // Summary
            if let price = Int(store.pricePerHunter), price > 0 {
                Divider()

                let commission = Double(price) * Double(commissionPct) / 100.0
                PriceSummaryRow(
                    label: "You earn per hunter",
                    value: String(format: "%.2f€", Double(price) - commission)
                )
                PriceSummaryRow(
                    label: "Hunter pays",
                    value: "\(price)€",
                    isTotal: true
                )
            }
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
    var isEnabled: Bool = true
    let action: () -> Void

    private var hasGradient: Bool { gradient != nil }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Text(icon)
                    .font(.system(size: 32))
                    .grayscale(isEnabled ? 0 : 1)

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
            .opacity(isEnabled ? 1 : 0.4)
            .saturation(isEnabled ? 1 : 0)
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
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
                .foregroundStyle(isTotal ? Color.CROrange : Color.onBackground)
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
