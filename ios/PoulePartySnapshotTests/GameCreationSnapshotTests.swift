//
//  GameCreationSnapshotTests.swift
//  PoulePartySnapshotTests
//

import ComposableArchitecture
import FirebaseFirestore
import SnapshotTesting
import SwiftUI
import Testing
@testable import PouleParty

@MainActor
@Suite(.snapshots(record: .missing))
struct GameCreationSnapshotTests {

    private func makeStore(
        step: Int = 0,
        isParticipating: Bool = true,
        gameMod: Game.GameMod = .stayInTheZone,
        powerUpsEnabled: Bool = false,
        chickenCanSeeHunters: Bool = false,
        duration: Double = 120
    ) -> StoreOf<GameCreationFeature> {
        var game = Game(id: "snapshot-test")
        game.foundCode = "1234"
        // Fixed date to avoid snapshot diffs from time changes
        game.startTimestamp = .init(date: Date(timeIntervalSince1970: 1_800_000_000)) // 2027-01-15 08:00 UTC
        game.gameMod = gameMod
        game.powerUpsEnabled = powerUpsEnabled
        game.chickenCanSeeHunters = chickenCanSeeHunters
        game.chickenHeadStartMinutes = 5

        let shared = Shared(value: game)
        let mapConfig = ChickenMapConfigFeature.State(game: shared)

        var state = GameCreationFeature.State(
            game: shared,
            mapConfigState: mapConfig
        )
        state.currentStepIndex = step
        state.isParticipating = isParticipating
        state.gameDurationMinutes = duration

        return Store(initialState: state) {
            GameCreationFeature()
        }
    }

    private func makeVC(store: StoreOf<GameCreationFeature>) -> UIViewController {
        let view = GameCreationView(store: store)
            .frame(width: 393, height: 852)
        let vc = UIHostingController(rootView: view)
        vc.view.frame = CGRect(x: 0, y: 0, width: 393, height: 852)
        vc.view.layoutIfNeeded()
        return vc
    }

    private let size = CGSize(width: 393, height: 852)

    // MARK: - Step Snapshots

    @Test func participationStep() {
        let vc = makeVC(store: makeStore(step: 0))
        assertSnapshot(of: vc, as: .image(size: size))
    }

    @Test func zoneSetupStep() {
        let vc = makeVC(store: makeStore(step: 2))
        assertSnapshot(of: vc, as: .image(size: size))
    }

    @Test func chickenSelectionStep() {
        let vc = makeVC(store: makeStore(step: 1, isParticipating: false))
        assertSnapshot(of: vc, as: .image(size: size))
    }

    @Test func gameModeStep() {
        let vc = makeVC(store: makeStore(step: 1))
        assertSnapshot(of: vc, as: .image(size: size))
    }

    @Test func startTimeStep() {
        let vc = makeVC(store: makeStore(step: 3))
        assertSnapshot(of: vc, as: .image(size: size))
    }

    @Test func durationStep() {
        let vc = makeVC(store: makeStore(step: 4))
        assertSnapshot(of: vc, as: .image(size: size))
    }

    @Test func headStartStep() {
        let vc = makeVC(store: makeStore(step: 5))
        assertSnapshot(of: vc, as: .image(size: size))
    }

    @Test func powerUpsStepDisabled() {
        let vc = makeVC(store: makeStore(step: 6, powerUpsEnabled: false))
        assertSnapshot(of: vc, as: .image(size: size))
    }

    @Test func powerUpsStepEnabled() {
        let vc = makeVC(store: makeStore(step: 6, powerUpsEnabled: true))
        assertSnapshot(of: vc, as: .image(size: size))
    }

    @Test func chickenSeesHuntersStep() {
        let vc = makeVC(store: makeStore(step: 7, gameMod: .followTheChicken))
        assertSnapshot(of: vc, as: .image(size: size))
    }

    @Test func recapStepStayInZone() {
        let vc = makeVC(store: makeStore(step: 7))
        assertSnapshot(of: vc, as: .image(size: size))
    }

    @Test func recapStepFollowChicken() {
        let vc = makeVC(store: makeStore(step: 8, gameMod: .followTheChicken))
        assertSnapshot(of: vc, as: .image(size: size))
    }
}
