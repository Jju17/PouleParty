//
//  ChickenConfig.swift
//  PouleParty
//
//  Created by Julien Rahier on 15/03/2024.
//

import ComposableArchitecture
import CoreLocation
import FirebaseFirestore
import MapboxMaps
import SwiftUI

@Reducer
struct ChickenConfigFeature {

    @ObservableState
    struct State: Equatable {
        @Presents var destination: Destination.State?
        @Shared var game: Game
        var path = StackState<ChickenMapConfigFeature.State>()
        var isExpertMode: Bool = false
        var gameDurationMinutes: Double = 120
        var showPowerUpSelection: Bool = false
        var currentGame: Game { game }
    }

    enum Action: BindableAction {
        case backButtonTapped
        case binding(BindingAction<State>)
        case chickenCanSeeHuntersChanged(Bool)
        case chickenHeadStartChanged(Double)
        case configSaveFailed
        case destination(PresentationAction<Destination.Action>)
        case expertModeToggled(Bool)
        case gameCreated(Game)
        case gameDurationChanged(Double)
        case gameModChanged(Game.GameMode)
        case initialRadiusChanged(Double)
        case mapPreviewTapped
        case path(StackAction<ChickenMapConfigFeature.State, ChickenMapConfigFeature.Action>)
        case powerUpsToggled(Bool)
        case powerUpTypeToggled(PowerUp.PowerUpType)
        case powerUpSelectionTapped
        case radiusDeclinePerUpdateChanged(Double)
        case radiusIntervalUpdateChanged(Double)
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

