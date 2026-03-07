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
    }

    enum Action: BindableAction {
        case backButtonTapped
        case binding(BindingAction<State>)
        case chickenHeadStartChanged(Double)
        case configSaveFailed
        case destination(PresentationAction<Destination.Action>)
        case expertModeToggled(Bool)
        case gameCreated(Game)
        case gameDurationChanged(Double)
        case initialRadiusChanged(Double)
        case mapPreviewTapped
        case path(StackAction<ChickenMapConfigFeature.State, ChickenMapConfigFeature.Action>)
        case powerUpsToggled(Bool)
        case powerUpTypeToggled(PowerUp.PowerUpType)
        case powerUpSelectionTapped
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
        let effectiveDuration = max(state.gameDurationMinutes - state.game.chickenHeadStartMinutes, 1)
        let (interval, decline) = calculateNormalModeSettings(
            initialRadius: state.game.initialRadius,
            gameDurationMinutes: effectiveDuration
        )
        state.$game.withLock { game in
            game.radiusIntervalUpdate = interval
            game.radiusDeclinePerUpdate = decline
        }
    }

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding:
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
            case .backButtonTapped:
                return .none
            case let .chickenHeadStartChanged(minutes):
                state.$game.withLock { $0.chickenHeadStartMinutes = minutes }
                if !state.isExpertMode {
                    self.recalculateNormalMode(state: &state)
                }
                return .none
            case let .initialRadiusChanged(radius):
                state.$game.withLock { $0.initialRadius = radius }
                if !state.isExpertMode {
                    self.recalculateNormalMode(state: &state)
                }
                return .none
            case .mapPreviewTapped:
                state.path.append(ChickenMapConfigFeature.State(game: state.$game))
                return .none
            case let .powerUpsToggled(enabled):
                state.$game.withLock { $0.powerUpsEnabled = enabled }
                return .none
            case let .powerUpTypeToggled(type):
                state.$game.withLock { game in
                    if let index = game.enabledPowerUpTypes.firstIndex(of: type.rawValue) {
                        // Don't allow deselecting the last one
                        if game.enabledPowerUpTypes.count > 1 {
                            game.enabledPowerUpTypes.remove(at: index)
                        }
                    } else {
                        game.enabledPowerUpTypes.append(type.rawValue)
                    }
                }
                return .none
            case .powerUpSelectionTapped:
                state.showPowerUpSelection = true
                return .none
            case .path:
                return .none
            case .startGameButtonTapped:
                // Auto-calculate endDate
                state.$game.withLock { game in
                    if state.isExpertMode {
                        // Expert mode: endDate from radius parameters
                        let shrinks = ceil(game.initialRadius / game.radiusDeclinePerUpdate)
                        let duration = shrinks * game.radiusIntervalUpdate * 60
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
                    enabledTypes: store.game.enabledPowerUpTypes,
                    onToggle: { type in store.send(.powerUpTypeToggled(type)) }
                )
            }
            .safeAreaInset(edge: .bottom) {
                Button {
                    store.send(.startGameButtonTapped)
                } label: {
                    BangerText("Start game", size: 24)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.CROrange)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .padding(.horizontal)
                .padding(.bottom, 8)
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
            GameCodeRow(gameCode: store.game.gameCode)
        }
    }

    private var scheduleSection: some View {
        Section("Schedule") {
            DatePicker(selection: $store.game.startDate, in: .now.addingTimeInterval(120)...) {
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
                    let endDate = store.game.startDate.addingTimeInterval(store.gameDurationMinutes * 60)
                    Text(endDate, style: .time)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private var gameModeSection: some View {
        Section("Game Mode") {
            Picker("Game Mode", selection: $store.game.gameMod) {
                ForEach(Game.GameMod.allCases, id: \.self) { mode in
                    Text(mode.title).tag(mode)
                }
            }
            Toggle("Chicken can see hunters", isOn: $store.game.chickenCanSeeHunters)
        }
    }

    private var powerUpsSection: some View {
        Section("Power-Ups") {
            Toggle("Enable Power-Ups", isOn: Binding(
                get: { store.game.powerUpsEnabled },
                set: { store.send(.powerUpsToggled($0)) }
            ))

            if store.game.powerUpsEnabled {
                let enabledCount = store.game.enabledPowerUpTypes.count
                let totalCount = PowerUp.PowerUpType.allCases.count
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
            MapPreviewView(game: store.game)
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

            VStack(alignment: .leading) {
                HStack {
                    Text("Radius")
                    Spacer()
                    Text("\(Int(store.game.initialRadius)) m")
                }
                Slider(
                    value: Binding(
                        get: { store.game.initialRadius },
                        set: { store.send(.initialRadiusChanged($0)) }
                    ),
                    in: 500...2000,
                    step: 100
                )
            }
        }
    }

    private var advancedSection: some View {
        Section("Advanced") {
            VStack(alignment: .leading) {
                HStack {
                    Text("Radius interval update")
                    Spacer()
                    Text("\(Int(self.store.game.radiusIntervalUpdate)) minutes")
                }
                Slider(value: self.$store.game.radiusIntervalUpdate, in: 1...60, step: 1)
            }
            VStack(alignment: .leading) {
                HStack {
                    Text("Radius decline")
                    Spacer()
                    Text("\(Int(self.store.game.radiusDeclinePerUpdate)) meters")
                }
                Slider(value: self.$store.game.radiusDeclinePerUpdate, in: 50...1000, step: 10)
            }
        }
    }

    private var headStartSection: some View {
        Section("Head Start") {
            VStack(alignment: .leading) {
                HStack {
                    Text("Chicken head start")
                    Spacer()
                    Text("\(Int(self.store.game.chickenHeadStartMinutes)) minutes")
                }
                Slider(
                    value: Binding(
                        get: { store.game.chickenHeadStartMinutes },
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
        zoomForRadius(CLLocationDistance(game.initialRadius), latitude: game.initialLocation.latitude) - 1.0
    }

    var body: some View {
        Map(viewport: .constant(.camera(center: game.initialLocation, zoom: zoom))) {
            let circlePolygon = Polygon(center: game.initialLocation, radius: CLLocationDistance(game.initialRadius), vertices: 72)
            PolylineAnnotation(lineCoordinates: circlePolygon.outerRing.coordinates)
                .lineColor(StyleColor(UIColor(Color.CROrange).withAlphaComponent(0.8)))
                .lineWidth(2)

            MapViewAnnotation(coordinate: game.initialLocation) {
                Image(systemName: "mappin.circle.fill")
                    .font(.title)
                    .foregroundStyle(.red)
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
