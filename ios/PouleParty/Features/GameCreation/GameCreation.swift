//
//  GameCreation.swift
//  PouleParty
//
//  Created by Julien Rahier on 04/04/2026.
//

import ComposableArchitecture
import CoreLocation
import SwiftUI

// MARK: - Step Enum

enum GameCreationStep: Equatable {
    case participation
    case chickenSelection
    case gameMode
    case zoneSetup
    case startTime
    case duration
    case headStart
    case powerUps
    case chickenSeesHunters
    case registration
    case recap
}

// MARK: - Reducer

@Reducer
struct GameCreationFeature {

    @ObservableState
    struct State: Equatable {
        @Presents var destination: Destination.State?
        @Shared var game: Game
        var currentStepIndex: Int = 0
        var isParticipating: Bool = true
        var gameDurationMinutes: Double = 90
        var showPowerUpSelection: Bool = false
        var mapConfigState: ChickenMapConfigFeature.State
        var goingForward: Bool = true

        var steps: [GameCreationStep] {
            var result: [GameCreationStep] = [.participation]
            if !isParticipating {
                result.append(.chickenSelection)
            }
            result.append(contentsOf: [
                .gameMode,
                .zoneSetup,
                .registration,
                .startTime,
                .duration,
                .headStart,
                .powerUps,
            ])
            result.append(.chickenSeesHunters)
            result.append(.recap)
            return result
        }

        var currentStep: GameCreationStep {
            let allSteps = steps
            let clampedIndex = min(currentStepIndex, allSteps.count - 1)
            return allSteps[max(0, clampedIndex)]
        }

        var progress: Double {
            let allSteps = steps
            guard !allSteps.isEmpty else { return 0 }
            return Double(currentStepIndex + 1) / Double(allSteps.count)
        }

        var canGoBack: Bool {
            currentStepIndex > 0
        }

        var isZoneConfigured: Bool {
            let center = game.zone.center
            let isDefault = abs(center.latitude - AppConstants.defaultLatitude) < 0.001
                && abs(center.longitude - AppConstants.defaultLongitude) < 0.001
            guard !isDefault else { return false }
            if game.gameMode == .stayInTheZone {
                return game.zone.finalCenter != nil
            }
            return true
        }

        /// Minimum start time based on registration settings.
        /// Open join: now + 1 minute.
        /// Registration required: now + deadline + 5 minutes buffer.
        var minimumStartDate: Date {
            let bufferMinutes: Double = 5
            if game.registration.required {
                if let deadline = game.registration.closesMinutesBefore {
                    return Date.now.addingTimeInterval((Double(deadline) + bufferMinutes) * 60)
                }
                return Date.now.addingTimeInterval(bufferMinutes * 60)
            }
            return Date.now.addingTimeInterval(60)
        }

        var currentGame: Game { game }
    }

    enum Action: BindableAction {
        case backTapped
        case binding(BindingAction<State>)
        case chickenCanSeeHuntersChanged(Bool)
        case chickenHeadStartChanged(Double)
        case configSaveFailed
        case destination(PresentationAction<Destination.Action>)
        case gameCreated(Game)
        /// Forfait flow: server created the game doc (in `.pendingPayment`) via
        /// the Stripe webhook path. Parent should dismiss creation UI and leave
        /// the user on Home — the game will appear under `My Games` once the
        /// webhook flips status to `.waiting` (a second or two).
        case paidGameCreated(game: Game)
        case gameDurationChanged(Double)
        case gameModChanged(Game.GameMode)
        case initialRadiusChanged(Double)
        case mapConfig(ChickenMapConfigFeature.Action)
        case nextTapped
        case participationChanged(Bool)
        case powerUpsToggled(Bool)
        case powerUpTypeToggled(PowerUp.PowerUpType)
        case registrationClosesBeforeStartChanged(Int?)
        case requiresRegistrationChanged(Bool)
        case startDateChanged(Date)
        case startGameButtonTapped
    }

    @Reducer
    struct Destination {
        @ObservableState
        enum State: Equatable {
            case alert(AlertState<Action.Alert>)
            case payment(PaymentFeature.State)
        }

        enum Action {
            case alert(Alert)
            case payment(PaymentFeature.Action)

            enum Alert: Equatable { }
        }