        Reduce { state, action in
            switch action {
            case .binding:
                return .none
            case let .chickenCanSeeHuntersChanged(value):
                state.$game.withLock { $0.chickenCanSeeHunters = value }
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
            case let .expertModeToggled(isExpert):
                state.isExpertMode = isExpert
                if !isExpert {
                    self.recalculateNormalMode(state: &state)
                }
                return .none
            case let .gameDurationChanged(duration):
                state.gameDurationMinutes = duration
                if !state.isExpertMode {
                    self.recalculateNormalMode(state: &state)
                }
                return .none
            case let .gameModChanged(mode):
                state.$game.withLock { $0.gameMode = mode }
                return .none
            case .backButtonTapped:
                return .none
            case let .chickenHeadStartChanged(minutes):
                state.$game.withLock { $0.timing.headStartMinutes = minutes }
                if !state.isExpertMode {
                    self.recalculateNormalMode(state: &state)
                }
                return .none
            case let .initialRadiusChanged(radius):
                state.$game.withLock { $0.zone.radius = radius }
                if !state.isExpertMode {
                    self.recalculateNormalMode(state: &state)
                }
                return .none
            case .mapPreviewTapped:
                let finalMarker = state.game.finalLocation.map { MarkerOverlay(title: "Final", coordinate: $0) }
                state.path.append(ChickenMapConfigFeature.State(game: state.$game, finalMarker: finalMarker))
                return .none
            case let .powerUpsToggled(enabled):
                state.$game.withLock { $0.powerUps.enabled = enabled }
                return .none
            case let .powerUpTypeToggled(type):
                state.$game.withLock { game in
                    if let index = game.powerUps.enabledTypes.firstIndex(of: type.rawValue) {
                        // Count available (non-unavailable) enabled types
                        let unavailableRaw: Set<String> = game.gameMode == .stayInTheZone
                            ? [PowerUp.PowerUpType.invisibility.rawValue, PowerUp.PowerUpType.decoy.rawValue, PowerUp.PowerUpType.jammer.rawValue]
                            : []
                        let availableEnabledCount = game.powerUps.enabledTypes.filter { !unavailableRaw.contains($0) }.count
                        let isAvailable = !unavailableRaw.contains(type.rawValue)
                        // Don't allow deselecting the last available one
                        if !isAvailable || availableEnabledCount > 1 {
                            game.powerUps.enabledTypes.remove(at: index)
                        }
                    } else {
                        game.powerUps.enabledTypes.append(type.rawValue)
                    }
                }
                return .none
            case .powerUpSelectionTapped:
                state.showPowerUpSelection = true
                return .none
            case let .radiusDeclinePerUpdateChanged(value):
                state.$game.withLock { $0.zone.shrinkMetersPerUpdate = value }
                return .none
            case let .radiusIntervalUpdateChanged(value):
                state.$game.withLock { $0.zone.shrinkIntervalMinutes = value }
                return .none
            case let .startDateChanged(date):
                state.$game.withLock { $0.startDate = date }
                return .none
            case .path(.popFrom):
                // Recalculate when returning from map config (radius may have changed)
                if !state.isExpertMode {
                    self.recalculateNormalMode(state: &state)
                }
                return .none
            case .path:
                return .none
            case .startGameButtonTapped:
                // Auto-calculate endDate
                state.$game.withLock { game in
                    if state.isExpertMode {
                        // Expert mode: endDate from radius parameters
                        let shrinks = ceil(game.zone.radius / game.zone.shrinkMetersPerUpdate)
                        let duration = shrinks * game.zone.shrinkIntervalMinutes * 60
                        game.endDate = game.hunterStartDate.addingTimeInterval(duration)
                    } else {
                        // Normal mode: endDate = startDate + total game duration
                        game.endDate = game.startDate.addingTimeInterval(state.gameDurationMinutes * 60)
                    }
                }
                return .run { [state = state] send in
                    do {
                        try await apiClient.setConfig(state.game)
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
        .forEach(\.path, action: \.path) {
            ChickenMapConfigFeature()
        }
    }
}


struct ChickenConfigView: View {
    @Bindable var store: StoreOf<ChickenConfigFeature>

    private let durationOptions: [(String, Double)] = [
        ("1h", 60),
        ("1h30", 90),
        ("2h", 120),
        ("2h30", 150),
        ("3h", 180),
    ]

    private var isZoneConfigured: Bool {
        let loc = store.currentGame.initialLocation
        let isDefault = abs(loc.latitude - AppConstants.defaultLatitude) < 0.001
            && abs(loc.longitude - AppConstants.defaultLongitude) < 0.001
        guard !isDefault else { return false }
        if store.currentGame.gameMode == .stayInTheZone {
            return store.currentGame.finalLocation != nil
        }
        return true
    }

    var body: some View {
        NavigationStack(path: $store.scope(state: \.path, action: \.path)) {
            Form {
                gameCodeSection
                scheduleSection
                gameModeSection
                powerUpsSection
                zoneSection
                if store.isExpertMode {
                    advancedSection
                }
                headStartSection
                settingsModeSection
            }
            .sheet(isPresented: $store.showPowerUpSelection) {
                PowerUpSelectionView(
                    enabledTypes: store.currentGame.powerUps.enabledTypes,
                    gameMode: store.currentGame.gameMode,
                    onToggle: { type in store.send(.powerUpTypeToggled(type)) }
                )
            }
            .safeAreaInset(edge: .bottom) {
                VStack(spacing: 10) {
                    if !isZoneConfigured {
                        Text(store.currentGame.gameMode == .stayInTheZone
                             ? "Set a start zone and final zone to start"
                             : "Set a start zone to start")
                            .font(.gameboy(size: 8))
                            .foregroundStyle(Color.CROrange)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 16)
                    }
                    Button {
                        store.send(.startGameButtonTapped)
                    } label: {
                        BangerText("Start game", size: 24)
                            .foregroundStyle(.black)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(isZoneConfigured ? AnyShapeStyle(Color.gradientFire) : AnyShapeStyle(Color.gray.opacity(0.3)))
                            .clipShape(Capsule())
                            .shadow(color: .black.opacity(isZoneConfigured ? 0.2 : 0), radius: 4, y: 2)
                    }
                    .disabled(!isZoneConfigured)
                    .padding(.horizontal, 20)
                }
                .padding(.top, 12)
                .padding(.bottom, 16)
                .background(.thinMaterial)
            }
            .navigationTitle("Game Settings")
        } destination: { store in
            ChickenMapConfigView(store: store)
        }
        .alert(
            $store.scope(
                state: \.destination?.alert,
                action: \.destination.alert
            )
        )
    }

    private var gameCodeSection: some View {
        Section("Game Code") {
            GameCodeRow(gameCode: store.currentGame.gameCode)
        }
    }

    private var scheduleSection: some View {
        Section("Schedule") {
            DatePicker(selection: Binding(
                get: { store.currentGame.startDate },
                set: { store.send(.startDateChanged($0)) }
            ), in: .now.addingTimeInterval(7200)...) {
                Text("Start at")
            }
            .datePickerStyle(.compact)

            if !store.isExpertMode {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Duration")
                    Picker("Duration", selection: Binding(
                        get: { store.gameDurationMinutes },
                        set: { store.send(.gameDurationChanged($0)) }
                    )) {
                        ForEach(durationOptions, id: \.1) { option in
                            Text(option.0).tag(option.1)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                HStack {
                    Text("Ends at")
                    Spacer()
                    let endDate = store.currentGame.startDate.addingTimeInterval(store.gameDurationMinutes * 60)
                    Text(endDate, style: .time)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private var gameModeSection: some View {
        Section("Game Mode") {
            Picker("Game Mode", selection: Binding(
                get: { store.currentGame.gameMode },
                set: { store.send(.gameModChanged($0)) }
            )) {
                ForEach(Game.GameMode.allCases, id: \.self) { mode in
                    Text(mode.title).tag(mode)
                }
            }
            Toggle("Chicken can see hunters", isOn: Binding(
                get: { store.currentGame.chickenCanSeeHunters },
                set: { store.send(.chickenCanSeeHuntersChanged($0)) }
            ))
        }
    }

    private var powerUpsSection: some View {
        Section("Power-Ups") {
            Toggle("Enable Power-Ups", isOn: Binding(
                get: { store.currentGame.powerUps.enabled },
                set: { store.send(.powerUpsToggled($0)) }
            ))

            if store.currentGame.powerUps.enabled {
                let unavailableRaw: Set<String> = store.currentGame.gameMode == .stayInTheZone
                    ? [PowerUp.PowerUpType.invisibility.rawValue, PowerUp.PowerUpType.decoy.rawValue, PowerUp.PowerUpType.jammer.rawValue]
                    : []
                let enabledCount = store.currentGame.powerUps.enabledTypes.filter { !unavailableRaw.contains($0) }.count
                let totalCount = PowerUp.PowerUpType.allCases.filter { !unavailableRaw.contains($0.rawValue) }.count
                Button {
                    store.send(.powerUpSelectionTapped)
                } label: {
                    HStack {
                        Text("Choose Power-Ups")
                            .foregroundStyle(.primary)
                        Spacer()
                        Text("\(enabledCount)/\(totalCount)")
                            .foregroundStyle(.secondary)
                        Image(systemName: "chevron.right")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private var zoneSection: some View {
        Section("Zone") {
            MapPreviewView(game: store.currentGame)
                .frame(height: 180)
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .overlay(alignment: .bottomTrailing) {
                    Label("Change location", systemImage: "pencil")
                        .font(.caption)
                        .fontWeight(.medium)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(.thinMaterial)
                        .clipShape(Capsule())
                        .padding(8)
                }
                .contentShape(Rectangle())
                .onTapGesture {
                    store.send(.mapPreviewTapped)
                }
        }
    }

    private var advancedSection: some View {
        Section("Advanced") {
            VStack(alignment: .leading) {
                HStack {
                    Text("Radius interval update")
                    Spacer()
                    Text("\(Int(self.store.currentGame.zone.shrinkIntervalMinutes)) minutes")
                }
                Slider(value: Binding(
                    get: { store.currentGame.zone.shrinkIntervalMinutes },
                    set: { store.send(.radiusIntervalUpdateChanged($0)) }
                ), in: 1...60, step: 1)
            }
            VStack(alignment: .leading) {
                HStack {
                    Text("Radius decline")
                    Spacer()
                    Text("\(Int(self.store.currentGame.zone.shrinkMetersPerUpdate)) meters")
                }
                Slider(value: Binding(
                    get: { store.currentGame.zone.shrinkMetersPerUpdate },
                    set: { store.send(.radiusDeclinePerUpdateChanged($0)) }
                ), in: 50...1000, step: 10)
            }
        }
    }

    private var headStartSection: some View {
        Section("Head Start") {
            VStack(alignment: .leading) {
                HStack {
                    Text("Chicken head start")
                    Spacer()
                    Text("\(Int(self.store.currentGame.timing.headStartMinutes)) minutes")
                }
                Slider(
                    value: Binding(
                        get: { store.currentGame.timing.headStartMinutes },
                        set: { store.send(.chickenHeadStartChanged($0)) }
                    ),
                    in: 0...45,
                    step: 1
                )
            }
        }
    }

    private var settingsModeSection: some View {
        Section("Mode") {
            Picker("Settings", selection: Binding(
                get: { store.isExpertMode },
                set: { store.send(.expertModeToggled($0)) }
            )) {
                Text("Normal").tag(false)
                Text("Expert").tag(true)
            }
            .pickerStyle(.segmented)
        }
    }
}

// MARK: - Map Preview

struct MapPreviewView: View {
    let game: Game

    private var zoom: CGFloat {
        // Extra -1 to account for the short height (180pt) of the inline preview
        zoomForRadius(CLLocationDistance(game.zone.radius), latitude: game.initialLocation.latitude) - 1.0
    }

    var body: some View {
        Map(viewport: .constant(.camera(center: game.initialLocation, zoom: zoom))) {
            let circlePolygon = Polygon(center: game.initialLocation, radius: CLLocationDistance(game.zone.radius), vertices: 72)
            // Neon glow on start zone circle
            PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
                .lineColor(StyleColor(UIColor(Color.CROrange).withAlphaComponent(0.1)))
                .lineWidth(12)
            PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
                .lineColor(StyleColor(UIColor(Color.CROrange).withAlphaComponent(0.3)))
                .lineWidth(4)
            PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
                .lineColor(StyleColor(UIColor(Color.CROrange).withAlphaComponent(0.9)))
                .lineWidth(2)

            MapViewAnnotation(coordinate: game.initialLocation) {
                VStack(spacing: 0) {
                    Text("JOIN")
                        .font(.gameboy(size: 6))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(
                            Capsule().fill(Color.CROrange)
                        )
                    Image(systemName: "triangle.fill")
                        .font(.system(size: 6))
                        .foregroundStyle(Color.CROrange)
                        .rotationEffect(.degrees(180))
                }
            }
            .allowOverlap(true)

            // Final zone marker
            if let finalLoc = game.finalLocation {
                MapViewAnnotation(coordinate: finalLoc) {
                    VStack(spacing: 0) {
                        Text("FINAL")
                            .font(.gameboy(size: 6))
                            .foregroundStyle(.black)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(
                                Capsule().fill(Color.zoneGreen)
                            )
                        Image(systemName: "triangle.fill")
                            .font(.system(size: 6))
                            .foregroundStyle(Color.zoneGreen)
                            .rotationEffect(.degrees(180))
                    }
                }
                .allowOverlap(true)

                let finalCircle = Polygon(center: finalLoc, radius: 50, vertices: 36)
                PolylineAnnotation(lineCoordinates: finalCircle.outerRing.coordinates)
                    .lineColor(StyleColor(UIColor(Color.zoneGreen).withAlphaComponent(0.4)))
                    .lineWidth(3)
                PolylineAnnotation(lineCoordinates: finalCircle.outerRing.coordinates)
                    .lineColor(StyleColor(UIColor(Color.zoneGreen).withAlphaComponent(0.9)))
                    .lineWidth(1.5)
            }
        }
        .allowsHitTesting(false)
    }
}

#Preview {
    ChickenConfigView(store: Store(initialState: ChickenConfigFeature.State(game: Shared(value: Game.mock))) {
        ChickenConfigFeature()
    })
}
