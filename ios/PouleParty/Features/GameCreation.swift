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
        }

        enum Action {
            case alert(Alert)

            enum Alert: Equatable { }
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

            case .gameCreated:
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

    private let durationOptions: [(String, Double)] = [
        ("1h", 60),
        ("1h30", 90),
        ("2h", 120),
        ("2h30", 150),
        ("3h", 180),
    ]

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
        case .participation:
            participationStep
        case .chickenSelection:
            chickenSelectionStep
        case .gameMode:
            gameModeStep
        case .zoneSetup:
            zoneSetupStep
        case .startTime:
            startTimeStep
        case .duration:
            durationStep
        case .headStart:
            headStartStep
        case .powerUps:
            powerUpsStep
        case .chickenSeesHunters:
            chickenSeesHuntersStep
        case .registration:
            registrationStep
        case .recap:
            recapStep
        }
    }

    // MARK: - Participation Step

    private var participationStep: some View {
        VStack(spacing: 24) {
            Spacer()
            stepHeader(
                title: "Are you playing?",
                subtitle: "Choose your role"
            )

            VStack(spacing: 16) {
                selectionCard(
                    title: "I am the Chicken",
                    emoji: "🐔",
                    subtitle: "You run, you hide",
                    isSelected: store.isParticipating,
                    gradient: Color.gradientChicken
                ) {
                    store.send(.participationChanged(true))
                }

                selectionCard(
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

    // MARK: - Chicken Selection Step

    private var chickenSelectionStep: some View {
        VStack(spacing: 24) {
            Spacer()
            stepHeader(
                title: "Who's the Chicken?",
                subtitle: "How will the chicken be chosen?"
            )

            VStack(spacing: 16) {
                selectionCard(
                    title: "Random",
                    emoji: "🎲",
                    subtitle: "A random player",
                    isSelected: true,
                    gradient: Color.gradientChicken
                ) { }

                selectionCard(
                    title: "First to join",
                    emoji: "🏃",
                    subtitle: "First player to join",
                    isSelected: false,
                    gradient: Color.gradientFire
                ) { }

                selectionCard(
                    title: "I'll choose",
                    emoji: "👆",
                    subtitle: "I'll pick the chicken later",
                    isSelected: false,
                    gradient: Color.gradientHunter
                ) { }
            }
            .padding(.horizontal, 24)
            Spacer()
        }
    }

    // MARK: - Game Mode Step

    private var gameModeStep: some View {
        VStack(spacing: 24) {
            Spacer()
            stepHeader(
                title: "Game Mode",
                subtitle: "Choose the game mode"
            )

            VStack(spacing: 16) {
                selectionCard(
                    title: "Follow the Chicken",
                    emoji: "🐔",
                    subtitle: "The zone shrinks toward the chicken",
                    isSelected: store.currentGame.gameMode == .followTheChicken,
                    gradient: Color.gradientChicken
                ) {
                    store.send(.gameModChanged(.followTheChicken))
                }

                selectionCard(
                    title: "Stay in the Zone",
                    emoji: "📍",
                    subtitle: "Fixed zone that shrinks",
                    isSelected: store.currentGame.gameMode == .stayInTheZone,
                    gradient: Color.gradientHunter
                ) {
                    store.send(.gameModChanged(.stayInTheZone))
                }
            }
            .padding(.horizontal, 24)
            Spacer()
        }
    }

    // MARK: - Zone Setup Step

    private var zoneSetupStep: some View {
        ChickenMapConfigView(
            store: store.scope(state: \.mapConfigState, action: \.mapConfig)
        )
    }

    // MARK: - Start Time Step

    private var startTimeStep: some View {
        VStack(spacing: 24) {
            Spacer()
            stepHeader(
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

    // MARK: - Duration Step

    private var durationStep: some View {
        VStack(spacing: 24) {
            Spacer()
            stepHeader(
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

    // MARK: - Head Start Step

    private var headStartStep: some View {
        VStack(spacing: 24) {
            Spacer()
            stepHeader(
                title: "Head Start",
                subtitle: "Time to hide before the hunt"
            )

            VStack(spacing: 12) {
                BangerText("\(Int(store.currentGame.timing.headStartMinutes)) min", size: 48)
                    .foregroundStyle(Color.CROrange)

                Slider(
                    value: Binding(
                        get: { store.currentGame.timing.headStartMinutes },
                        set: { store.send(.chickenHeadStartChanged($0)) }
                    ),
                    in: 0...15,
                    step: 1
                )
                .tint(Color.CROrange)
                .padding(.horizontal, 40)

                Text("The chicken gets a head start before hunters begin")
                    .font(.gameboy(size: 8))
                    .foregroundStyle(Color.onBackground.opacity(0.6))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }

            Spacer()
        }
    }

    // MARK: - Power-Ups Step

    private var powerUpsStep: some View {
        VStack(spacing: 24) {
            Spacer()
            stepHeader(
                title: "Power-Ups",
                subtitle: "Enable special abilities?"
            )

            VStack(spacing: 16) {
                selectionCard(
                    title: "Power-Ups ON",
                    emoji: "⚡",
                    subtitle: "Collect and use abilities",
                    isSelected: store.currentGame.powerUps.enabled,
                    gradient: Color.gradientFire
                ) {
                    store.send(.powerUpsToggled(true))
                }

                selectionCard(
                    title: "Power-Ups OFF",
                    emoji: "🚫",
                    subtitle: "Classic mode, no power-ups",
                    isSelected: !store.currentGame.powerUps.enabled,
                    gradient: LinearGradient(colors: [.gray, .gray.opacity(0.7)], startPoint: .topLeading, endPoint: .bottomTrailing)
                ) {
                    store.send(.powerUpsToggled(false))
                }

                if store.currentGame.powerUps.enabled {
                    let unavailableRaw: Set<String> = store.currentGame.gameMode == .stayInTheZone
                        ? [PowerUp.PowerUpType.invisibility.rawValue, PowerUp.PowerUpType.decoy.rawValue, PowerUp.PowerUpType.jammer.rawValue]
                        : []
                    let enabledCount = store.currentGame.powerUps.enabledTypes.filter { !unavailableRaw.contains($0) }.count
                    let totalCount = PowerUp.PowerUpType.allCases.filter { !unavailableRaw.contains($0.rawValue) }.count

                    Button {
                        store.showPowerUpSelection = true
                    } label: {
                        HStack {
                            Text("Choose Power-Ups")
                                .font(.gameboy(size: 10))
                                .foregroundStyle(Color.onBackground)
                            Spacer()
                            Text("\(enabledCount)/\(totalCount)")
                                .font(.gameboy(size: 10))
                                .foregroundStyle(.secondary)
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 14)
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.surface)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color.onBackground.opacity(0.2), lineWidth: 1)
                        )
                    }
                }
            }
            .padding(.horizontal, 24)
            Spacer()
        }
    }

    // MARK: - Chicken Sees Hunters Step

    private var chickenSeesHuntersStep: some View {
        VStack(spacing: 24) {
            Spacer()
            stepHeader(
                title: "Chicken Visibility",
                subtitle: "Can the chicken see the hunters?"
            )

            VStack(spacing: 16) {
                selectionCard(
                    title: "Yes",
                    emoji: "👀",
                    subtitle: "The chicken sees all hunters",
                    isSelected: store.currentGame.chickenCanSeeHunters,
                    gradient: Color.gradientChicken
                ) {
                    store.send(.chickenCanSeeHuntersChanged(true))
                }

                selectionCard(
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

    // MARK: - Registration Step

    private var registrationStep: some View {
        let isDepositPlan = store.currentGame.pricing.model == .deposit
        let showDeadline = store.currentGame.registration.required
        return VStack(spacing: 0) {
            Spacer()
            stepHeader(
                title: String(localized: "Registration"),
                subtitle: String(localized: "Do hunters need to register before joining?")
            )
            .padding(.bottom, 20)

            // Cards block — moves up/down as a unit
            VStack(spacing: 12) {
                selectionCard(
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

                selectionCard(
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
                registrationDeadlinePicker
                    .padding(.horizontal, 24)
                    .padding(.top, 12)
                    .transition(.offset(y: -40).combined(with: .opacity))
            }

            Spacer()
        }
    }

    private var registrationDeadlinePicker: some View {
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

    // MARK: - Recap Step

    private var recapStep: some View {
        GeometryReader { geo in
        ScrollView {
            VStack(spacing: 20) {
                stepHeader(
                    title: "Ready to go!",
                    subtitle: "Review your game settings"
                )

                // Game Code
                VStack(spacing: 8) {
                    Text("GAME CODE")
                        .font(.gameboy(size: 8))
                        .foregroundStyle(Color.onBackground.opacity(0.6))
                    BangerText(store.currentGame.gameCode, size: 40)
                        .foregroundStyle(Color.CROrange)
                }
                .padding(.vertical, 16)
                .frame(maxWidth: .infinity)
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color.surface)
                )
                .padding(.horizontal, 24)

                // Settings Summary
                VStack(spacing: 0) {
                    recapRow(label: "Role", value: store.isParticipating ? "Chicken 🐔" : "Organizer 📋")
                    Divider()
                    recapRow(label: "Mode", value: store.currentGame.gameMode.title)
                    Divider()
                    recapRow(label: "Max Players", value: "\(store.currentGame.maxPlayers)")
                    Divider()
                    recapRow(label: "Start", value: formattedDate(store.currentGame.startDate))
                    Divider()
                    recapRow(label: "Duration", value: formattedDuration(store.gameDurationMinutes))
                    Divider()
                    let endDate = store.currentGame.startDate.addingTimeInterval(store.gameDurationMinutes * 60)
                    recapRow(label: "End", value: formattedDate(endDate))
                    Divider()
                    recapRow(label: "Head Start", value: "\(Int(store.currentGame.timing.headStartMinutes)) min")
                    Divider()
                    recapRow(label: "Zone Radius", value: "\(Int(store.currentGame.zone.radius)) m")
                    Divider()
                    recapRow(label: "Power-Ups", value: store.currentGame.powerUps.enabled ? "Enabled" : "Disabled")
                    if store.currentGame.powerUps.enabled {
                        Divider()
                        let enabledNames = PowerUp.PowerUpType.allCases
                            .filter { store.currentGame.powerUps.enabledTypes.contains($0.rawValue) }
                            .map(\.displayName)
                            .joined(separator: ", ")
                        recapRow(label: "Active Types", value: enabledNames)
                    }
                    if store.currentGame.gameMode == .followTheChicken {
                        Divider()
                        recapRow(label: "Chicken sees hunters", value: store.currentGame.chickenCanSeeHunters ? "Yes" : "No")
                    }
                    if store.currentGame.isPaid {
                        Divider()
                        recapRow(label: "Pricing", value: store.currentGame.pricing.model.title)
                        Divider()
                        let totalCents = store.currentGame.pricing.pricePerPlayer * store.currentGame.maxPlayers
                        recapRow(label: "Total price", value: String(format: "%.2f€", Double(totalCents) / 100.0))
                        if store.currentGame.pricing.deposit > 0 {
                            Divider()
                            recapRow(label: "Deposit", value: String(format: "%.2f€", Double(store.currentGame.pricing.deposit) / 100.0))
                        }
                    }
                    Divider()
                    recapRow(label: "Registration", value: store.currentGame.registration.required ? "Required" : "Open")
                    if store.currentGame.registration.required, let minutes = store.currentGame.registration.closesMinutesBefore {
                        Divider()
                        recapRow(label: "Registration closes", value: registrationDeadlineLabel(minutes))
                    }
                }
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color.surface)
                )
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .padding(.horizontal, 24)

                if !store.isZoneConfigured {
                    Text(store.currentGame.gameMode == .stayInTheZone
                         ? "Set a start zone and final zone to start"
                         : "Set a start zone to start")
                        .font(.gameboy(size: 8))
                        .foregroundStyle(Color.CROrange)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 16)
                }

                Spacer().frame(height: 20)
            }
            .frame(minHeight: geo.size.height)
        }
        }
    }

    // MARK: - Shared Components

    private func stepHeader(title: String, subtitle: String) -> some View {
        VStack(spacing: 8) {
            BangerText(title, size: 28)
                .foregroundStyle(Color.onBackground)
                .multilineTextAlignment(.center)
            Text(subtitle)
                .font(.gameboy(size: 10))
                .foregroundStyle(Color.onBackground.opacity(0.6))
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 24)
    }

    private func selectionCard(
        title: String,
        emoji: String,
        subtitle: String,
        isSelected: Bool,
        gradient: LinearGradient,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Text(emoji)
                    .font(.system(size: 36))

                VStack(alignment: .leading, spacing: 4) {
                    BangerText(title, size: 22)
                        .foregroundStyle(isSelected ? .black : Color.onBackground)
                    Text(subtitle)
                        .font(.gameboy(size: 7))
                        .foregroundStyle(isSelected ? .black.opacity(0.7) : Color.onBackground.opacity(0.6))
                }

                Spacer()

                Image(systemName: "checkmark.circle.fill")
                    .font(.title2)
                    .foregroundStyle(.black)
                    .opacity(isSelected ? 1 : 0)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(isSelected ? AnyShapeStyle(gradient) : AnyShapeStyle(Color.surface))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(isSelected ? Color.clear : Color.onBackground.opacity(0.2), lineWidth: 1)
            )
            .shadow(color: isSelected ? Color.CROrange.opacity(0.3) : .clear, radius: 8, y: 4)
        }
    }

    private func recapRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.gameboy(size: 9))
                .foregroundStyle(Color.onBackground.opacity(0.6))
            Spacer()
            Text(value)
                .font(.gameboy(size: 9))
                .foregroundStyle(Color.onBackground)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private func formattedDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }

    private func formattedDuration(_ minutes: Double) -> String {
        let hours = Int(minutes) / 60
        let mins = Int(minutes) % 60
        if mins == 0 {
            return "\(hours)h"
        }
        return "\(hours)h\(String(format: "%02d", mins))"
    }

    private func registrationDeadlineLabel(_ minutes: Int) -> String {
        switch minutes {
        case ..<60: return "\(minutes) min before"
        case 60: return "1 hour before"
        case 1440: return "1 day before"
        default: return "\(minutes / 60) hours before"
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
