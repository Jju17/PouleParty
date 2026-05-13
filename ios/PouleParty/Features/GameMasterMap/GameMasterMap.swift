//
//  GameMasterMap.swift
//  PouleParty
//
//  GameMaster (PP-24) — pure observer view. Streams the chicken's and
//  every hunter's live position, the spawned power-ups (read-only),
//  and the game's shrinking zone. The GM never tracks their own GPS
//  and cannot collect / activate power-ups.
//

import ComposableArchitecture
import FirebaseFirestore
import MapboxMaps
import os
import SwiftUI

private let logger = Logger(category: "GameMasterMap")

@Reducer
struct GameMasterMapFeature {

    @ObservableState
    struct State: Equatable {
        var game: Game
        var chickenLocation: CLLocationCoordinate2D?
        var chickenIsInvisible: Bool = false
        var hunterAnnotations: [HunterAnnotation] = []
        var powerUpAnnotations: [PowerUp] = []
        var nextRadiusUpdate: Date?
        var nowDate: Date = .now
        var radius: Int = 1500
        var mapCircle: CircleOverlay?
        var showGameInfo: Bool = false
        var showHuntersDrawer: Bool = false
        var winnerNotification: String? = nil
        var countdownNumber: Int? = nil
        var countdownText: String? = nil
        var previousWinnersCount: Int = -1

        // MARK: - MapFeatureState surface (GM has no power-up tray)
        var availablePowerUps: [PowerUp] { [] }
        var collectedPowerUps: [PowerUp] { [] }
        var showPowerUpInventory: Bool { false }
        var powerUpNotification: String? { nil }
        var lastActivatedPowerUpType: PowerUp.PowerUpType? { nil }
        /// The GM has no per-player zone check — they are pure spectator.
        var isOutsideZone: Bool { false }
        var hasGameStarted: Bool { nowDate >= game.startDate }
    }

    enum Action: BindableAction {
        case binding(BindingAction<State>)
        case delegate(Delegate)
        case `internal`(Internal)
        case view(View)

        @CasePathable
        enum View {
            case onTask
            case infoButtonTapped
            case gameInfoDismissed
            case huntersDrawerTapped
            case huntersDrawerDismissed
            case leaveGameTapped
        }

        @CasePathable
        enum Internal {
            case gameUpdated(Game)
            case chickenLocationUpdated(CLLocationCoordinate2D?, isInvisible: Bool)
            case hunterLocationsUpdated([HunterLocation])
            case powerUpsUpdated([PowerUp])
            case timerTicked
            case winnerNotificationDismissed
        }

        @CasePathable
        enum Delegate {
            case returnedToMenu
        }
    }

    @Dependency(\.apiClient) var apiClient
    @Dependency(\.continuousClock) var clock

    var body: some ReducerOf<Self> {
        BindingReducer()

        Reduce { state, action in
            switch action {
            case .binding, .delegate:
                return .none
            case .view(.onTask):
                let gameId = state.game.id
                let powerUpsEnabled = state.game.powerUps.enabled
                return .merge(
                    .run { send in
                        for await _ in self.clock.timer(interval: .seconds(1)) {
                            await send(.internal(.timerTicked))
                        }
                    },
                    .run { send in
                        for await game in apiClient.gameConfigStream(gameId) {
                            if let game {
                                await send(.internal(.gameUpdated(game)))
                            }
                        }
                    },
                    .run { send in
                        for await chickenLoc in apiClient.chickenLocationStream(gameId) {
                            // PP-87: GM always renders the chicken's
                            // position regardless of the `invisible` flag;
                            // the flag is surfaced separately so the
                            // marker can render in a distinct style.
                            let coordinate = chickenLoc.map { CLLocationCoordinate2D(latitude: $0.location.latitude, longitude: $0.location.longitude) }
                            let isInvisible = chickenLoc?.invisible ?? false
                            await send(.internal(.chickenLocationUpdated(coordinate, isInvisible: isInvisible)))
                        }
                    },
                    .run { send in
                        for await hunters in apiClient.hunterLocationsStream(gameId) {
                            await send(.internal(.hunterLocationsUpdated(hunters)))
                        }
                    },
                    .run { send in
                        guard powerUpsEnabled else { return }
                        for await powerUps in apiClient.powerUpsStream(gameId) {
                            await send(.internal(.powerUpsUpdated(powerUps)))
                        }
                    }
                )
            case let .internal(.gameUpdated(game)):
                state.game = game
                let (lastUpdate, lastRadius) = game.findLastUpdate()
                state.radius = lastRadius
                state.nextRadiusUpdate = lastUpdate
                let circleCenter = interpolateZoneCenter(
                    initialCenter: game.zone.center.toCLCoordinates,
                    finalCenter: game.finalLocation,
                    initialRadius: game.zone.radius,
                    currentRadius: Double(lastRadius)
                )
                state.mapCircle = CircleOverlay(
                    center: circleCenter,
                    radius: CLLocationDistance(lastRadius)
                )
                if state.previousWinnersCount >= 0,
                   let notif = detectNewWinners(
                       winners: game.winners,
                       previousCount: state.previousWinnersCount
                   ) {
                    state.winnerNotification = notif
                    state.previousWinnersCount = game.winners.count
                    return .run { send in
                        try await clock.sleep(for: .seconds(AppConstants.winnerNotificationSeconds))
                        await send(.internal(.winnerNotificationDismissed))
                    }
                }
                state.previousWinnersCount = game.winners.count
                return .none
            case let .internal(.chickenLocationUpdated(coord, isInvisible)):
                state.chickenLocation = coord
                state.chickenIsInvisible = isInvisible
                return .none
            case let .internal(.hunterLocationsUpdated(hunters)):
                let sorted = hunters.sorted { $0.hunterId < $1.hunterId }
                state.hunterAnnotations = sorted.enumerated().map { index, hunter in
                    HunterAnnotation(
                        id: hunter.hunterId,
                        coordinate: CLLocationCoordinate2D(
                            latitude: hunter.location.latitude,
                            longitude: hunter.location.longitude
                        ),
                        displayName: "Hunter \(index + 1)"
                    )
                }
                return .none
            case let .internal(.powerUpsUpdated(powerUps)):
                state.powerUpAnnotations = powerUps.filter { !$0.isCollected }
                return .none
            case .internal(.timerTicked):
                let now: Date = .now
                state.nowDate = now
                if let next = state.nextRadiusUpdate, now >= next {
                    let (newNext, newRadius) = state.game.findLastUpdate()
                    state.radius = newRadius
                    state.nextRadiusUpdate = newNext
                    let circleCenter = interpolateZoneCenter(
                        initialCenter: state.game.zone.center.toCLCoordinates,
                        finalCenter: state.game.finalLocation,
                        initialRadius: state.game.zone.radius,
                        currentRadius: Double(newRadius)
                    )
                    state.mapCircle = CircleOverlay(
                        center: circleCenter,
                        radius: CLLocationDistance(newRadius)
                    )
                }
                return .none
            case .internal(.winnerNotificationDismissed):
                state.winnerNotification = nil
                return .none
            case .view(.infoButtonTapped):
                state.showGameInfo = true
                return .none
            case .view(.gameInfoDismissed):
                state.showGameInfo = false
                return .none
            case .view(.huntersDrawerTapped):
                state.showHuntersDrawer = true
                return .none
            case .view(.huntersDrawerDismissed):
                state.showHuntersDrawer = false
                return .none
            case .view(.leaveGameTapped):
                return .send(.delegate(.returnedToMenu))
            }
        }
    }
}
