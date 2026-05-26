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
    case maxPlayers
    case gameMode
    /// PP-70 / PP-88: chicken opts in to the GameMaster role and sets
    /// a 4-digit password. Falls after `gameMode` per PP-70 spec.
    case gameMasterPassword
    /// PP-11: pin de départ. In `stayInTheZone` mode the radius is left
    /// at its default (recomputed at the recap step PP-13). In
    /// `followTheChicken` mode a small / medium / large picker sets the
    /// radius directly.
    case startZoneSetup
    /// PP-12: pin d'arrivée. Skipped entirely in `followTheChicken`
    /// (the zone follows the chicken's live position, no `finalCenter`).
    case finalZoneSetup
    /// PP-13: zone recap. Computes the initial radius from the placed
    /// pins (stayInTheZone) or echoes the picked size (followTheChicken),
    /// then renders every future shrunk circle with a stable rainbow
    /// palette so the chicken can preview where the game will run.
    /// PP-14: a Shuffle button here regenerates `driftSeed` for a fresh
    /// pattern. Hidden in followTheChicken (no drift in that mode).
    case zonesRecap
    case startTime
    case duration
    case headStart
    case powerUps
    case chickenSeesHunters
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
        /// PP-42: lifts the maxPlayers stepper from `2...5` (Free standard)
        /// to `2...500`. Always `false` in PP-42; PP-45 will flip it via the
        /// `jujurahier` admin code modal.
        var isAdminCreation: Bool = false
        /// PP-88: toggle on the gameMasterPassword step. Default ON for
        /// D-Day so every Free game ships with a GameMaster slot
        /// available; the chicken can flip it off.
        var isGameMasterEnabled: Bool = true
        /// PP-88: 4-digit password collected on the gameMasterPassword
        /// step. Empty until the user types. The CF
        /// `setGameMasterPassword` is called after `setConfig` succeeds
        /// when this is non-empty and `isGameMasterEnabled` is true.
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
        /// — the static helper is the single source of truth for the
        /// wizard order.
        var steps: [GameCreationStep] = State.recomputedSteps(
            isParticipating: true,
            gameMode: .stayInTheZone
        )

        /// Wizard order: When → How long → Mode → Where → Rules.
        /// The timing trio precedes the zone block so PP-13's recap
        /// sees a valid duration window when it computes the shrink
        /// schedule. `gameMode` sits right before `startZoneSetup`
        /// because it decides the zone setup sub-steps themselves
        /// (stayInTheZone has a final pin step, followTheChicken
        /// doesn't) — keeping them adjacent makes the wizard read as
        /// one coherent "configure the playing field" beat.
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
            // PP-12: `finalZoneSetup` only exists in stayInTheZone —
            // followTheChicken's zone tracks the chicken's live
            // position, no `finalCenter` to place.
            if gameMode == .stayInTheZone {
                result.append(.finalZoneSetup)
            }
            // PP-13: recap step lives right after the zone pins so
            // the chicken can preview the trajectory.
            result.append(.zonesRecap)
            // PP-70 / PP-88: GameMaster password parked with the
            // other modifier toggles at the tail end of the wizard.
            result.append(.gameMasterPassword)
            result.append(.powerUps)
            result.append(.chickenSeesHunters)
            result.append(.recap)
            return result
        }

        /// Closed range allowed by the current Stepper. PP-45 plumbs through
        /// `isAdminCreation = true` to unlock the wider range.
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

        /// PP-11: a hunter pin has been placed (i.e. the zone center is no
        /// longer the Brussels default seeded at wizard creation). Used
        /// to gate the Next button on `startZoneSetup`.
        var isStartZoneConfigured: Bool {
            let center = game.zone.center
            let isDefault = abs(center.latitude - AppConstants.defaultLatitude) < 0.001
                && abs(center.longitude - AppConstants.defaultLongitude) < 0.001
            return !isDefault
        }

        /// PP-12: a final pin has been placed AND is at least 100 m from
        /// the start (haversine). Used to gate Next on `finalZoneSetup`
        /// and on the recap fallback when stayInTheZone.
        var isFinalZoneConfigured: Bool {
            guard let finalCenter = game.zone.finalCenter else { return false }
            let start = CLLocation(
                latitude: game.zone.center.latitude,
                longitude: game.zone.center.longitude
            )
            let end = CLLocation(
                latitude: finalCenter.latitude,
                longitude: finalCenter.longitude
            )
            return start.distance(from: end) >= 100
        }

        /// Combined gate kept for backwards compatibility with the recap
        /// step (PP-13) and any caller that asked "is the zone ready?".
        var isZoneConfigured: Bool {
            guard isStartZoneConfigured else { return false }
            if game.gameMode == .stayInTheZone {
                return isFinalZoneConfigured
            }
            return true
        }

        /// Minimum start time : 1 min in the future. PP-90 dropped the
        /// in-app registration deadline gating so we no longer need to
        /// reserve buffer time for a sign-up window.
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
        case configSaveFailed
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
        /// PP-13 phase 1 — fired on entering `.zonesRecap`. Recomputes
        /// `Game.zone.radius` from the placed start + final pins (and
        /// the picked `radiusHint` in followTheChicken) via the
        /// client-side `computeZoneRadius` helper, allocates a new
        /// `driftSeed` if none is set, and refreshes the shrink params.
        case zonesRecapEntered
        /// PP-14 phase 1 — Shuffle button on the recap step regenerates
        /// the drift seed; the cached preview circles redraw.
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

            enum Alert: Equatable { }
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
                // Keep `timing.end` in sync so any reader (notably
                // PP-13's recap via `computeDebugShiftedCircles`)
                // sees a valid duration window long before the
                // wizard finishes. Without this, `endDate` stays at
                // the Game model default (now + 65 min) which is
                // earlier than the default `startDate` (now + 2 h)
                // and the preview shrink loop runs zero iterations.
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
                // (finalZoneSetup is stayInTheZone-only) — re-cache.
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
                // wizard — re-cache.
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
                        // PP-35: lean on the strict availability helper so
                        // we don't drift from the UI's filter rules.
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
                // Sync endDate too — see `gameDurationChanged`
                // comment.
                state.$game.withLock { game in
                    game.startDate = date
                    game.endDate = date.addingTimeInterval(state.gameDurationMinutes * 60)
                }
                return .none

            case .zonesRecapEntered:
                // PP-13 phase 1: recompute the initial radius from the
                // start + final pins via the client-side mirror of the
                // PP-69 backend formula. In followTheChicken the
                // picker already wrote `Game.zone.radius` on PP-11, but
                // we still validate it against the allowed presets.
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
                    // PP-14: only assign a drift seed on first visit so
                    // back-navigating + revisiting the recap doesn't
                    // re-shuffle the preview unintentionally. Shuffle
                    // does it explicitly.
                    if game.zone.driftSeed == 0 {
                        game.zone.driftSeed = generateDriftSeed()
                    }
                    // PP-13 bug fix: pick a non-centered initial disc
                    // that still contains both pins. In
                    // `followTheChicken` the disc stays anchored on
                    // the chicken's start (it's a "follow me" mode);
                    // in `stayInTheZone` we offset the geometric
                    // center via the drift seed so the user-placed
                    // pins live inside the disc as markers, not as
                    // its center.
                    if game.gameMode == .stayInTheZone, let finalCenter = game.finalLocation {
                        game.initialLocation = pickInitialZoneCenter(
                            startPin: game.startPinLocation,
                            finalCenter: finalCenter,
                            radius: radius,
                            seed: game.zone.driftSeed
                        )
                    } else {
                        // followTheChicken: keep the disc on the
                        // start pin — there's no second point to
                        // contain.
                        game.initialLocation = game.startPinLocation
                    }
                }
                recalculateNormalMode(state: &state)
                return .none

            case .shuffleDriftSeed:
                // PP-14 phase 1: regenerate a fresh seed AND re-pick
                // the initial disc center (stayInTheZone only). The
                // radius stays — it's a function of the pin positions
                // only. Preview circles re-derive deterministically
                // from the new seed.
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
                let enableGameMaster = state.isGameMasterEnabled
                    && state.gameMasterPassword.count == 4
                let gameMasterPassword = state.gameMasterPassword
                return .run { [state = state, analyticsClient] send in
                    do {
                        try await apiClient.setConfig(state.game)
                        // PP-88: enable the GM role *after* the Game doc
                        // exists — `setGameMasterPassword` validates the
                        // caller is `creatorId`, which only resolves once
                        // the doc is committed. Wizard skips silently
                        // when the toggle is OFF or the password is empty.
                        if enableGameMaster {
                            do {
                                try await apiClient.setGameMasterPassword(state.game.id, gameMasterPassword)
                            } catch {
                                // Game is still created — chicken can
                                // retry from Settings.
                            }
                        }
                        analyticsClient.gameCreated(
                            gameMode: state.game.gameMode.rawValue,
                            maxPlayers: state.game.maxPlayers,
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
            // Fixed header: progress bar + step counter + dismiss button
            // PP-15: the `X / Y` counter sits on top of the bar; Y is
            // the count of the currently-active step subset so steps
            // that skip (e.g. `finalZoneSetup` in followTheChicken or
            // `chickenSelection` when the chicken is participating)
            // don't inflate the denominator.
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

            // Step description bar (for map step). Copy is step-specific
            // so the chicken knows whether they're placing the start or
            // the final pin (PP-11 / PP-12).
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
