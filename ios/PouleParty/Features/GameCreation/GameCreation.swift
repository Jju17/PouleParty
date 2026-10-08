
import ComposableArchitecture
import os
import CoreLocation
import SwiftUI

// MARK: - Step Enum

enum GameCreationStep: Equatable {
    case participation
    case chickenSelection
    case maxPlayers
    case gameMode
    case gameMasterPassword
    case startZoneSetup
    case finalZoneSetup
    case zonesRecap
    case startTime
    case duration
    case headStart
    case powerUps
    case chickenSeesHunters
    case recap
}

// MARK: - Reducer

private let logger = Logger(category: "GameCreation")
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
        var isAdminCreation: Bool = false
        /// QA debug game (entered via the `qa_debug_code` long-press). At
        /// finalize the wizard compresses the timing (near-now start, 0
        /// head start, short duration, 1-min shrink interval) and flags the
        /// doc so the map shows the QA panel.
        var isDebugGame: Bool = false
        var isGameMasterEnabled: Bool = true
        var gameMasterPassword: String = ""

        /// Cached wizard step sequence. Recomputed by the reducer
        /// whenever `isParticipating` or `game.gameMode` changes (the
        /// only two inputs that affect the order). Stored rather than
        /// computed so SwiftUI body re-evaluations (driven by Mapbox
        /// animations, Firestore listeners, the power-up clock, …)
        /// don't re-allocate the 13-element array hundreds of times
        /// per second.
        ///
        /// Compute via [`recomputedSteps(isParticipating:gameMode:)`]
        ///, the static helper is the single source of truth for the
        /// wizard order.
        var steps: [GameCreationStep] = State.recomputedSteps(
            isParticipating: true,
            gameMode: .stayInTheZone
        )

        static func recomputedSteps(
            isParticipating: Bool,
            gameMode: Game.GameMode
        ) -> [GameCreationStep] {
            var result: [GameCreationStep] = [.participation]
            if !isParticipating {
                result.append(.chickenSelection)
            }
            result.append(contentsOf: [
                .maxPlayers,
                .startTime,
                .duration,
                .headStart,
                .gameMode,
                .startZoneSetup,
            ])
            if gameMode == .stayInTheZone {
                result.append(.finalZoneSetup)
            }
            result.append(.zonesRecap)
            result.append(.gameMasterPassword)
            result.append(.powerUps)
            result.append(.chickenSeesHunters)
            result.append(.recap)
            return result
        }

        var maxPlayersRange: ClosedRange<Int> {
            isAdminCreation ? 2...500 : 2...5
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

        var isStartZoneConfigured: Bool {
            let center = game.zone.center
            let isDefault = abs(center.latitude - AppConstants.defaultLatitude) < 0.001
                && abs(center.longitude - AppConstants.defaultLongitude) < 0.001
            return !isDefault
        }

        var isFinalZoneConfigured: Bool {
            guard let finalCenter = game.zone.finalCenter else { return false }
            return distanceMeters(
                game.zone.center.latitude, game.zone.center.longitude,
                finalCenter.latitude, finalCenter.longitude
            ) >= 100
        }

        var isZoneConfigured: Bool {
            guard isStartZoneConfigured else { return false }
            if game.gameMode == .stayInTheZone {
                return isFinalZoneConfigured
            }
            return true
        }

        var minimumStartDate: Date {
            Date.now.addingTimeInterval(60)
        }

        var currentGame: Game { game }

        /// Initialises the wizard state and seeds the cached `steps`
        /// array from the actual `game.gameMode` (the type-level
        /// default falls back to `.stayInTheZone`, but Home may pass
        /// a Shared game in any mode).
        init(
            destination: Destination.State? = nil,
            game: Shared<Game>,
            currentStepIndex: Int = 0,
            isParticipating: Bool = true,
            gameDurationMinutes: Double = 90,
            showPowerUpSelection: Bool = false,
            mapConfigState: ChickenMapConfigFeature.State,
            goingForward: Bool = true,
            isAdminCreation: Bool = false,
            isDebugGame: Bool = false,
            isGameMasterEnabled: Bool = true,
            gameMasterPassword: String = ""
        ) {
            self.destination = destination
            self._game = game
            self.currentStepIndex = currentStepIndex
            self.isParticipating = isParticipating
            self.gameDurationMinutes = gameDurationMinutes
            self.showPowerUpSelection = showPowerUpSelection
            self.mapConfigState = mapConfigState
            self.goingForward = goingForward
            self.isAdminCreation = isAdminCreation
            self.isDebugGame = isDebugGame
            self.isGameMasterEnabled = isGameMasterEnabled
            self.gameMasterPassword = gameMasterPassword
            self.steps = Self.recomputedSteps(
                isParticipating: isParticipating,
                gameMode: game.wrappedValue.gameMode
            )
        }
    }

    enum Action: BindableAction {
        case backTapped
        case binding(BindingAction<State>)
        case chickenCanSeeHuntersChanged(Bool)
        case manualStartChanged(Bool)
        case chickenHeadStartChanged(Double)
        case configSaveFailed(String)
        case gameMasterCodeFailed
        case destination(PresentationAction<Destination.Action>)
        case gameCreated(Game)
        case gameDurationChanged(Double)
        case gameModChanged(Game.GameMode)
        case initialRadiusChanged(Double)
        case mapConfig(ChickenMapConfigFeature.Action)
        case maxPlayersChanged(Int)
        case nextTapped
        case participationChanged(Bool)
        case powerUpsToggled(Bool)
        case powerUpTypeToggled(PowerUp.PowerUpType)
        case startDateChanged(Date)
        case startGameButtonTapped
        case zonesRecapEntered
        case shuffleDriftSeed
    }

    @Reducer
    struct Destination {
        @ObservableState
        enum State: Equatable {
            case alert(AlertState<Action.Alert>)
        }

        enum Action {
            case alert(Alert)

            enum Alert: Equatable {
                case retryGameMasterCode
                case continueWithoutGameMaster
            }
        }

        var body: some ReducerOf<Self> {
            EmptyReducer()
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

    /// Compresses a QA debug game's timing so every phase is reachable in
    /// minutes: near-now start, no head start, short total duration and the
    /// minimum 1-min shrink interval (the floor enforced by
    /// firestore.rules). Manual launch stays on so the host triggers the
    /// start on demand. Called at finalize, after `recalculateNormalMode`,
    /// so it overrides the standard zone math.
    private func applyDebugTiming(state: inout State) {
        let durationMinutes = 5.0
        let shrinkIntervalMinutes = 1.0
        let start = state.minimumStartDate
        state.$game.withLock { game in
            game.isDebugGame = true
            game.manualStartEnabled = true
            game.startDate = start
            game.timing.headStartMinutes = 0
            game.endDate = start.addingTimeInterval(durationMinutes * 60)
            let shrinks = max(1.0, durationMinutes / shrinkIntervalMinutes)
            game.zone.shrinkIntervalMinutes = shrinkIntervalMinutes
            game.zone.shrinkMetersPerUpdate = max(0, (game.zone.radius - 100) / shrinks)
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

            case let .manualStartChanged(value):
                state.$game.withLock { $0.manualStartEnabled = value }
                return .none

            case let .chickenHeadStartChanged(minutes):
                state.$game.withLock { $0.timing.headStartMinutes = minutes }
                recalculateNormalMode(state: &state)
                return .none

            case let .configSaveFailed(reason):
                state.destination = .alert(
                    AlertState {
                        TextState("Could not create the game.")
                    } actions: {
                        ButtonState(role: .cancel) {
                            TextState("OK")
                        }
                    } message: {
                        TextState(reason)
                    }
                )
                return .none

            case .gameMasterCodeFailed:
                state.destination = .alert(
                    AlertState {
                        TextState("Referee code not saved")
                    } actions: {
                        ButtonState(action: .retryGameMasterCode) {
                            TextState("Try again")
                        }
                        ButtonState(action: .continueWithoutGameMaster) {
                            TextState("Continue without referee")
                        }
                    } message: {
                        TextState("The game is created, but referees cannot join until the code is saved.")
                    }
                )
                return .none

            case .destination(.presented(.alert(.retryGameMasterCode))):
                return saveGameMasterCodeThenEnter(state.game, password: state.gameMasterPassword)

            case .destination(.presented(.alert(.continueWithoutGameMaster))):
                return .send(.gameCreated(state.game))

            case .destination:
                return .none

            case let .gameDurationChanged(duration):
                state.gameDurationMinutes = duration
                state.$game.withLock { game in
                    game.endDate = game.startDate.addingTimeInterval(duration * 60)
                }
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
                // Mode toggles which sub-steps belong in the wizard
                // (finalZoneSetup is stayInTheZone-only), re-cache.
                state.steps = State.recomputedSteps(
                    isParticipating: state.isParticipating,
                    gameMode: mode
                )
                return .none

            case let .initialRadiusChanged(radius):
                state.$game.withLock { $0.zone.radius = radius }
                recalculateNormalMode(state: &state)
                return .none

            case .mapConfig:
                return .none

            case let .maxPlayersChanged(value):
                let clamped = min(max(value, state.maxPlayersRange.lowerBound), state.maxPlayersRange.upperBound)
                state.$game.withLock { $0.maxPlayers = clamped }
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
                // Toggles whether `chickenSelection` belongs in the
                // wizard, re-cache.
                state.steps = State.recomputedSteps(
                    isParticipating: participating,
                    gameMode: state.game.gameMode
                )
                return .none

            case let .powerUpsToggled(enabled):
                state.$game.withLock { $0.powerUps.enabled = enabled }
                return .none

            case let .powerUpTypeToggled(type):
                state.$game.withLock { game in
                    if let index = game.powerUps.enabledTypes.firstIndex(of: type.rawValue) {
                        let availableRaw = Set(availablePowerUpTypes(for: game.gameMode).map(\.rawValue))
                        let availableEnabledCount = game.powerUps.enabledTypes.filter { availableRaw.contains($0) }.count
                        let isAvailable = availableRaw.contains(type.rawValue)
                        if !isAvailable || availableEnabledCount > 1 {
                            game.powerUps.enabledTypes.remove(at: index)
                        }
                    } else {
                        game.powerUps.enabledTypes.append(type.rawValue)
                    }
                }
                return .none

            case let .startDateChanged(date):
                // Sync endDate too, see `gameDurationChanged`
                // comment.
                state.$game.withLock { game in
                    game.startDate = date
                    game.endDate = date.addingTimeInterval(state.gameDurationMinutes * 60)
                }
                return .none

            case .zonesRecapEntered:
                let radiusHint = state.game.gameMode == .followTheChicken
                    ? state.game.zone.radius
                    : nil
                let radius = computeZoneRadius(
                    start: state.game.startPinLocation,
                    finalCenter: state.game.finalLocation,
                    gameMode: state.game.gameMode,
                    radiusHint: radiusHint
                )
                state.$game.withLock { game in
                    game.zone.radius = radius
                    // Defensive: if the user skipped both duration
                    // and startDate edits (no-op Next on those
                    // steps), the Game model's stale defaults could
                    // leave `endDate < startDate` and the preview
                    // shrink schedule comes out empty. Re-sync here.
                    game.endDate = game.startDate.addingTimeInterval(state.gameDurationMinutes * 60)
                    if game.zone.driftSeed == 0 {
                        game.zone.driftSeed = generateDriftSeed()
                    }
                    if game.gameMode == .stayInTheZone, let finalCenter = game.finalLocation {
                        game.initialLocation = pickInitialZoneCenter(
                            startPin: game.startPinLocation,
                            finalCenter: finalCenter,
                            radius: radius,
                            seed: game.zone.driftSeed
                        )
                    } else {
                        // followTheChicken: keep the disc on the
                        // start pin, there's no second point to
                        // contain.
                        game.initialLocation = game.startPinLocation
                    }
                }
                recalculateNormalMode(state: &state)
                return .none

            case .shuffleDriftSeed:
                let newSeed = generateDriftSeed()
                state.$game.withLock { game in
                    game.zone.driftSeed = newSeed
                    if game.gameMode == .stayInTheZone, let finalCenter = game.finalLocation {
                        game.initialLocation = pickInitialZoneCenter(
                            startPin: game.startPinLocation,
                            finalCenter: finalCenter,
                            radius: game.zone.radius,
                            seed: newSeed
                        )
                    }
                }
                return .none

            case .startGameButtonTapped:
                clampStartDateToMinimum(state: &state)
                state.$game.withLock { game in
                    game.endDate = game.startDate.addingTimeInterval(state.gameDurationMinutes * 60)
                }
                recalculateNormalMode(state: &state)
                if state.isDebugGame {
                    applyDebugTiming(state: &state)
                }
                let enableGameMaster = state.isGameMasterEnabled
                    && state.gameMasterPassword.count == 4
                let gameMasterPassword = state.gameMasterPassword
                let game = state.game
                return .run { [analyticsClient] send in
                    do {
                        try await apiClient.setConfig(game)
                    } catch {
                        logger.warning("[create] game write failed: \(error.localizedDescription)")
                        await send(.configSaveFailed(error.userMessage))
                        return
                    }
                    analyticsClient.gameCreated(
                        gameMode: game.gameMode.rawValue,
                        maxPlayers: game.maxPlayers,
                        powerUpsEnabled: game.powerUps.enabled
                    )
                    guard enableGameMaster else {
                        await send(.gameCreated(game))
                        return
                    }
                    // The callable checks the caller owns the game, so the code is saved after the game exists.
                    do {
                        try await apiClient.setGameMasterPassword(game.id, gameMasterPassword)
                        await send(.gameCreated(game))
                    } catch {
                        logger.warning("[create] referee code not saved: \(error.localizedDescription)")
                        await send(.gameMasterCodeFailed)
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

    private func saveGameMasterCodeThenEnter(_ game: Game, password: String) -> Effect<Action> {
        .run { send in
            do {
                try await apiClient.setGameMasterPassword(game.id, password)
                await send(.gameCreated(game))
            } catch {
                logger.warning("[create] referee code retry failed: \(error.localizedDescription)")
                await send(.gameMasterCodeFailed)
            }
        }
    }
}

// MARK: - View

struct GameCreationView: View {
    @Bindable var store: StoreOf<GameCreationFeature>
    var onDismiss: (() -> Void)?

    private var isMapStep: Bool {
        store.currentStep == .startZoneSetup || store.currentStep == .finalZoneSetup
    }

    /// Header / Next-button label for the current map step. Two steps
    /// share `isMapStep` but each has its own copy + validation gate.
    private var mapStepIsConfigured: Bool {
        switch store.currentStep {
        case .startZoneSetup: return store.isStartZoneConfigured
        case .finalZoneSetup: return store.isFinalZoneConfigured
        default: return true
        }
    }

    private var mapStepTitle: String {
        switch store.currentStep {
        case .startZoneSetup: return String(localized: "Start zone")
        case .finalZoneSetup: return String(localized: "Final zone")
        default: return ""
        }
    }

    private var mapStepSubtitle: String {
        switch store.currentStep {
        case .startZoneSetup:
            if store.currentGame.gameMode == .stayInTheZone {
                return String(localized: "Place the start. The zone size will be computed on the next step.")
            } else {
                return String(localized: "Place the start, then pick a zone size.")
            }
        case .finalZoneSetup:
            return store.isFinalZoneConfigured
                ? String(localized: "Tap the map to adjust the final zone.")
                : String(localized: "Place the final pin at least 100 m from the start.")
        default: return ""
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                VStack(spacing: 4) {
                    HStack {
                        Spacer()
                        Text("\(store.currentStepIndex + 1) / \(store.steps.count)")
                            .font(.gameboy(size: 8))
                            .foregroundStyle(Color.onBackground.opacity(0.6))
                            .monospacedDigit()
                    }
                    ProgressView(value: store.progress)
                        .tint(Color.CROrange)
                        .animation(.easeInOut(duration: 0.25), value: store.progress)
                }
                if let onDismiss {
                    Button(action: onDismiss) {
                        Image(systemName: "xmark")
                            .font(.system(.subheadline, weight: .bold))
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

            if isMapStep {
                VStack(spacing: 2) {
                    BangerText(mapStepTitle, size: 20)
                        .foregroundStyle(Color.onBackground)
                    Text(mapStepSubtitle)
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
                        .foregroundStyle(.white)
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
                let nextDisabled = isMapStep && !mapStepIsConfigured
                Button {
                    store.send(.nextTapped)
                } label: {
                    HStack(spacing: 4) {
                        Text("Next")
                            .font(.gameboy(size: 10))
                        Image(systemName: "chevron.right")
                    }
                    .foregroundStyle(nextDisabled ? .white.opacity(0.5) : .white)
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
        case .maxPlayers:          MaxPlayersStep(store: store)
        case .gameMode:            GameModeStep(store: store)
        case .gameMasterPassword:  GameMasterPasswordStep(store: store)
        case .startZoneSetup:      StartZoneSetupStep(store: store)
        case .finalZoneSetup:      FinalZoneSetupStep(store: store)
        case .zonesRecap:          ZonesRecapStep(store: store)
        case .startTime:           StartTimeStep(store: store)
        case .duration:            DurationStep(store: store)
        case .headStart:           HeadStartStep(store: store)
        case .powerUps:            PowerUpsStep(store: store)
        case .chickenSeesHunters:  ChickenSeesHuntersStep(store: store)
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