        var body: some ReducerOf<Self> {
            Scope(state: \.payment, action: \.payment) {
                PaymentFeature()
            }
        }
    }

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.locationClient) var locationClient
    @Dependency(\.analyticsClient) var analyticsClient

    /// Pushes the start date forward if it falls before the minimum allowed.
    private func clampStartDateToMinimum(state: inout State) {
        let minimum = state.minimumStartDate
        if state.game.startDate < minimum {
            state.$game.withLock { $0.startDate = minimum }
        }
    }

    private func recalculateNormalMode(state: inout State) {
        let effectiveDuration = max(state.gameDurationMinutes - state.game.timing.headStartMinutes, 1)
        let (interval, decline) = calculateNormalModeSettings(
            initialRadius: state.game.zone.radius,
            gameDurationMinutes: effectiveDuration
        )
        state.$game.withLock { game in
            game.zone.shrinkIntervalMinutes = interval
            game.zone.shrinkMetersPerUpdate = decline
        }
    }

    var body: some ReducerOf<Self> {
        BindingReducer()

        Scope(state: \.mapConfigState, action: \.mapConfig) {
            ChickenMapConfigFeature()
        }

        Reduce { state, action in
            switch action {
            case .backTapped:
                if state.currentStepIndex > 0 {
                    state.goingForward = false
                    state.currentStepIndex -= 1
                }
                clampStartDateToMinimum(state: &state)
                return .none

            case .binding:
                return .none

            case let .chickenCanSeeHuntersChanged(value):
                state.$game.withLock { $0.chickenCanSeeHunters = value }
                return .none

            case let .chickenHeadStartChanged(minutes):
                state.$game.withLock { $0.timing.headStartMinutes = minutes }
                recalculateNormalMode(state: &state)
                return .none

            case .configSaveFailed:
                state.destination = .alert(
                    AlertState {
                        TextState("Error")
                    } actions: {
                        ButtonState(role: .cancel) {
                            TextState("OK")
                        }
                    } message: {
                        TextState("Could not create the game. Please check your connection and try again.")
                    }
                )
                return .none

            case let .destination(.presented(.payment(.delegate(.creatorPaymentConfirmed(gameId))))):
                state.destination = nil
                analyticsClient.gameCreated(
                    gameMode: state.game.gameMode.rawValue,
                    maxPlayers: state.game.maxPlayers,
                    pricingModel: state.game.pricing.model.rawValue,
                    powerUpsEnabled: state.game.powerUps.enabled
                )
                // The Cloud Function `createCreatorPaymentSheet` ignores any
                // client-supplied id and writes the game doc at its own Firestore
                // auto-ID (see `functions/src/stripe.ts:195`). Patch the local
                // game snapshot with the authoritative server id before handing
                // it to the confirmation screen, otherwise its `gameConfigStream`
                // subscribes to the orphan client-side id and never sees the
                // webhook flip `pending_payment → waiting`.
                state.$game.withLock { $0.id = gameId }
                return .send(.paidGameCreated(game: state.game))

            case .destination(.presented(.payment(.delegate(.cancelled)))):
                state.destination = nil
                return .none

            case .destination:
                return .none

            case let .gameDurationChanged(duration):
                state.gameDurationMinutes = duration
                recalculateNormalMode(state: &state)
                return .none

            case let .gameModChanged(mode):
                state.$game.withLock { $0.gameMode = mode }
                // Switching to Follow the Chicken: the final zone is dynamically the
                // chicken's live position, so clear any manually-placed final zone
                // and reset the pin mode to start.
                if mode == .followTheChicken {
                    state.$game.withLock { $0.finalLocation = nil }
                    state.mapConfigState.finalMarker = nil
                    state.mapConfigState.pinMode = .start
                }
                return .none

            case let .initialRadiusChanged(radius):
                state.$game.withLock { $0.zone.radius = radius }
                recalculateNormalMode(state: &state)
                return .none

            case .mapConfig:
                return .none

            case .nextTapped:
                let maxIndex = state.steps.count - 1
                if state.currentStepIndex < maxIndex {
                    state.goingForward = true
                    state.currentStepIndex += 1
                }
                clampStartDateToMinimum(state: &state)
                return .none

            case let .participationChanged(participating):
                state.isParticipating = participating
                return .none

            case let .powerUpsToggled(enabled):
                state.$game.withLock { $0.powerUps.enabled = enabled }
                return .none

            case let .registrationClosesBeforeStartChanged(minutes):
                state.$game.withLock { $0.registration.closesMinutesBefore = minutes }
                clampStartDateToMinimum(state: &state)
                return .none

            case let .requiresRegistrationChanged(value):
                state.$game.withLock { game in
                    game.registration.required = value
                    if value {
                        game.registration.closesMinutesBefore = game.registration.closesMinutesBefore ?? 15
                    } else {
                        game.registration.closesMinutesBefore = nil
                    }
                }
                clampStartDateToMinimum(state: &state)
                return .none

            case let .powerUpTypeToggled(type):
                state.$game.withLock { game in
                    if let index = game.powerUps.enabledTypes.firstIndex(of: type.rawValue) {
                        let unavailableRaw: Set<String> = game.gameMode == .stayInTheZone
                            ? [PowerUp.PowerUpType.invisibility.rawValue, PowerUp.PowerUpType.decoy.rawValue, PowerUp.PowerUpType.jammer.rawValue]
                            : []
                        let availableEnabledCount = game.powerUps.enabledTypes.filter { !unavailableRaw.contains($0) }.count
                        let isAvailable = !unavailableRaw.contains(type.rawValue)
                        if !isAvailable || availableEnabledCount > 1 {
                            game.powerUps.enabledTypes.remove(at: index)
                        }
                    } else {
                        game.powerUps.enabledTypes.append(type.rawValue)
                    }
                }
                return .none

            case let .startDateChanged(date):
                state.$game.withLock { $0.startDate = date }
                return .none

            case .startGameButtonTapped:
                clampStartDateToMinimum(state: &state)
                state.$game.withLock { game in
                    game.endDate = game.startDate.addingTimeInterval(state.gameDurationMinutes * 60)
                }
                recalculateNormalMode(state: &state)
                // Forfait requires an up-front creator payment. Rest of the flow
                // (free / caution) stays client-created: for caution the creator
                // doesn't pay right now — only hunters do, at registration.
                if state.game.pricing.model == .flat {
                    state.destination = .payment(PaymentFeature.State(context: .creatorForfait(gameConfig: state.game)))
                    return .none
                }
                return .run { [state = state, analyticsClient] send in
                    do {
                        try await apiClient.setConfig(state.game)
                        analyticsClient.gameCreated(
                            gameMode: state.game.gameMode.rawValue,
                            maxPlayers: state.game.maxPlayers,
                            pricingModel: state.game.pricing.model.rawValue,
                            powerUpsEnabled: state.game.powerUps.enabled
                        )
                        await send(.gameCreated(state.game))
                    } catch {
                        await send(.configSaveFailed)
                    }
                }

            case .gameCreated, .paidGameCreated:
                return .none
            }
        }
        .ifLet(\.$destination, action: \.destination) {
            Destination()
        }
    }
}

