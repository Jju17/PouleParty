import ComposableArchitecture
import FirebaseFirestore
import SnapshotTesting
import SwiftUI
import Testing
@testable import PouleParty

@MainActor
/// Fails on a missing or different image; run with SNAPSHOT_RECORD=1 to re-record on purpose.
@Suite(.snapshots(record: ProcessInfo.processInfo.environment["SNAPSHOT_RECORD"] == "1" ? .all : .never))
struct GameCreationSnapshotTests {

    private func makeStore(
        step: GameCreationStep = .participation,
        isParticipating: Bool = true,
        gameMode: Game.GameMode = .stayInTheZone,
        powerUpsEnabled: Bool = false,
        chickenCanSeeHunters: Bool = false,
        duration: Double = 120
    ) -> StoreOf<GameCreationFeature> {
        var game = Game(id: "snapshot-test")
        game.foundCode = "1234"
        // Fixed date to avoid snapshot diffs from time changes
        game.timing.start = .init(date: Date(timeIntervalSince1970: 1_800_000_000))
        game.gameMode = gameMode
        game.powerUps.enabled = powerUpsEnabled
        game.chickenCanSeeHunters = chickenCanSeeHunters
        game.timing.headStartMinutes = 5

        let shared = Shared(value: game)
        let mapConfig = ChickenMapConfigFeature.State(game: shared)

        var state = GameCreationFeature.State(
            game: shared,
            mapConfigState: mapConfig
        )
        state.steps = GameCreationFeature.State.recomputedSteps(isParticipating: isParticipating, gameMode: gameMode)
        state.currentStepIndex = state.steps.firstIndex(of: step) ?? 0
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
        assertSnapshot(of: makeVC(store: makeStore()), as: .image(size: size))
    }

    @Test func chickenSelectionStep() {
        assertSnapshot(of: makeVC(store: makeStore(step: .chickenSelection, isParticipating: false)), as: .image(size: size))
    }

    @Test func maxPlayersStep() {
        assertSnapshot(of: makeVC(store: makeStore(step: .maxPlayers)), as: .image(size: size))
    }

    @Test func startTimeStep() {
        assertSnapshot(of: makeVC(store: makeStore(step: .startTime)), as: .image(size: size))
    }

    @Test func timingStep() {
        assertSnapshot(of: makeVC(store: makeStore(step: .timing)), as: .image(size: size))
    }

    @Test func gameModeStep() {
        assertSnapshot(of: makeVC(store: makeStore(step: .gameMode)), as: .image(size: size))
    }

    @Test func optionsStepPowerUpsOff() {
        assertSnapshot(of: makeVC(store: makeStore(step: .options, powerUpsEnabled: false)), as: .image(size: size))
    }

    @Test func optionsStepPowerUpsOn() {
        assertSnapshot(of: makeVC(store: makeStore(step: .options, powerUpsEnabled: true)), as: .image(size: size))
    }

    @Test func recapStepStayInZone() {
        assertSnapshot(of: makeVC(store: makeStore(step: .recap)), as: .image(size: size))
    }

    @Test func recapStepFollowChicken() {
        assertSnapshot(of: makeVC(store: makeStore(step: .recap, gameMode: .followTheChicken)), as: .image(size: size))
    }
}
