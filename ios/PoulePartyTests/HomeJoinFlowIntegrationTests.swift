import ComposableArchitecture
import FirebaseFirestore
import Foundation
import Testing
@testable import PouleParty

@MainActor
struct HomeJoinFlowIntegrationTests {

    @Test func joinDelegateForWaitingGameSendsHunterGameJoined() async {
        var game = Game.mock
        game.status = .waiting

        var state = HomeFeature.State()
        state.destination = .joinFlow(JoinFlowFeature.State())

        let store = TestStore(initialState: state) {
            HomeFeature()
        }
        store.exhaustivity = .off

        await store.send(.destination(.presented(.joinFlow(.delegate(.joinGame(game, hunterName: "Tester"))))))
        await store.receive(\.hunterGameJoined)
        #expect(store.state.destination == nil)
    }
}