// MARK: - View

struct GameCreationView: View {
    @Bindable var store: StoreOf<GameCreationFeature>
    var onDismiss: (() -> Void)?

    private var isMapStep: Bool { store.currentStep == .zoneSetup }

    var body: some View {
        VStack(spacing: 0) {
            // Fixed header: progress bar + dismiss button
            HStack {
                ProgressView(value: store.progress)
                    .tint(Color.CROrange)
                if let onDismiss {
                    Button(action: onDismiss) {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Color.onBackground.opacity(0.6))
                            .padding(10)
                            .background(Color.surface)
                            .clipShape(Circle())
                            .shadow(color: .black.opacity(0.1), radius: 2, y: 1)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            .padding(.bottom, 4)

            // Step description bar (for map step)
            if isMapStep {
                VStack(spacing: 2) {
                    BangerText(String(localized: "Configure the zone"), size: 20)
                        .foregroundStyle(Color.onBackground)
                    Text(store.isZoneConfigured
                         ? String(localized: "Tap the map to adjust your zones")
                         : store.currentGame.gameMode == .stayInTheZone
                            ? String(localized: "Set a start zone and final zone to start")
                            : String(localized: "Set a start zone to start"))
                        .font(.gameboy(size: 8))
                        .foregroundStyle(Color.onBackground.opacity(0.6))
                        .multilineTextAlignment(.center)
                }
                .padding(.vertical, 8)
                .frame(maxWidth: .infinity)
                .background(Color.background)
            }

            // Step content
            stepContent(for: store.currentStep)
                .id(store.currentStep)
                .transition(.push(from: store.goingForward ? .trailing : .leading))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .ignoresSafeArea(edges: isMapStep ? .top : [])
                .clipped()

            // Fixed bottom bar
            bottomBar
        }
        .animation(.linear(duration: 0.15), value: store.currentStep)
        .background(Color.background)
        .sheet(isPresented: $store.showPowerUpSelection) {
            PowerUpSelectionView(
                enabledTypes: store.currentGame.powerUps.enabledTypes,
                gameMode: store.currentGame.gameMode,
                onToggle: { type in store.send(.powerUpTypeToggled(type)) }
            )
        }
        .alert(
            $store.scope(
                state: \.destination?.alert,
                action: \.destination.alert
            )
        )
        .sheet(
            item: $store.scope(state: \.destination?.payment, action: \.destination.payment)
        ) { paymentStore in
            PaymentView(store: paymentStore)
        }
    }

    // MARK: - Bottom Bar

    private var bottomBar: some View {
        HStack {
            if store.canGoBack {
                Button {
                    store.send(.backTapped)
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                        Text("Back")
                            .font(.gameboy(size: 10))
                    }
                    .foregroundStyle(Color.onBackground)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .background(Color.surface)
                    .clipShape(Capsule())
                    .shadow(color: .black.opacity(0.2), radius: 6, y: 3)
                }
            }

            Spacer()

            if store.currentStep == .recap {
                Button {
                    store.send(.startGameButtonTapped)
                } label: {
                    BangerText("Start Game", size: 22)
                        .foregroundStyle(.black)
                        .padding(.horizontal, 28)
                        .padding(.vertical, 14)
                        .background(
                            store.isZoneConfigured
                                ? AnyShapeStyle(Color.gradientFire)
                                : AnyShapeStyle(Color.gray.opacity(0.3))
                        )
                        .clipShape(Capsule())
                        .shadow(color: .black.opacity(store.isZoneConfigured ? 0.2 : 0), radius: 6, y: 3)
                }
                .disabled(!store.isZoneConfigured)
            } else {
                let nextDisabled = isMapStep && !store.isZoneConfigured
                Button {
                    store.send(.nextTapped)
                } label: {
                    HStack(spacing: 4) {
                        Text("Next")
                            .font(.gameboy(size: 10))
                        Image(systemName: "chevron.right")
                    }
                    .foregroundStyle(nextDisabled ? .black.opacity(0.4) : .black)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .background(nextDisabled ? AnyShapeStyle(Color.gray.opacity(0.3)) : AnyShapeStyle(Color.gradientFire))
                    .clipShape(Capsule())
                    .shadow(color: .black.opacity(nextDisabled ? 0 : 0.25), radius: 6, y: 3)
                }
                .disabled(nextDisabled)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
    }

    // MARK: - Step Content

    @ViewBuilder
    private func stepContent(for step: GameCreationStep) -> some View {
        switch step {
        case .participation:       ParticipationStep(store: store)
        case .chickenSelection:    ChickenSelectionStep(store: store)
        case .gameMode:            GameModeStep(store: store)
        case .zoneSetup:           ZoneSetupStep(store: store)
        case .startTime:           StartTimeStep(store: store)
        case .duration:            DurationStep(store: store)
        case .headStart:           HeadStartStep(store: store)
        case .powerUps:            PowerUpsStep(store: store)
        case .chickenSeesHunters:  ChickenSeesHuntersStep(store: store)
        case .registration:        RegistrationStep(store: store)
        case .recap:               RecapStep(store: store)
        }
    }
}

// MARK: - Preview

#Preview {
    GameCreationView(
        store: Store(
            initialState: GameCreationFeature.State(
                game: Shared(value: Game.mock),
                mapConfigState: ChickenMapConfigFeature.State(game: Shared(value: Game.mock))
            )
        ) {
            GameCreationFeature()
        }
    )
}
