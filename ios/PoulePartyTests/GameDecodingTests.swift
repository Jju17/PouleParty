import FirebaseFirestore
import Testing
@testable import PouleParty

struct GameDecodingTests {
    @Test func decodesADocumentWrittenBeforeRolesAndFlagsExisted() throws {
        let dict: [String: Any] = [
            "id": "legacy",
            "name": "Old game",
            "status": "done",
            "creatorId": "c1",
            "timing": [
                "start": Timestamp(seconds: 1_800_000_000, nanoseconds: 0),
                "end": Timestamp(seconds: 1_800_003_600, nanoseconds: 0),
            ],
        ]
        let game = try Firestore.Decoder().decode(Game.self, from: dict)
        #expect(game.id == "legacy")
        #expect(game.roles.isEmpty)
        #expect(game.hasGameMasterPassword == false)
        #expect(game.isAdminCreation == false)
        #expect(game.manualStartEnabled == false)
        #expect(game.isDebugGame == false)
        #expect(game.timing.headStartMinutes == 2)
        #expect(game.zone.radius == 1500)
        #expect(game.status == .done)
    }

    @Test func dropsMalformedWinnersInsteadOfFailingTheGame() throws {
        let dict: [String: Any] = [
            "id": "g",
            "winners": [
                ["hunterId": "h1", "hunterName": "Team", "timestamp": Timestamp(seconds: 1, nanoseconds: 0)],
                ["hunterId": "h2"],
            ],
        ]
        let game = try Firestore.Decoder().decode(Game.self, from: dict)
        #expect(game.winners.map(\.hunterId) == ["h1"])
    }

    @Test func readsTheFlagsWrittenByAndroid() throws {
        let dict: [String: Any] = ["id": "g", "isAdminCreation": true, "isDebugGame": true]
        let game = try Firestore.Decoder().decode(Game.self, from: dict)
        #expect(game.isAdminCreation)
        #expect(game.isDebugGame)
    }

    @Test func driftSeedsStayWithin32Bits() {
        for _ in 0..<200 {
            let seed = generateDriftSeed()
            #expect(seed > 0)
            #expect(seed <= Int(Int32.max))
        }
    }
}
