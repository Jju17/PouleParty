//
//  GameCreationSnapshotTests.swift
//  PoulePartyTests
//

import ComposableArchitecture
import FirebaseFirestore
import SnapshotTesting
import SwiftUI
import XCTest
@testable import PouleParty

@MainActor
final class GameCreationSnapshotTests: XCTestCase {

    // To re-record all snapshots, change .missing to .all
    override func invokeTest() {
        withSnapshotTesting(record: .missing) {
            super.invokeTest()
        }
    }

    private func makeStore(
        step: Int = 0,
        isParticipating: Bool = true,
        gameMod: Game.GameMode = .stayInTheZone,
        powerUpsEnabled: Bool = false,
        chickenCanSeeHunters: Bool = false,
        duration: Double = 120
    ) -> StoreOf<GameCreationFeature> {
        var game = Game(id: "snapshot-test")
        game.foundCode = "1234"
        // Fixed date to avoid snapshot diffs from time changes
        game.timing.start = .init(date: Date(timeIntervalSince1970: 1_800_000_000)) // 2027-01-15 08:00 UTC
        game.gameMode = gameMod
        game.powerUps.enabled = powerUpsEnabled
        game.chickenCanSeeHunters = chickenCanSeeHunters
        game.timing.headStartMinutes = 5

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

    // MARK: - Step Snapshots

    func testParticipationStep() {
        let vc = makeVC(store: makeStore(step: 0))
        assertSnapshot(of: vc, as: .image(size: CGSize(width: 393, height: 852)))
    }

    func testChickenSelectionStep() {
        let vc = makeVC(store: makeStore(step: 1, isParticipating: false))
        assertSnapshot(of: vc, as: .image(size: CGSize(width: 393, height: 852)))
    }

    func testGameModeStep() {
        let vc = makeVC(store: makeStore(step: 1))
        assertSnapshot(of: vc, as: .image(size: CGSize(width: 393, height: 852)))
    }

    func testStartTimeStep() {
        let vc = makeVC(store: makeStore(step: 3))
        assertSnapshot(of: vc, as: .image(size: CGSize(width: 393, height: 852)))
    }

    func testDurationStep() {
        let vc = makeVC(store: makeStore(step: 4))
        assertSnapshot(of: vc, as: .image(size: CGSize(width: 393, height: 852)))
    }

    func testHeadStartStep() {
        let vc = makeVC(store: makeStore(step: 5))
        assertSnapshot(of: vc, as: .image(size: CGSize(width: 393, height: 852)))
    }

    func testPowerUpsStepDisabled() {
        let vc = makeVC(store: makeStore(step: 6, powerUpsEnabled: false))
        assertSnapshot(of: vc, as: .image(size: CGSize(width: 393, height: 852)))
    }

    func testPowerUpsStepEnabled() {
        let vc = makeVC(store: makeStore(step: 6, powerUpsEnabled: true))
        assertSnapshot(of: vc, as: .image(size: CGSize(width: 393, height: 852)))
    }

    func testChickenSeesHuntersStep() {
        let vc = makeVC(store: makeStore(step: 7, gameMod: .followTheChicken))
        assertSnapshot(of: vc, as: .image(size: CGSize(width: 393, height: 852)))
    }

    func testRegistrationStepStayInZone() {
        let vc = makeVC(store: makeStore(step: 7))
        assertSnapshot(of: vc, as: .image(size: CGSize(width: 393, height: 852)))
    }

    func testRecapStepStayInZone() {
        let vc = makeVC(store: makeStore(step: 8))
        assertSnapshot(of: vc, as: .image(size: CGSize(width: 393, height: 852)))
    }

    func testRecapStepFollowChicken() {
        let vc = makeVC(store: makeStore(step: 9, gameMod: .followTheChicken))
        assertSnapshot(of: vc, as: .image(size: CGSize(width: 393, height: 852)))
    }
}
